package com.notes.config;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final Properties props = new Properties();
    private static volatile ConfigReader instance;

    private ConfigReader() {
        try (InputStream is = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new RuntimeException("config.properties not found on classpath");
            }
            props.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static ConfigReader getInstance() {
        if (instance == null) {
            synchronized (ConfigReader.class) {
                if (instance == null) {
                    instance = new ConfigReader();
                }
            }
        }
        return instance;
    }


    public String get(String key) {
        String v = props.getProperty(key);
        if (v == null) throw new RuntimeException("Property not found: " + key);
        return v.trim();
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }
}
