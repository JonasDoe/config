package config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a nested {@link Config}. The prefix (which will be removed from all
 * relating setting names) defines which part of the settings belong to this
 * {@code Subconfig}. Further nesting with further prefixes is possible.
 *
 * @author JonasDoe
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NestedConfig {
    
    /**
     * The prefix of the subconfig. Will be removed from the settings' names.
     */
    String prefix();
}