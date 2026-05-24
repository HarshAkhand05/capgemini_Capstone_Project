package com.notes.pages;

import com.notes.utils.WaitUtils;
import org.openqa.selenium.By;

public class RegisterPage extends BasePage {

    private static final By NAME_FIELD     = By.id("name");
    private static final By EMAIL_FIELD    = By.id("email");
    private static final By PASSWORD_FIELD = By.id("password");
    private static final By CONFIRM_FIELD  = By.id("confirmPassword");
    private static final By REGISTER_BTN   = By.cssSelector("[data-testid='register-submit']");
    private static final By SUCCESS_MSG =
            By.cssSelector(".alert.alert-success");
    private static final By ERROR_MSG =
            By.cssSelector(".alert.alert-danger");

    public RegisterPage enterName(String name) {
        type(NAME_FIELD, name);
        return this;
    }

    public RegisterPage enterEmail(String email) {
        type(EMAIL_FIELD, email);
        return this;
    }

    public RegisterPage enterPassword(String pwd) {
        type(PASSWORD_FIELD, pwd);
        return this;
    }

    public RegisterPage enterConfirm(String pwd) {
        type(CONFIRM_FIELD, pwd);
        return this;
    }

    public void clickRegister() {
        click(REGISTER_BTN);
    }

    /**
     * Fills the entire registration form and clicks Register.
     * Password is used for both password and confirm fields.
     */
    public void registerUser(String name, String email, String password) {
        log.info("Registering user: {}", email);
        enterName(name)
                .enterEmail(email)
                .enterPassword(password)
                .enterConfirm(password);
        clickRegister();
    }

    /**
     * Waits up to DEFAULT_TIMEOUT (20 s) for the success alert to appear.
     * The alert is populated by an async API call so we need the full explicit wait —
     * NOT the 5-second shortcut used by isDisplayed().
     *
     * @return true if the success alert becomes visible within the timeout
     */
    public boolean isRegistrationSuccessful() {

        try {

            WaitUtils.waitForVisible(SUCCESS_MSG);

            return true;

        } catch (Exception e) {

            log.warn("Registration failed: {}", e.getMessage());

            return false;
        }
    }
    /**
     * Waits up to DEFAULT_TIMEOUT for the error alert to appear.
     * Used by negative registration tests.
     */
    public boolean isErrorDisplayed() {
        try {
            WaitUtils.waitForVisible(ERROR_MSG); // 20 s default
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getErrorMessage() {
        WaitUtils.waitForVisible(ERROR_MSG);
        return getText(ERROR_MSG);
    }

    /**
     * Waits for the registration page to be fully interactive before the test body runs.
     * Called explicitly in every test after navigation.
     */
    public void waitForPageLoad() {
        WaitUtils.waitForPageLoad();           // document.readyState === 'complete'
        WaitUtils.waitForVisible(REGISTER_BTN); // button is rendered and visible
        WaitUtils.waitForClickable(REGISTER_BTN); // button is enabled / not covered
    }
}
