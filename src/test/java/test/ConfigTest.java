package test;

import config.ConfigPreparer;
import config.SettingConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    
    private static File CONFIG_STORE_FILE = new File("stored.cfg");
    private static URL CONFIG_LOAD_FILE = ConfigTest.class.getClassLoader().getResource("test.cfg");
    private static URL CONFIG_INCOMPLETE_LOAD_FILE = ConfigTest.class.getClassLoader().getResource("incomplete.cfg");
    
    @BeforeAll
    static void deleteStored() {
        if (CONFIG_STORE_FILE.exists()) CONFIG_STORE_FILE.delete();
    }
    
    @Test
    void test_complete() throws IOException, URISyntaxException {
        // Check that loading settings works
        Path fileToLoad = Paths.get(CONFIG_LOAD_FILE.toURI());
        ExampleChildConfig childConfig = new ConfigPreparer(fileToLoad.toFile()).fillConfig(new ExampleChildConfig());
        checkContent(childConfig);
    
        // Check that saving and reloading works
        childConfig.store(CONFIG_STORE_FILE);
        childConfig = new ConfigPreparer(fileToLoad.toFile()).fillConfig(new ExampleChildConfig());
        checkContent(childConfig);
    
        // Check that registration of new SettingConverter works
        ConfigPreparer configPreparer = new ConfigPreparer(fileToLoad.toFile());
        configPreparer.registerConverter(URL.class, new SettingConverter(Object::toString, string -> {
            try {
                return new URL(string);
            } catch (MalformedURLException e) {
                return null;
            }
        }));
        childConfig = configPreparer.fillConfig(new ExampleChildConfig());
        assertEquals(new URL("http://www.testes.com"), childConfig.testUrl);
    }
    
    private void checkContent(ExampleChildConfig childConfig) {
        // inheritance check
        assertEquals("testSuperAttribute", childConfig.superValue);
        // default value check
        assertEquals("testDefaultValue", childConfig.testSettingDefault);
        // class support check
        assertEquals(java.lang.String.class, childConfig.testClass);
        // no existing default value check
        assertEquals("asdfäöü", childConfig.testSettingNotDefault);
        // primitive check
        assertEquals(43, childConfig.testPrimitive);
        // nested config check
        assertEquals(Integer.valueOf(12345), childConfig.testNestedConfig.nestedConfigAttribute);
        // override check
        assertEquals("subValue", childConfig.testOverride);
        // list type check
        assertEquals(3, childConfig.list.size());
        // annotations work with all fields that are not private check
        assertEquals("I'mNonPublic", childConfig.nonPublicSetting);
        // entries with spaces can be read
        assertEquals("has space", childConfig.withSpacing);
    }
    
    @Test
    void test_incomplete() throws IOException, URISyntaxException {
        // Check that loading settings works
        File fileToLoad = Paths.get(CONFIG_INCOMPLETE_LOAD_FILE.toURI()).toFile();
        try {
            new ConfigPreparer().addSettingsSource(fileToLoad).fillConfig(new IncompleteConfig());
            fail();
        } catch (IllegalStateException e) {/*no op, exception wanted*/}
        IncompleteConfig incomplete = new ConfigPreparer().addSettingsSource(fileToLoad).getStump(
                new IncompleteConfig());
        checkContentIncomplete(incomplete);
        incomplete.store(CONFIG_STORE_FILE);
    
        List<String> content = Files.readAllLines(CONFIG_STORE_FILE.toPath());
        assertEquals(3, content.size());
        assertTrue(content.contains("existing_value=hi, I'm there!"));
        assertTrue(content.contains("does not exist="));
        assertTrue(content.contains("existing_default=default"));
    }
    
    private void checkContentIncomplete(IncompleteConfig incomplete) {
        assertNull(incomplete.missingValue);
        assertEquals("hi, I'm there!", incomplete.readValue);
        assertEquals("default", incomplete.existingDefault);
        assertNull(incomplete.optionalValue);
    }
}
