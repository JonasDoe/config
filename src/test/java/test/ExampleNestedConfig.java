package test;

import config.Config;
import config.Setting;

public class ExampleNestedConfig extends Config {
    
    @Setting(descriptor = "nestedId", defaultValue = "0")
    public Integer nestedConfigAttribute;
}
