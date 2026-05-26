package com.notes.utils;

import com.notes.base.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * SelfHealingLocator
 *
 * Tries the primary locator first.
 * If it fails, it silently tries each fallback locator in order.
 * If all fail, it calls McpSeleniumClient to ask Claude for a new locator suggestion.
 *
 * Usage (inside any Page class):
 *   WebElement el = SelfHealingLocator.find(
 *       By.id("note-submit"),                          // primary
 *       By.cssSelector("[data-testid='note-submit']"), // fallback 1
 *       By.xpath("//button[@type='submit']")           // fallback 2
 *   );
 */
public class SelfHealingLocator {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingLocator.class);

    private SelfHealingLocator() {}

    /**
     * Try primary locator; if it fails try each fallback.
     * If all fail, ask MCP (Claude) for a locator suggestion and try that too.
     *
     * @param primary   The main locator to try first
     * @param fallbacks Zero or more fallback locators
     * @return          The first successfully found WebElement
     * @throws RuntimeException if no locator works
     */
    public static WebElement find(By primary, By... fallbacks) {

        // 1. Try primary
        WebElement element = tryLocator(primary);
        if (element != null) {
            log.info("[SelfHealing] Primary locator succeeded: {}", primary);
            return element;
        }

        log.warn("[SelfHealing] Primary locator FAILED: {} — trying fallbacks...", primary);

        // 2. Try each fallback
        List<By> fallbackList = Arrays.asList(fallbacks);
        for (By fallback : fallbackList) {
            element = tryLocator(fallback);
            if (element != null) {
                log.warn("[SelfHealing] Fallback locator succeeded: {}", fallback);
                attachHealingEventToAllure(primary.toString(), fallback.toString());
                return element;
            }
            log.warn("[SelfHealing] Fallback FAILED: {}", fallback);
        }

        // 3. Ask MCP (Claude API) for a new locator suggestion
        log.warn("[SelfHealing] All fallbacks failed. Asking MCP for locator suggestion...");
        String mcpSuggestion = McpSeleniumClient.suggestLocator(
                primary.toString(),
                BaseTest.getDriver().getPageSource()
        );

        if (mcpSuggestion != null && !mcpSuggestion.isBlank()) {
            log.info("[SelfHealing] MCP suggested locator: {}", mcpSuggestion);
            By mcpLocator = parseMcpLocator(mcpSuggestion);
            element = tryLocator(mcpLocator);
            if (element != null) {
                log.info("[SelfHealing] MCP locator succeeded!");
                attachHealingEventToAllure(primary.toString(), mcpSuggestion + " [via MCP]");
                return element;
            }
        }

        throw new RuntimeException(
                "[SelfHealing] ALL locators failed for primary: " + primary
                        + " | Fallbacks tried: " + fallbackList
                        + " | MCP suggestion: " + mcpSuggestion
        );
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /**
     * Tries to find an element using the given locator.
     * Returns null instead of throwing if not found.
     */
    private static WebElement tryLocator(By locator) {
        try {
            WebElement el = WaitUtils.waitForVisible(locator, 5);
            return el;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses the MCP suggestion string into a By locator.
     * MCP is prompted to return exactly one of:
     *   css::selector-here
     *   xpath:://xpath-here
     *   id::element-id-here
     */
    private static By parseMcpLocator(String suggestion) {
        String clean = suggestion.trim();
        if (clean.startsWith("css::")) {
            return By.cssSelector(clean.substring(5).trim());
        } else if (clean.startsWith("xpath::")) {
            return By.xpath(clean.substring(7).trim());
        } else if (clean.startsWith("id::")) {
            return By.id(clean.substring(4).trim());
        } else {
            // Default: treat as CSS selector
            log.warn("[SelfHealing] MCP locator format unknown, treating as CSS: {}", clean);
            return By.cssSelector(clean);
        }
    }

    /**
     * Logs a healing event into the Allure report as an attachment.
     */
    private static void attachHealingEventToAllure(String original, String healed) {
        try {
            io.qameta.allure.Allure.addAttachment(
                    "🔧 Self-Healing Event",
                    "text/plain",
                    "Original locator FAILED : " + original + "\n"
                            + "Healed  locator USED   : " + healed
            );
        } catch (Exception ignored) {}
    }
}
