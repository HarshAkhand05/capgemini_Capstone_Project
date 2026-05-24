package com.notes.tests.ui;

import com.notes.base.BaseTest;
import com.notes.config.ConfigReader;
import com.notes.pages.RegisterPage;
import com.notes.utils.ExcelReader;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;

@Epic("Notes App")
@Feature("UI - Registration")
public class RegistrationTests extends BaseTest {

    private static final String EXCEL =
            System.getProperty("user.dir") + "/src/test/resources/RegistrationTestData.xlsx";

    @DataProvider(name = "registrationData", parallel = true)
    public Iterator<Object[]> registrationData() {
        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    @Test(dataProvider = "registrationData",
            groups = {"ui", "registration"},
            description = "Register a user from Excel RegistrationData sheet")
    @Story("Registration")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Navigates to /register, fills Name/Email/Password from Excel, asserts success message.")
    public void testRegistration(Map<String, String> row) {
        String tcId     = row.get("TCID");
        String name     = row.get("Name");
        String email    = row.get("Email")
                .replace("@gmail.com",
                        System.currentTimeMillis() + "@gmail.com");;
        String password = row.get("Password");

        Allure.parameter("TCID",     tcId);
        Allure.parameter("Name",     name);
        Allure.parameter("Email",    email);

        log.info("[{}] Registering user: name={} email={}", tcId, name, email);

        // Step 1: Navigate to registration page
        Allure.step("Navigate to /register", () ->
                getDriver().get(ConfigReader.getInstance().get("base.url") + "register"));

        RegisterPage registerPage = new RegisterPage();

        // Step 2: Wait for the page to fully load
        Allure.step("Wait for registration page to load", registerPage::waitForPageLoad);

        // Step 3: Fill and submit the form
        Allure.step("Fill Name=" + name + ", Email=" + email + ", Password=***", () ->
                registerPage.registerUser(name, email, password));

        // Step 4: Assert success message
        Allure.step("Assert registration success message is displayed", () ->
                Assert.assertTrue(
                        registerPage.isRegistrationSuccessful(),
                        tcId + ": Registration success message must appear. "
                                + "email=" + email));

        // Step 5: Store credentials for NoteCreationTests
        log.info("[{}] Registration PASSED -- credentials stored for {}", tcId, email);

        // Browser is closed automatically by BaseTest @AfterMethod
    }
}
