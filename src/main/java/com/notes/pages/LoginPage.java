package com.notes.pages;

import com.notes.utils.WaitUtils;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    private static final By EMAIL_FIELD    = By.id("email");
    private static final By PASSWORD_FIELD = By.id("password");
    private static final By LOGIN_BTN =
            By.cssSelector("[data-testid='login-submit']");
    private static final By ERROR_MSG      = By.cssSelector(".alert-danger, [role='alert']");

    public LoginPage enterEmail(String email) {
        type(EMAIL_FIELD, email != null ? email : "");
        return this;
    }

    public LoginPage enterPassword(String pwd) {
        type(PASSWORD_FIELD, pwd != null ? pwd : "");
        return this;
    }


    public DashboardPage clickLogin() {
        click(LOGIN_BTN);
        // Wait for the URL to move away from the login page before handing back
        // a DashboardPage — this prevents the test from asserting on a mid-redirect DOM.
        WaitUtils.waitForUrlNotContains("login");
        return new DashboardPage();
    }

    /**
     * Clicks login when we expect the page to STAY on login (negative tests).
     * Does NOT wait for a URL change — we wait for the error message instead.
     */
    public LoginPage clickLoginExpectingFailure() {
        click(LOGIN_BTN);
        return this;
    }

    /** Convenience: enter email + password + click login (happy path). */
    public DashboardPage login(
            String email,
            String password) {

        log.info("Logging in with: {}", email);

        enterEmail(email)
                .enterPassword(password);

        return clickLogin();
    }
    /**
     * Waits up to DEFAULT_TIMEOUT (20 s) for the error alert to become visible.
     * The alert is populated by an async API call — using isDisplayed()'s 5 s
     * shortcut was not enough in slow environments.
     *
     * @return true if error alert appears within timeout
     */
    public boolean isErrorDisplayed() {
        try {
            WaitUtils.waitForVisible(ERROR_MSG); // 20 s default
            return true;
        } catch (Exception e) {
            log.warn("Login error message did not appear: {}", e.getMessage());
            return false;
        }
    }

    public String getErrorMessage() {
        WaitUtils.waitForVisible(ERROR_MSG);
        return getText(ERROR_MSG);
    }

    /**
     * Waits for the login page to be fully interactive.
     * Called explicitly in every test after navigation.
     */
    public void waitForPageLoad() {
        WaitUtils.waitForPageLoad();            // document.readyState === 'complete'
        WaitUtils.waitForVisible(LOGIN_BTN);    // button rendered
        WaitUtils.waitForClickable(LOGIN_BTN);  // button enabled
    }
}
