package com.notes.utils;

import com.notes.base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * AgenticDriver — Section 3.3 Agentic Automation
 *
 * Provides intelligent, self-managing interactions with Selenium:
 *
 *  1. Auto-retry mechanism   → Retries flaky UI steps automatically
 *  2. Intelligent waiting    → Combines JS polling + explicit waits
 *  3. Decision-based rerun   → Decides whether to retry or abort based on error type
 *  4. Self-healing clicks    → Falls back to JS click when normal click fails
 *
 * Usage:
 *   // Instead of plain click:
 *   AgenticDriver.agentClick(By.id("submit"));
 *
 *   // Retry any action up to N times:
 *   AgenticDriver.retryAction(() -> somePageMethod(), 3);
 *
 *   // Wait intelligently for an element:
 *   WebElement el = AgenticDriver.intelligentWait(By.id("note-title"));
 */
public class AgenticDriver {

    private static final Logger log = LoggerFactory.getLogger(AgenticDriver.class);

    // Max retries for flaky steps
    private static final int MAX_RETRIES = 3;

    // Delay between retries (ms)
    private static final int RETRY_DELAY_MS = 1000;

    private AgenticDriver() {}

    // ── 1. Auto-retry mechanism ──────────────────────────────────────────────

    /**
     * Retries the given action up to maxRetries times.
     * Uses decision-based logic to determine if retry is worth attempting.
     *
     * @param action     Lambda of the action to retry
     * @param maxRetries Number of attempts
     * @param <T>        Return type of the action
     * @return Result of the action
     */
    public static <T> T retryAction(Supplier<T> action, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                log.info("[Agentic] Attempt {}/{}", attempt, maxRetries);
                T result = action.get();
                log.info("[Agentic] Action succeeded on attempt {}", attempt);
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("[Agentic] Attempt {} FAILED: {}", attempt, e.getMessage());

                // Decision-based: decide if retry makes sense
                if (!shouldRetry(e, attempt, maxRetries)) {
                    log.error("[Agentic] Decision: ABORT — error is not retryable: {}",
                            e.getClass().getSimpleName());
                    break;
                }

                log.info("[Agentic] Decision: RETRY after {}ms delay...", RETRY_DELAY_MS);
                sleep(RETRY_DELAY_MS);

                // Intelligent recovery between retries
                recoverBetweenRetries();
            }
        }

        throw new RuntimeException(
                "[Agentic] All " + maxRetries + " attempts failed. Last error: "
                        + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException
        );
    }

    /**
     * Overload with default MAX_RETRIES.
     */
    public static <T> T retryAction(Supplier<T> action) {
        return retryAction(action, MAX_RETRIES);
    }

    /**
     * Retries a void action (Runnable).
     */
    public static void retryVoid(Runnable action, int maxRetries) {
        retryAction(() -> {
            action.run();
            return null;
        }, maxRetries);
    }

    public static void retryVoid(Runnable action) {
        retryVoid(action, MAX_RETRIES);
   }
    // ── 2. Intelligent waiting ───────────────────────────────────────────────

    /**
     * Intelligent wait: combines JS readyState check + explicit visibility wait.
     * More reliable than either approach alone.
     *
     * @param locator The By locator to wait for
     * @return        The visible WebElement
     */
    public static WebElement intelligentWait(By locator) {
        log.info("[Agentic] Intelligent wait for: {}", locator);

        // Step 1: Wait for JS page load
        waitForJsReady();

        // Step 2: Wait for React/Angular/Vue rendering to settle
        waitForDomStability();

        // Step 3: Explicit visibility wait
        WebElement element = WaitUtils.waitForVisible(locator);
        log.info("[Agentic] Element found after intelligent wait: {}", locator);
        return element;
    }

    /**
     * Wait for document.readyState === 'complete' using JS polling.
     */
    public static void waitForJsReady() {
        WebDriver driver = BaseTest.getDriver();
        long timeout = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < timeout) {
            String state = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState");
            if ("complete".equals(state)) {
                log.debug("[Agentic] JS ready state: complete");
                return;
            }
            sleep(200);
        }
        log.warn("[Agentic] JS readyState did not reach 'complete' within 30s");
    }

    /**
     * Waits for DOM to stop mutating (checks element count stability).
     * Useful after SPA navigation where content loads asynchronously.
     */
    public static void waitForDomStability() {
        WebDriver driver = BaseTest.getDriver();
        int previousCount = -1;
        int stableCount = 0;
        long timeout = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < timeout) {
            int currentCount = driver.findElements(By.cssSelector("*")).size();
            if (currentCount == previousCount) {
                stableCount++;
                if (stableCount >= 3) {
                    log.debug("[Agentic] DOM stable with {} elements", currentCount);
                    return;
                }
            } else {
                stableCount = 0;
            }
            previousCount = currentCount;
            sleep(300);
        }
        log.warn("[Agentic] DOM did not stabilise within 10s — proceeding anyway");
    }

    // ── 3. Decision-based rerun logic ────────────────────────────────────────

    /**
     * Decides whether to retry based on the exception type and attempt number.
     *
     * Retryable:
     *   - StaleElementReferenceException   (element detached from DOM)
     *   - ElementClickInterceptedException  (element overlapped briefly)
     *   - NoSuchElementException            (element not yet rendered)
     *   - TimeoutException                  (page load delay)
     *
     * Not retryable (abort immediately):
     *   - AssertionError                    (test has genuinely failed)
     *   - IllegalArgumentException           (bad test data / config bug)
     *   - SessionNotCreatedException        (browser crashed)
     */
    static boolean shouldRetry(Exception e, int attempt, int maxRetries) {
        if (attempt >= maxRetries) {
            log.warn("[Agentic] Max retries reached — giving up");
            return false;
        }

        String exName = e.getClass().getSimpleName();

        // Retryable exception types
        boolean retryable = exName.contains("StaleElement")
                || exName.contains("ElementClickIntercepted")
                || exName.contains("NoSuchElement")
                || exName.contains("Timeout")
                || exName.contains("ElementNotInteractable")
                || exName.contains("WebDriverException");

        if (!retryable) {
            log.warn("[Agentic] Non-retryable exception: {}", exName);
        }
        return retryable;
    }

    // ── 4. Self-healing click ────────────────────────────────────────────────

    /**
     * Agentic click: tries normal click → JS click → self-healed locator click.
     * Logs each healing step.
     */
    public static void agentClick(By primary, By... fallbacks) {
        log.info("[Agentic] agentClick on: {}", primary);

        // Try normal click with retry
        try {
            retryVoid(() -> {
                WebElement el = WaitUtils.waitForClickable(primary);
                el.click();
            }, 2);
            return;
        } catch (Exception e) {
            log.warn("[Agentic] Normal click failed on {}. Trying JS click...", primary);
        }

        // Try JS click
        try {
            WebElement el = WaitUtils.waitForPresence(primary);
            ((JavascriptExecutor) BaseTest.getDriver())
                    .executeScript("arguments[0].click();", el);
            log.info("[Agentic] JS click succeeded for: {}", primary);
            return;
        } catch (Exception e) {
            log.warn("[Agentic] JS click also failed. Activating self-healing...");
        }

        // Self-healing: try fallback locators
        WebElement healed = SelfHealingLocator.find(primary, fallbacks);
        healed.click();
        log.info("[Agentic] Self-healed click succeeded.");
    }

    /**
     * Agentic type: retries clearing and typing into a field.
     */
    public static void agentType(By locator, String text) {
        retryVoid(() -> {
            WebElement el = WaitUtils.waitForVisible(locator);
            el.clear();
            el.sendKeys(text);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Attempts recovery actions between retries:
     * - Scroll to top
     * - Wait for page stability
     */
    private static void recoverBetweenRetries() {
        try {
            WebDriver driver = BaseTest.getDriver();
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0,0)");
            waitForJsReady();
        } catch (Exception e) {
            log.debug("[Agentic] Recovery step failed (non-critical): {}", e.getMessage());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}