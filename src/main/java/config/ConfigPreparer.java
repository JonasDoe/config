package config;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is an alternative to {@code Properties}. While it holds all
 * settings in an internal {@link Map} it also offers support for updating the
 * relating {@code Config fields} if anotated by {@link Setting}. Additionally,
 * it parses "\" correctly and offers some options and intelligence for
 * different encodings. Furthermore, it allows the management of nested
 * settings, indicated by prefixes. <br>
 * To use it, you have to derive an own {@code Config} from this
 * {@code Config class}. There are serveral ways utilize it in detail, each of
 * them has it's own advantages and disadvantages:<br>
 * {@code MyConfig myConfig = new MyConfig(pathToCfgFile);
 * }<br>
 * or<br>
 * {@code MyConfig myConfig = (MyConfig) new MyConfig().addSettingsSource(pathToCfgFile).update(Config);}
 * <br>
 * or you can also skip the annotation support and just configure the settings
 * in your {@code MyConfig Class} like this:<br>
 * {@code public String mySetting = getSetting("my_setting_name")}
 *
 * @author JonasDoe
 */
public final class ConfigPreparer {
    /**
     * Lines trailing this annotation will be interpreted as comment and thereby
     * ignored.
     */
    private String commentDesignator = "#";
    /** Determines how a line read from a file will be parsed */
    private Pattern settingFromLine = Pattern.compile("\\s*([\\S]*)\\s*=(.*)");
    /**
     * Key the {@code Config} is looking for to determine the encoding of the
     * file.
     */
    private static final String ENCODING_ENTRY = "encoding";
    /** Standard encoding if no further information is given or found */
    private static final Charset STANDARD_ENCODING = StandardCharsets.UTF_8;
    /** Location where config changes can be stored by default */
    private File usedFileName = new File(Config.STANDARD_FILE_NAME);
    /** Holds the config settings and represents how they will stored */
    private Map<String, String> settings = new HashMap<>();
    /** Encoding that will be applied when reading the config file */
    private Charset encoding = STANDARD_ENCODING;
    /**
     * List of settings which where missing despite the
     * {@link Setting#isOptional()} constraint
     */
    private final List<Field> missingSettings = new ArrayList<>();
    /**
     * Maps {@link Field}s to the {@link Exception} message they caused when
     * read
     */
    private final Map<Field, String> causedExeptions = new HashMap<>();
    /**
     * All converters than can be used to convert {@code Objects} to
     * {@code Strings} and back
     */
    private final Map<Class<?>, SettingConverter> settingConverters = new HashMap<>();
    /** Whether the values will be trimmed or not */
    private boolean trim = true;
    
    /**
     * Creates an empty {@code Config} which only has some standard
     * {@link SettingConverter}s.
     */
    public ConfigPreparer() {
        setStandardValues();
    }
    
    /**
     * Creates a {@code Config} with standard {@link SettingConverter}s and the
     * {@link #addSettingsSource(File)} method and the {@link #fillConfig(Config)} method
     * right after.
     *
     * @param settingSource
     *         the file containing settings in the form of
     *         {@code ("name=value")}
     * @throws IOException
     *         while reading the file
     * @throws IllegalStateException
     *         if non-optional settings are missing or a field could not be
     *         set due to another reason
     */
    public ConfigPreparer(File settingSource) throws IOException, IllegalStateException {
        addSettingsSource(settingSource);
        usedFileName = settingSource;
        setStandardValues();
    }
    
    /**
     * Creates a {@code Config} with standard {@link SettingConverter}s and the
     * {@link #addSettingsSource(File, Charset)} method and the
     * {@link #fillConfig(Config)} method right after.
     *
     * @param settingSource
     *         the file containing settings in the form of
     *         {@code ("name=value")}
     * @param encoding
     *         the encoding of the source file
     * @throws IOException
     *         while reading the file
     * @throws IllegalStateException
     *         if non-optional settings are missing or a field could not be
     *         set due to another reason
     */
    public ConfigPreparer(File settingSource, Charset encoding) throws IOException, IllegalStateException {
        addSettingsSource(settingSource, encoding);
        usedFileName = settingSource;
        setStandardValues();
    }
    
