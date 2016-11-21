package config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Internal utility methods
 *
 * @author JonasDoe
 */
public class ConfigUtils {
    /**
     * This a is a pure utility class which should not be instantiated
     */
    private ConfigUtils() {
    }
    
    /**
     * Creates a {@code Class} based on a given name and throws an
     * {@code Unchecked Exception} if that fails.
     *
     * @param name
     *         of the {@code Class} to be created
     * @return IllegalArgumentException if the creation fails.
     */
    public static Class<?> createClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Creates a {{@code URL}based on a given name and throws an
     * {@code Unchecked Exception} if that fails.
     *
     * @param name
     *         of the {@code URL} to be created
     * @return IllegalArgumentException if the creation fails.
     */
    public static URI createURI(String name) {
        try {
            return new URI(name);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    /**
     * Creates a {@code URL} based on a given name and throws an
     * {@code Unchecked Exception} if that fails.
     *
     * @param name
     *         of the {@code URL} to be created
     * @return IllegalArgumentException if the creation fails.
     */
    public static URL createURL(String name) {
        try {
            return new URL(name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
