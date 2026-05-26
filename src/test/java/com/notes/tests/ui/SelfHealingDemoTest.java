package com.notes.tests.ui;

import com.notes.base.BaseTest;
import com.notes.utils.SelfHealingLocator;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SelfHealingDemoTest extends BaseTest {

    @Test(groups = {"ui"})
    public void testSelfHealingFallbackLocator() {

        // Navigate to login page so real elements exist on page
        getDriver().get(
                "https:/practice.expandtesting.com/notes/app/login"
        );

        log.info("[SelfHealing Demo] Testing self-healing with intentionally broken primary locator");

        // Primary locator is INTENTIONALLY WRONG — simulates a broken locator
        // Fallback 1 (By.id("email")) is REAL — healing will succeed here
        // Fallback 2 is also real — backup if fallback 1 fails
        WebElement emailField = SelfHealingLocator.find(
                By.id("THIS_ID_DOES_NOT_EXIST"),        // primary — will fail
                By.id("email"),                          // fallback 1 — heals here
                By.cssSelector("input[type='email']")    // fallback 2 — backup
        );

        Assert.assertNotNull(
                emailField,
                "Self-healing should have found the email field via fallback"
        );

        log.info("[SelfHealing Demo] ✅ Self-healing worked! Tag: {}", emailField.getTagName());
    }
}