    /**
     * Creates a {@code Config} with standard {@link SettingConverter}s and the
     * {@link #addSettingsSource(Map)} method and the {@link #fillConfig(Config)} method
     * right after.
     *
     * @param settingSource
     *         the {@link Map} that containing setting names and setting
     *         values
     * @throws IllegalStateException
     *         if non-optional settings are missing or a field could not be
     *         set due to another reason
     */
    public ConfigPreparer(Map<String, String> settingSource) throws IllegalStateException {
        addSettingsSource(settingSource);
        setStandardValues();
    }
    
    /**
     * Registeres {@link SettingConverter}s to the {@code Config} which specify
     * the handling of new types of settings. {@link #fillConfig(Config)} must be invoked
     * to take effect on the config.
     *
     * @param converters
     *         {@link Map} mapping a {@link Class} to a
     *         {@link SettingConverter} which specifies how to convert
     *         settings of the specified {@code Class} to a {@link String}
     *         and back.
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer registerConverters(Map<Class<?>, SettingConverter> converters) {
        settingConverters.putAll(converters);
        return this;
    }
    
    /**
     * Registeres a {@link SettingConverter} to the {@code Config} which
     * specifies the handling of a new type of setting. {@link #fillConfig(Config)} must
     * be invoked to take effect on the config.
     *
     * @param type
     *         {@link Class} the {@code SettingConverter} is able to handle
     * @param converter
     *         {@link SettingConverter} which specifies how to convert
     *         settings of the specified {@code Class} to a {@link String}
     *         and back.
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer registerConverter(Class<?> type, SettingConverter converter) {
        settingConverters.put(type, converter);
        return this;
    }
    
    /**
     * Loads the configuration from a file at the specified {@code Path}.
     * Attempts to find a key named {@value #ENCODING_ENTRY}. If there is any,
     * the file will be reloaded with the encoding specified there.
     * {@link #fillConfig(Config)} must be invoked to take effect on the config.
     *
     * @param settingSource
     *         {@link Path} to the file containing settings in the form of
     *         {@code ("name=value")}
     * @return the {@code Config Object} itself.
     * @throws IOException
     *         while reading the file
     */
    public ConfigPreparer addSettingsSource(File settingSource) throws IOException {
        readSettings(settingSource, STANDARD_ENCODING);
        String currentEncoding = getSetting(ENCODING_ENTRY);
        if (!currentEncoding.isEmpty() && !Charset.forName(currentEncoding).equals(STANDARD_ENCODING)) {
            encoding = Charset.forName(currentEncoding);
            readSettings(settingSource, Charset.forName(currentEncoding));
        }
        return this;
    }
    
    /**
     * Loads the configuration from a file at the specified {@code Path}.
     * {@link #fillConfig(Config)} must be invoked to take effect on the config.
     *
     * @param settingSource
     *         {@link Path} to the file containing settings in the form of
     *         {@code ("name=value")}
     * @param encoding
     *         of the config file
     * @return the {@code Config Object} itself.
     * @throws IOException
     *         while reading the file
     */
    public ConfigPreparer addSettingsSource(File settingSource, Charset encoding) throws IOException {
        readSettings(settingSource, encoding);
        return this;
    }
    
    /**
     * Creates a configuration based on a {@link Properties} object. Not
     * recommanded since it converts the {@code Key Object} plainly with
     * {@link String#toString()}. {@link #fillConfig(Config)} must be invoked to take
     * effect on the config.
     *
     * @param settingSource
     *         that will be interpreted as configuration.
     * @return the {@code Config Object} itself.
     */
    @Deprecated
    public ConfigPreparer addSettingsSource(Properties settingSource) {
        Map<String, String> settingsMap = new HashMap<>();
        settingSource.forEach(
                (key, value) -> settingsMap.put(key.toString(), getConverter(value.getClass()).toString.apply(value)));
        addSettingsSource(settingsMap);
        return this;
    }
    
