package com.notes.utils;

import java.util.UUID;

/**
 * Resolves AUTO_GEN tokens from Excel into unique runtime values.
 * Prevents parallel test collisions on email / title fields.
 */
public class TestDataFactory {

    private static final String AUTO_GEN = "AUTO_GEN";
    private TestDataFactory() {}

    public static String resolve(String value) {
        return AUTO_GEN.equalsIgnoreCase(value) ? generate() : value;
    }

    public static String resolveEmail(String value) {
        return AUTO_GEN.equalsIgnoreCase(value) ? generateEmail() : value;
    }

    public static String resolvePassword(String value) {
        return AUTO_GEN.equalsIgnoreCase(value) ? generatePassword() : value;
    }

    public static String generate() {
        return "auto_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public static String generateEmail() {
        return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "@notes.io";
    }

    public static String generatePassword() {
        return "Pass_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "!1";
    }
}