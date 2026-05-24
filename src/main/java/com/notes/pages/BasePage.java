package com.notes.pages;

import com.notes.base.BaseTest;
import com.notes.utils.WaitUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class BasePage {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected WebDriver driver() {
        return BaseTest.getDriver();
    }

    protected void click(By locator) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                WebElement element = WaitUtils.waitForClickable(locator);
                scrollIntoView(element);
                element.click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                attempts++;
                log.warn("Click failed on attempt {} for locator {}. Retrying...", attempts, locator);
                if (attempts == 3) {
                    jsClick(locator);
                }
            }
        }
    }

    /** Clear + sendKeys with explicit visibility wait. */
    protected void type(By locator, String text) {
        WebElement el = WaitUtils.waitForVisible(locator);
        scrollIntoView(el);
        el.clear();
        el.sendKeys(text != null ? text : "");
    }

    /** Select a <select> dropdown option by visible text. */
    protected void selectByVisibleText(By locator, String text) {
        WebElement dropdown = WaitUtils.waitForVisible(locator);
        scrollIntoView(dropdown);
        new Select(dropdown).selectByVisibleText(text);
    }

    /** Returns trimmed visible text of the element. */
    protected String getText(By locator) {
        return WaitUtils.waitForVisible(locator).getText().trim();
    }

    /** Returns true if the element is visible within 5 seconds; false otherwise. */
    protected boolean isDisplayed(By locator) {
        try {
            return WaitUtils.waitForVisible(locator, 5).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /** JavaScript click — used as a last resort when normal click is blocked. */
    protected void jsClick(By locator) {
        WebElement el = WaitUtils.waitForPresence(locator);
        scrollIntoView(el);
        ((JavascriptExecutor) driver()).executeScript("arguments[0].click();", el);
    }

    /** Scrolls the element into the centre of the viewport. */
    protected void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver()).executeScript(
                "arguments[0].scrollIntoView({block: 'center', inline: 'center'});",
                element);
    }
}
