package test;

import config.NestedConfig;
import config.Setting;

import java.net.URL;
import java.util.List;

public class ExampleChildConfig extends ExampleSuperConfig {
    
    @Setting(descriptor = "testDefaultDescriptor", defaultValue = "testDefaultValue")
    public String testSettingDefault;
    
    @Setting(descriptor = "umlaut", defaultValue = "defaultValueUmläüt")
    public String testSettingNotDefault;
    
    @Setting(descriptor = "test_int", defaultValue = "42")
    public Integer testInteger;
    
    @Setting(descriptor = "test_primitive", defaultValue = "43")
    public int testPrimitive;
    
    @Setting(descriptor = "override", defaultValue = "subValue")
    public String testOverride;
    
    @Setting(descriptor = "class")
    public Class<?> testClass;
    
    @Setting(isOptional = true)
    public List<String> list;
    
    @Setting(isOptional = true)
    public URL testUrl;
    
    @Setting
    String nonPublicSetting;
    
    @Setting
    public String withSpacing;
    
    @NestedConfig(prefix = "test.")
    public ExampleNestedConfig testNestedConfig = null;
}
