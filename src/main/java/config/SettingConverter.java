package config;

import java.util.function.Function;

/**
 * Converts an {@code Object} to its {@code String} representation and vice
 * versa
 *
 * @author JonasDoe
 */
public class SettingConverter {
    /** Converts an {@code Object} to its {@code String} representation */
    public final Function<Object, String> toString;
    /**
     * Converts a {@code String} representation to the represened {@code Object}
     */
    public final Function<String, Object> toObject;
    
    /**
     * Creates a new {@code SettingsConverter} based on the two specified
     * conversion {@code Functions}.
     *
     * @param toString
     *         {@code Function} which converts a {@code String}
     *         representation to the represented config value/setting
     * @param toConfigValue
     *         {@code Function} which converts a value/setting from the config to its
     *         {@code String} representation
     */
    public SettingConverter(Function<Object, String> toString, Function<String, Object> toConfigValue) {
        this.toString = toString;
        this.toObject = toConfigValue;
    }
}