    /**
     * Modifies an existing setting, or creates a new entry. The changes will
     * take place both in the {@code Config} object and the internal {@link Map}
     * holding the settings. {@link #fillConfig(Config)} must be invoked to take effect on
     * the config.
     *
     * @param settingName
     *         the name of the setting to be modified. If it does not exist,
     *         it will be created.
     * @param settingValueToChange
     *         the value of the new or modified setting.
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer addSettingsSource(String settingName, Object settingValueToChange) {
        HashMap<String, Object> update = new HashMap<>();
        update.put(settingName, settingValueToChange);
        addSettingsSource(update);
        return this;
    }
    
    /**
     * Updates the {@code Config} subclass by modifying existing settings or
     * creates a new entries. The changes will take place both in the
     * {@code Config} object and the internal {@link Map} holding the settings.
     * {@link #fillConfig(Config)} must be invoked to take effect on the config.
     *
     * @param settingSource
     *         the {@link Map} that containing setting names and setting
     *         values
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer addSettingsSource(Map<String, ?> settingSource) {
        settings.putAll(toStringStringMap(settingSource));
        return this;
    }
    
    /**
     * Sets whether leading and trailing spacings will be stripped from the
     * value or not.
     *
     * @param trim
     *         {@code true} if the spacings will be removed, {@code false}
     *         otherwise
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer withTrim(boolean trim) {
        this.trim = true;
        return this;
    }
    
    /**
     * Sets the {@code String} that will indicate comment at the beginning of a
     * line.
     *
     * @param commentDesignator
     *         that indicates a comment
     * @return the {@code Config Object} itself.
     */
    public ConfigPreparer withCommentDesignator(String commentDesignator) {
        this.commentDesignator = commentDesignator;
        return this;
    }
    
    /**
     * Refreshes the {@code Config} based on all added {@code Setting Sources}
     * and added {@link SettingConverter}s.
     *
     * @return the {@code Config Object} itself.
     * @throws IllegalStateException
     *         if non-optional settings are missing or a field could not be
     *         set due to another reason
     */
    public <T extends Config> T fillConfig(T toFill) throws IllegalStateException {
        updateViaAnnotation(toFill);
        toFill.setEncoding(encoding);
        toFill.setUsedFileName(usedFileName);
        toFill.setSettingConverters(settingConverters);
        toFill.setSettings(settings);
        return toFill;
    }
    
    /**
     * Creates a {@code Config} that might be incomplete (i.e. fields could not
     * be assigned or non-optional fields are empty). Because of this the
     * returned {@code Config} should not be used standard processing (use
     * {@link #fillConfig(Config)} instead). In fact, the {@code Config} by this method
     * can be used to determine in which (probably lacking) state the
     * {@code Config} is, for example to store it in a file and offer the user
     * to add the lacking information manually in this file.
     *
     * @param base
     *         the config to be filled as far as possible
     * @return an possibly incomplete {@code Config}
     */
    public <T extends Config> T getStump(T base) {
        try {
            fillConfig(base);
        } catch (IllegalStateException e) {
            // no op - that's what a stump is there for
        }
        return base;
    }
    
    @Override
    public String toString() {
        return toString(settings);
    }
    
    /**
     * Returns the specified setting.
     *
     * @param settingName
     *         the name of the requested setting.
     * @return the setting specified by the name or an empty {@code String}, if
     * not found
     */
    protected String getSetting(String settingName) {
        String setting_value = settings.get(settingName);
        return setting_value == null ? "" : setting_value;
    }
    
    /**
     * Returns the specified setting.
     *
     * @param settingsName
     *         the name of the requested setting.
     * @param defaultValue
     *         of the setting that will be returned if setting is
     *         {@code null} or empty.
     * @return the setting specified by the name
     */
    protected String getSetting(String settingsName, String defaultValue) {
        String setting = settings.get(settingsName);
        return setting == null || setting.isEmpty() ? defaultValue : setting;
    }
    
    /**
     * Returns all settings of the config, filtered by a the prefix, i.e.
     * "ftp.".
     *
     * @param prefix
     *         of the settings of interest.
     * @return the settings of the config beginning with the prefix. The prefix
     * is removed from all entries.
     */
    protected Map<String, String> getSettings(String prefix) {
        return filterMapByPrefix(prefix);
    }
    
