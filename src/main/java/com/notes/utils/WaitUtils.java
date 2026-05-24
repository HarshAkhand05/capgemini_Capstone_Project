package com.notes.utils;

import com.notes.base.BaseTest;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;


public class WaitUtils {

    private static final int DEFAULT_TIMEOUT = 20;
    private static final int LONG_TIMEOUT    = 40;
    private static final int SHORT_TIMEOUT   = 5;

    private WaitUtils() {}

    private static WebDriverWait wait(int seconds) {
        return new WebDriverWait(BaseTest.getDriver(), Duration.ofSeconds(seconds));
    }

    // ── Visibility ─────────────────────────────────────────────────────────


    public static WebElement waitForVisible(By locator) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.visibilityOfElementLocated(locator));
    }


    public static WebElement waitForVisible(By locator, int seconds) {
        return wait(seconds).until(
                ExpectedConditions.visibilityOfElementLocated(locator));
    }

    // ── Clickability ───────────────────────────────────────────────────────


    public static WebElement waitForClickable(By locator) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.elementToBeClickable(locator));
    }

    public static WebElement waitForClickable(WebElement element) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.elementToBeClickable(element));
    }

    // ── Presence ───────────────────────────────────────────────────────────


    public static WebElement waitForPresence(By locator) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.presenceOfElementLocated(locator));
    }

    // ── Text ───────────────────────────────────────────────────────────────

    public static boolean waitForTextContains(By locator, String text) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }

    // ── URL ────────────────────────────────────────────────────────────────


    public static boolean waitForUrlContains(String fragment) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.urlContains(fragment));
    }


    public static boolean waitForUrlNotContains(String fragment) {
        return wait(DEFAULT_TIMEOUT).until(driver ->
                !driver.getCurrentUrl().contains(fragment));
    }

    // ── Invisibility ───────────────────────────────────────────────────────


    public static boolean waitForInvisible(By locator) {
        return wait(DEFAULT_TIMEOUT).until(
                ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    // ── Alert ──────────────────────────────────────────────────────────────

    public static Alert waitForAlert() {
        return wait(SHORT_TIMEOUT).until(ExpectedConditions.alertIsPresent());
    }

    // ── Page-load (JS-level) ───────────────────────────────────────────────


    public static void waitForPageLoad() {
        wait(LONG_TIMEOUT).until(driver ->
                ((JavascriptExecutor) driver)
                        .executeScript("return document.readyState").equals("complete"));
    }


    public static void waitForJQueryIdle() {
        try {
            wait(DEFAULT_TIMEOUT).until(driver ->
                    (Boolean) ((JavascriptExecutor) driver)
                            .executeScript("return jQuery.active === 0"));
        } catch (Exception ignored) {}
    }

    // ── Note-specific wait ─────────────────────────────────────────────────


    public static void waitForNotePresent(String title) {

        By noteTitle = By.xpath(
                "//*[@data-testid='note-card-title' and contains(text(),'"
                        + title + "')]");

        wait(DEFAULT_TIMEOUT)
                .until(ExpectedConditions
                        .visibilityOfElementLocated(noteTitle));
    }

    // ── Count-based wait ───────────────────────────────────────────────────


    public static void waitForCountGreaterThan(By locator, int minimumCount) {
        wait(DEFAULT_TIMEOUT).until(driver ->
                driver.findElements(locator).size() > minimumCount);
    }
}
