package com.notes.drivers;

import com.notes.config.ConfigReader;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    // ── Grid running on YOUR machine ───────────────────────────────────────
    private static final String GRID_URL = "http://localhost:4444";

    private DriverFactory() {}

    public static WebDriver createDriver(String browser) {
        boolean headless = Boolean.parseBoolean(
                ConfigReader.getInstance().get("headless", "false"));

        log.info("Launching '{}' via Selenium Grid: {}", browser, GRID_URL);

        try {
            URL gridUrl = new URL(GRID_URL);

            return switch (browser.toLowerCase().trim()) {

                case "edge" -> {
                    EdgeOptions opts = new EdgeOptions();
                    if (headless) opts.addArguments("--headless");
                    opts.addArguments(
                            "--start-maximized",
                            "--disable-gpu",
                            "--no-sandbox",
                            "--disable-dev-shm-usage"
                    );
                    // Grid node auto downloaded EdgeDriver
                    yield new RemoteWebDriver(gridUrl, opts);
                }

                case "firefox" -> {
                    FirefoxOptions opts = new FirefoxOptions();
                    if (headless) opts.addArguments("--headless");
                    // Grid node auto downloaded GeckoDriver
                    yield new RemoteWebDriver(gridUrl, opts);
                }

                default -> {
                    // Chrome — default browser
                    ChromeOptions opts = new ChromeOptions();
                    if (headless) opts.addArguments("--headless=new");
                    opts.addArguments(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--window-size=1920,1080",
                            "--disable-gpu"
                    );
                    // Grid node auto downloaded ChromeDriver ✅
                    yield new RemoteWebDriver(gridUrl, opts);
                }
            };

        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Grid URL: " + GRID_URL, e);
        }
    }
}