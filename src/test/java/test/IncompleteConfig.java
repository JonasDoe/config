package test;

import config.Config;
import config.Setting;

public class IncompleteConfig extends Config {
    @Setting(descriptor = "existing_default", defaultValue = "default")
    final String existingDefault = null;
    
    @Setting(descriptor = "existing_value")
    String readValue;
    
    @Setting(descriptor = "does not exist", isOptional = true)
    String optionalValue;
    
    @Setting(descriptor = "does not exist")
    String missingValue;
    
    public IncompleteConfig() {
    }
}
