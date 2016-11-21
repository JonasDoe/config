package config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Directs to a target entry from a config file and assigns the value to the
 * annotated field. Supported field classes are listed in
 * {@code Config.SettingClass#SUPPORTED_CLASSES}. Note that assigning a value to
 * the field in the declaration (or anywhere else) overrides the assignment of
 * this annotation.
 *
 * @author JonasDoe
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Setting {
    
    /**
     * The name of the setting in the config file. If not the, the field's name
     * will be used as descriptor.
     */
    String descriptor() default "";
    
    /** The value of the setting if no value is found */
    String defaultValue() default "";
    
    /** Determines whether this setting is optional */
    boolean isOptional() default false;
    
}