package config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
public abstract class Config {
    /**
     * The standard location the config will be stored if {@link #store()} is
     * called.
     */
    static final String STANDARD_FILE_NAME = "config.cfg";
    /** Standard encoding if no further information is given or found */
    private static final Charset STANDARD_ENCODING = StandardCharsets.UTF_8;
    /** Holds the config settings and represents how they will stored */
    private Map<String, String> settings = new HashMap<>();
    /** Encoding that will be applied when reading the config file */
    private Charset encoding = STANDARD_ENCODING;
    /**
     * All converters than can be used to convert {@code Objects} to
     * {@code Strings} and back
     */
    private Map<Class<?>, SettingConverter> settingConverters = new HashMap<>();
    /** Location where config will be stored when {@link #store()} is called */
    private File usedFileName = new File(STANDARD_FILE_NAME);
    
    /**
     * Saves the {@code Config} to a file specified via the a
     * {@code Constructor} with a {@link Path} parameter or
     * {@value #STANDARD_FILE_NAME} else.
     *
     * @throws IOException
     *         if storing the config data to the file fails
     */
    public void store() throws IOException {
        store(usedFileName);
    }
    
    /**
     * Saves the {@code Config} to a file with the specified filename.
     *
     * @param configFile
     *         the name of the config file. If it exists, it will be
     *         overwritten, else it will be created.
     * @throws IOException
     *         if storing the config data to the file fails
     */
    public void store(File configFile) throws IOException {
        Map<String, String> toStore = new HashMap<>(settings);
        toStore.putAll(collectSettingsFromConfig());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configFile), encoding)) {
            writer.write(toString(toStore));
        }
    }
    
    @Override
    public String toString() {
        return toString(settings);
    }
    
    /**
     * Sets the encoding used when the config is stored.
     *
     * @param encoding
     *         to be used
     */
    void setEncoding(final Charset encoding) {
        this.encoding = encoding;
    }
    
    /**
     * Sets the default file name to be used when the config is stored.
     *
     * @param usedFileName
     *         name of the opened file which serves as standard save location as well.
     */
    void setUsedFileName(final File usedFileName) {
        this.usedFileName = usedFileName;
    }
    
    /**
     * Adds the given setting converters so they can be used if the config is stored.
     *
     * @param settingConverters
     *         to be added
     */
    void setSettingConverters(Map<Class<?>, SettingConverter> settingConverters) {
        this.settingConverters = new HashMap<>(settingConverters);
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
     * Set the settings backing this config.
     *
     * @param toSet
     *         of the settings to be set
     */
    void setSettings(Map<String, String> toSet) {
        settings = new HashMap<>(toSet);
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
     * Reads the values of all {@code Config fields} annotated by
     * {@link Setting} and put them into a {@link Map}. If
     * {@link Setting#descriptor()} is set, it will be used as key, else the
     * name of the {@link Field} is used. Additionally, nested
     * {@code Config fields} annotated by {@link NestedConfig} will be read via
     * the set {@link NestedConfig#prefix()} added to the result.
     *
     * @return {@link Map} that contains with the name of each setting as key
     * and its value as value. The keys from {@link Setting}s will
     * prefixed accordingly.
     */
    private Map<String, String> collectSettingsFromConfig() {
        Map<String, Object> settingsFromConfig = new HashMap<>();
        List<Field> sortedFields = getAllNonPrivateFieldsOrdered(this);
        for (Field field : sortedFields) {
            try {
                Object fieldObject = field.get(this);
                if (Config.class.isAssignableFrom(field.getType())) {
                    // Found nested Config
                    NestedConfig subAnnotation = field.getAnnotation(NestedConfig.class);
                    if (subAnnotation != null) {
                        Map<String, String> settingsFromSubConfig = new HashMap<>();
                        if (fieldObject != null) {
                            settingsFromSubConfig.putAll(((Config) fieldObject).collectSettingsFromConfig());
                        } else {
                            try {
                                settingsFromSubConfig.putAll(
                                        ((Config) field.getType().newInstance()).collectSettingsFromConfig());
                            } catch (InstantiationException e) {
                                // no op
                            }
                        }
                        settingsFromSubConfig.forEach(
                                (key, value) -> settingsFromConfig.put(subAnnotation.prefix() + key, value));
                    }
                } else { // Found normal field
                    Setting settingAnnotation = field.getAnnotation(Setting.class);
                    if (settingAnnotation != null) {
                        String descriptor = settingAnnotation.descriptor();
                        String settingName = descriptor.isEmpty() ? field.getName() : descriptor;
                        settingsFromConfig.put(settingName, fieldObject);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                // no op
            }
            
        }
        return toStringStringMap(settingsFromConfig);
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
        List<Field> declaredFields = Arrays.stream(config.getClass().getDeclaredFields()).peek(
                field -> field.setAccessible(true)).collect(Collectors.toList());
        List<Field> allFields = Arrays.stream(config.getClass().getFields()).filter(
                field -> !Modifier.isPrivate(field.getModifiers())).sorted(comparator).peek(
                field -> field.setAccessible(true)).collect(Collectors.toList());
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
