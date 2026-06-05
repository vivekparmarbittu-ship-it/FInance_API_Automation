package utilities;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Enhanced ConfigReader — loads from classpath first (works in JAR),
 * falls back to filesystem path.
 */
public class ConfigReader {
    private static Properties properties;

    static {
        properties = new Properties();
        try {
            // 1. Try classpath (works in JAR and IDE)
            InputStream classpathStream = ConfigReader.class.getClassLoader()
                    .getResourceAsStream("config.properties");
            if (classpathStream != null) {
                properties.load(classpathStream);
                classpathStream.close();
            } else {
                // 2. Fallback to filesystem (backward compatibility)
                FileInputStream fis = new FileInputStream("src/main/resources/config.properties");
                properties.load(fis);
                fis.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    /**
     * Get a property value. Returns null if not found.
     */
    public static String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get a property value with a default fallback.
     */
    public static String getOrDefault(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get a property value. Throws RuntimeException if not found.
     */
    public static String getRequired(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(
                    "Required config property '" + key + "' is missing or empty in config.properties");
        }
        return value.trim();
    }

    /**
     * Get a property as int with a default fallback.
     */
    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a property as boolean with a default fallback.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
