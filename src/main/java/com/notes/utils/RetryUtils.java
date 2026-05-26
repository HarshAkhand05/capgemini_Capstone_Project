package com.notes.utils;

import com.notes.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * RetryUtils — Decision-Based Rerun Logic (Section 3.3)
 *
 * Implements TestNG's IRetryAnalyzer to automatically rerun flaky tests.
 * Uses AgenticDriver's shouldRetry() logic to decide if a retry is worth it.
 *
 * How to use — add to any @Test:
 *   @Test(retryAnalyzer = RetryUtils.class)
 *
 * Or add it globally in BaseTest using a TestNG listener (see McpTestListener).
 *
 * Max retries is configurable via config.properties:
 *   retry.max.count=2
 */
public class RetryUtils implements IRetryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    private int retryCount = 0;

    // Read from config or default to 2
    private static final int MAX_RETRY_COUNT;

    static {
        int count = 2;
        try {
            String val = ConfigReader.getInstance().get("retry.max.count", "2");
            count = Integer.parseInt(val);
        } catch (Exception ignored) {}
        MAX_RETRY_COUNT = count;
    }

    /**
     * TestNG calls this when a test FAILS.
     * Return true  → retry the test
     * Return false → mark it as failed permanently
     */
    @Override
    public boolean retry(ITestResult result) {
        if (retryCount >= MAX_RETRY_COUNT) {
            log.warn("[RetryUtils] Max retries ({}) reached for: {}",
                    MAX_RETRY_COUNT,
                    result.getMethod().getMethodName());
            return false;
        }

        // Decision: should we retry based on the failure reason?
        Throwable cause = result.getThrowable();
        if (cause != null && !isRetryable(cause)) {
            log.warn("[RetryUtils] Non-retryable failure — skipping retry for: {}. Reason: {}",
                    result.getMethod().getMethodName(),
                    cause.getClass().getSimpleName());
            return false;
        }

        retryCount++;
        log.warn("[RetryUtils] 🔁 Retrying test [{}/{}]: {}",
                retryCount,
                MAX_RETRY_COUNT,
                result.getMethod().getMethodName());

        // Attach retry info to Allure
        attachRetryInfo(result, retryCount);

        return true;
    }

    /**
     * Decides if the test failure type is worth retrying.
     *
     * Retryable:
     *   - WebDriver / Selenium transient errors
     *   - Timeout errors
     *   - StaleElement errors
     *
     * NOT retryable:
     *   - AssertionError  (real test failure — data mismatch)
     *   - NullPointerException from bad test data
     */
    private boolean isRetryable(Throwable cause) {
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        // Real assertion failure → do NOT retry
        if (cause instanceof AssertionError) {
            log.info("[RetryUtils] AssertionError detected — not retrying");
            return false;
        }

        // Configuration or data bugs → do NOT retry
        if (name.equals("NullPointerException")
                || name.equals("IllegalArgumentException")
                || name.equals("IllegalStateException")) {
            return false;
        }

        // Selenium transient errors → RETRY
        if (name.contains("StaleElement")
                || name.contains("Timeout")
                || name.contains("ElementClickIntercepted")
                || name.contains("ElementNotInteractable")
                || name.contains("NoSuchElement")
                || name.contains("WebDriverException")
                || message.contains("session")
                || message.contains("connection")) {
            return true;
        }

        // Default: retry for unknown RuntimeExceptions
        return cause instanceof RuntimeException;
    }

    private void attachRetryInfo(ITestResult result, int attempt) {
        try {
            io.qameta.allure.Allure.addAttachment(
                    "🔁 Retry Attempt " + attempt,
                    "text/plain",
                    "Test: " + result.getMethod().getMethodName()
                            + "\nAttempt: " + attempt + "/" + MAX_RETRY_COUNT
                            + "\nReason: " + (result.getThrowable() != null
                            ? result.getThrowable().getMessage()
                            : "unknown")
            );
        } catch (Exception ignored) {}
    }
}