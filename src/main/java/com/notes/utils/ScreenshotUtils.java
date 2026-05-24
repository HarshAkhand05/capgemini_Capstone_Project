package com.notes.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ScreenshotUtils {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotUtils.class);
    private static final String SCREENSHOT_DIR = "target/screenshots/";

    private ScreenshotUtils() {}


    public static byte[] takeScreenshotAsBytes(WebDriver driver) {
        if (driver == null) {
            log.warn("takeScreenshotAsBytes called with null driver — skipping.");
            return new byte[0];
        }
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.warn("Screenshot capture failed: {}", e.getMessage());
            return new byte[0];
        }
    }


    public static String saveScreenshot(WebDriver driver, String testName) {
        byte[] bytes = takeScreenshotAsBytes(driver);
        if (bytes.length == 0) return "";

        try {
            Path dir = Paths.get(SCREENSHOT_DIR);
            Files.createDirectories(dir);

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

            // Sanitise testName: replace characters that are invalid in filenames
            String safeName = testName.replaceAll("[^a-zA-Z0-9_\\-]", "_");

            Path dest = dir.resolve(safeName + "_" + timestamp + ".png");
            Files.write(dest, bytes);

            log.info("Screenshot saved to: {}", dest.toAbsolutePath());
            return dest.toAbsolutePath().toString();

        } catch (IOException e) {
            log.warn("Failed to save screenshot for '{}': {}", testName, e.getMessage());
            return "";
        }
    }
}
