package test;

import config.Config;
import config.Setting;

public class ExampleSuperConfig extends Config {
    
    @Setting(descriptor = "super_attribute")
    public final String superValue = "";
    
    @Setting(descriptor = "override", defaultValue = "superValue")
    String testOverride;
}