    /**
     * Sets standard values for the {@code Config}.
     */
    private void setStandardValues() {
        setStandardConverters();
    }
    
    /**
     * Applies the config's {@code toString} method on a specified {@link Map}
     * (which should contain settings)
     *
     * @param settings
     *         that will be converted to a {@code String}
     * @return {@code String} with tha pattern of
     * "setting_name1=setting_value1\nsetting_name2=setting_value2 ..."
     */
    private String toString(Map<String, String> settings) {
        return settings.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(
                Collectors.joining("\n"));
    }
    
    /**
     * Registers the some standard {@link SettingConverter}s
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setStandardConverters() {
        registerConverter(Integer.class, new SettingConverter(Object::toString, Integer::parseInt));
        registerConverter(int.class, new SettingConverter(Object::toString, Integer::parseInt));
        registerConverter(Long.class, new SettingConverter(Object::toString, Long::parseLong));
        registerConverter(long.class, new SettingConverter(Object::toString, Long::parseLong));
        registerConverter(Float.class, new SettingConverter(Object::toString, Float::parseFloat));
        registerConverter(float.class, new SettingConverter(Object::toString, Float::parseFloat));
        registerConverter(Double.class, new SettingConverter(Object::toString, Double::parseDouble));
        registerConverter(double.class, new SettingConverter(Object::toString, Double::parseDouble));
        registerConverter(String.class, new SettingConverter(Object::toString, String::toString));
        registerConverter(List.class, new SettingConverter(list -> String.join(",", ((Collection<String>) list)),
                string -> List.of(string.split(","))));
        registerConverter(Array.class, new SettingConverter(array -> Stream.of(array)
                                                                           .map(Object::toString)
                                                                           .collect(Collectors.joining(",")),
                array -> array.split(",")));  // modifiable, but better than nothing
        registerConverter(File.class, new SettingConverter(Object::toString, File::new));
        registerConverter(Path.class, new SettingConverter(Object::toString, Paths::get));
        registerConverter(Class.class,
                new SettingConverter(clazz -> ((Class) clazz).getName(), ConfigUtils::createClass));
        registerConverter(URI.class, new SettingConverter(Object::toString, ConfigUtils::createURI));
        registerConverter(URL.class, new SettingConverter(Object::toString, ConfigUtils::createURL));
    }
    
    /**
     * Loads the configuration from a file at the specified {@code Path}.
     *
     * @param pathToConfigFile
     *         the location of the config file
     * @param encoding
     *         of the config file
     * @throws IOException
     *         if reading the file fails
     */
    private void readSettings(File pathToConfigFile, Charset encoding) throws IOException {
        // Stream<String> lines = Files.lines(pathToConfigFile, encoding)) can't
        // handle wrong charSet, so here a lenghty version
        try (BufferedReader fileReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(pathToConfigFile), encoding))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                // line=line.replace("\r", ""));
                lineToSetting(line);
            }
        }
    }
    
    /**
     * Parses the content of a line to a {@code Key-Value} pair and stores at as
     * setting.
     *
     * @param line
     *         to be parsed. Should have the form
     *         "setting_name=setting_value".
     *         {@code COMMENT) indicates a comment, i.e. this sign(s) and everything following will be ignored.
     */
    private void lineToSetting(String line) {
        if (line.isEmpty()) return;
        // appears for example when "UTF-8 with BOM" is set, e.g. in Notepad++
        char bom = 65279;
        line = line.replace(Character.toString(bom), "");
        if (line.trim().startsWith(commentDesignator)) return;
        Matcher settingMatcher = settingFromLine.matcher(line);
        if (settingMatcher.find()) {
            if (trim) settings.put(settingMatcher.group(1).trim(), settingMatcher.group(2).trim());
            else settings.put(settingMatcher.group(1), settingMatcher.group(2));
        }
    }
    
    /**
     * Gets the {@link SettingConverter} linked the the requested class. If no
     * one exists, the belonging to the next registered super class will be
     * returned. If this fails, too, an {@link IllegalArgumentException} will be
     * thrown.
     *
     * @param settingClass
     *         a matching {@code converter} is requested to
     * @return the matching {@code converter}
     * @throws IllegalArgumentException
     *         if matching {@link SettingConverter} is registered
     */
    private SettingConverter getConverter(Class<?> settingClass) {
        if (settingConverters.containsKey(settingClass)) return settingConverters.get(settingClass);
        else {
            Set<Class<?>> supportedClasses = new TreeSet<>((c1, c2) -> {
                if (c1.isAssignableFrom(c2)) return 1;
                    // we do not want to override items in the TreeSet, so we won't return 0
                else // if (c2.isAssignableFrom(c1))
                    return -1;
                // else return 0;
            });
            supportedClasses.addAll(settingConverters.keySet());
            Class<?> supportedClass = supportedClasses.stream()
                                                      .filter(clazz -> clazz.isAssignableFrom(settingClass))
                                                      .findFirst()
                                                      .orElseThrow(() -> new IllegalArgumentException(
                                                              "Class " + settingClass.getName() + " is not supported" + "."));
            return settingConverters.get(supportedClass);
        }
    }
    
    /**
     * Returns all settings from the configuration that start with the specified
     * {@code prefix}.
     *
     * @param prefix
     *         that will be used as filter, e.g. "ftp."
     * @return a filtered map with the relating settings. The single settings
     * won't start the {@code prefix} anymore.
     */
    private Map<String, String> filterMapByPrefix(String prefix) {
        Map<String, String> filtered = new HashMap<>();
        settings.forEach((key, value) -> {
            String[] prefixAndName = key.split(Pattern.quote(prefix), 2);
            if (prefixAndName.length > 1) {
                filtered.put(prefixAndName[1], value);
            }
        });
        return filtered;
    }
    
    /**
     * Fills all {@code Config fields} annotated by {@link Setting} with the
     * related values in the internal {@link #settings}. If
     * {@link Setting#descriptor()} is set, it will be used to find the relating
     * value in the {@link #settings}, else the name of the {@link Field} is
     * used.
     * Afterwards, all {@code Config fields} which represent {@code Config}s
     * themselves and are annotated by {@link NestedConfig} will set in the
     * same way.
     *
     * @throws IllegalStateException
     *         if non-optional settings are missing or a field could not be
     *         set due to another reason
     */
    private void updateViaAnnotation(Config toFill) {
        missingSettings.clear();
        causedExeptions.clear();
        updateNonNestedFields(toFill);
        configureSubConfigs(toFill);
        if (!missingSettings.isEmpty()) throw new IllegalStateException(
                "The following non-optional settings are missing: " + missingSettings.stream()
                                                                                     .map(Field::getName)
                                                                                     .collect(
                                                                                             Collectors.joining(", ")));
        else if (!causedExeptions.isEmpty()) throw new IllegalStateException(
                "The following settings caused exceptions: " + causedExeptions.entrySet()
                                                                              .stream()
                                                                              .map(e -> e.getKey()
                                                                                         .getName() + " (" + e.getValue() + ")")
                                                                              .collect(Collectors.joining(", ")));
    }
    
    /**
     * Settles the first part of the {@link #updateViaAnnotation(Config)} and returns
     * a {@code Map} of settings (and their set
     * {@link Setting#defaultValue()}s>) which did not appear in the
     * {@link #settings} read from file.
     */
    private void updateNonNestedFields(Config toFill) {
        List<Field> fields = getAllNonPrivateFieldsOrdered(toFill);
        for (Field field : fields) {
            Setting settingAnnotation = field.getAnnotation(Setting.class);
            if (settingAnnotation != null) {
                try {
                    String descriptor = settingAnnotation.descriptor();
                    String settingName = descriptor.isEmpty() ? field.getName() : descriptor;
                    String settingString = settings.get(settingName);
                    if (settingString == null) {
                        settingString = settingAnnotation.defaultValue()
                                                         .isEmpty() ? null : settingAnnotation.defaultValue();
                        if (!settingAnnotation.isOptional()) {
                            settings.put(settingName, settingString);
                        }
                    }
                    Function<String, Object> toObjectConverter = getConverter(field.getType()).toObject;
                    if (settingString == null || settingString.isEmpty()) {
                        if (!settingAnnotation.isOptional()) {
                            missingSettings.add(field);
                        }
                    } else {
                        Object setting = toObjectConverter.apply(settingString);
                        field.setAccessible(true);
                        field.set(toFill, setting);
                    }
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    if (!settingAnnotation.isOptional()) causedExeptions.put(field, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Settles the second part of the {@link #updateViaAnnotation(Config)} method, the
     * creation of annotated {@code Config}s inside a container {@code Config}.
     */
    private void configureSubConfigs(Config toFill) {
        Field[] fields = toFill.getClass().getFields();
        for (Field field : fields) {
            NestedConfig subConfigAnnotation = field.getAnnotation(NestedConfig.class);
            Class<?> type = field.getType();
            if (subConfigAnnotation != null && Config.class.isAssignableFrom(field.getType())) {
                try {
                    @SuppressWarnings("unchecked") Class<? extends Config> subConfigClass =
                            (Class<? extends Config>) type;
                    Map<String, ?> subSettings = getSettings(subConfigAnnotation.prefix());
                    Config subConfig = subConfigClass.getDeclaredConstructor().newInstance();
                    new ConfigPreparer().addSettingsSource(subSettings).fillConfig(subConfig);
                    field.setAccessible(true);
                    field.set(toFill, subConfig);
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException | SecurityException | NoSuchMethodException | InvocationTargetException e) {
                    causedExeptions.put(field, e.getMessage());
                }
            }
        }
        
    }
    
    /**
     * Gathers all non-private link Field}s and sorts them by their declaring
     * classes' inheritance order, i.e. super classes will come before sub
     * classes.
     *
     * @param config
     *         with the fields to be sorted by inheritance order of their declaring classes
     * @return sorted {@link List} of {@link Field}s
     */
    private List<Field> getAllNonPrivateFieldsOrdered(Config config) {
        Comparator<Field> comparator = (f1, f2) -> {
            if (f1.getDeclaringClass().isAssignableFrom(f2.getDeclaringClass())) return -1;
            else if (f2.getDeclaringClass().isAssignableFrom(f1.getDeclaringClass())) return 1;
            else return 0;
        };
        List<Field> declaredFields = Arrays.stream(config.getClass().getDeclaredFields()).collect(Collectors.toList());
        List<Field> allFields = Arrays.stream(config.getClass().getFields()).filter(
                field -> !Modifier.isPrivate(field.getModifiers())).sorted(comparator).collect(Collectors.toList());
        declaredFields.forEach(field -> {
            if (!allFields.contains(field)) allFields.add(field);
        });
        return allFields;
    }
    
    /**
     * Takes a {@link Map} typed {@code <String, Object>} to a {@link Map} typed
     * {@code <String, String>} by using the {@link SettingConverter#toString()}
     * method.
     *
     * @param updates
     *         The {@link Map} typed {@code <String, Object>}
     * @return The converted {@link Map} typed {@code <String, Object>}
     * @throws IllegalArgumentException
     *         if a setting couldn't be converted to a {@link String} by a
     *         {@link SettingConverter}
     */
    private Map<String, String> toStringStringMap(Map<String, ?> updates) throws IllegalStateException {
        Map<String, String> exceptions = new HashMap<>();
        Map<String, String> updatesAsStrings = new HashMap<>();
        updates.forEach((key, value) -> {
            if (value != null) {
                SettingConverter converter = getConverter(value.getClass());
                if (converter != null) {
                    updatesAsStrings.put(key, converter.toString.apply(value));
                } else {
                    exceptions.put(key, value.getClass().getName());
                }
            } else {
                updatesAsStrings.put(key, "");
            }
        });
        if (exceptions.isEmpty()) return updatesAsStrings;
        else throw new IllegalStateException(
                "The following settings are could not be converted to String:: " + exceptions.entrySet()
                                                                                             .stream()
                                                                                             .map(entry -> entry.getKey() + "(" + entry.getValue() + ")")
                                                                                             .collect(
                                                                                                     Collectors.joining(
                                                                                                             ", ")));
    }
}
