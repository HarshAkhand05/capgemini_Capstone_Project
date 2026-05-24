package com.notes.base;

import com.notes.config.ConfigReader;
import com.notes.drivers.DriverFactory;
import com.notes.utils.ScreenshotUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.io.ByteArrayInputStream;
import java.time.Duration;

public class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);


    private static final ThreadLocal<WebDriver> driverHolder = new ThreadLocal<>();

    public static WebDriver getDriver() {
        return driverHolder.get();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Parameters({"browser"})
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional("chrome") String browser, ITestResult result) {
        log.info("[Thread-{}] ▶ Starting '{}' browser for: {}",
                Thread.currentThread().getId(),
                browser,
                result.getMethod().getMethodName());

        WebDriver driver = DriverFactory.createDriver(browser);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        driver.manage().window().maximize();
        driverHolder.set(driver);

        // Navigate to base URL so every test starts from a known state
        driver.get(ConfigReader.getInstance().get("base.url"));
    }


    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        WebDriver driver = driverHolder.get();
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                log.error("[Thread-{}] ✖ FAILED: {}",
                        Thread.currentThread().getId(),
                        result.getMethod().getMethodName());
                captureScreenshot(result.getMethod().getMethodName(), driver);
            } else if (result.getStatus() == ITestResult.SUCCESS) {
                log.info("[Thread-{}] ✔ PASSED: {}",
                        Thread.currentThread().getId(),
                        result.getMethod().getMethodName());
            }
        } finally {

            if (driver != null) {
                try {
                    driver.quit();
                    log.info("[Thread-{}] Browser closed for: {}",
                            Thread.currentThread().getId(),
                            result.getMethod().getMethodName());
                } catch (Exception e) {
                    log.warn("driver.quit() threw an exception: {}", e.getMessage());
                } finally {
                    driverHolder.remove();
                }
            }
        }
    }

    // ── Screenshot helpers ─────────────────────────────────────────────────

    private void captureScreenshot(String testName, WebDriver driver) {
        if (driver == null) return;
        try {
            byte[] bytes = ScreenshotUtils.takeScreenshotAsBytes(driver);
            if (bytes.length > 0) {
                // 1. Attach to Allure report (inline in the timeline)
                attachScreenshotToAllure(testName, bytes);
                // 2. Save to disk under target/screenshots/
                String path = ScreenshotUtils.saveScreenshot(driver, testName);
                log.info("Screenshot saved: {}", path);
            }
        } catch (Exception e) {
            log.warn("Could not capture screenshot for '{}': {}", testName, e.getMessage());
        }
    }


    private void attachScreenshotToAllure(
            String testName,
            byte[] screenshot) {

        Allure.addAttachment(
                "Screenshot - " + testName,
                new ByteArrayInputStream(screenshot)
        );
    }
}
