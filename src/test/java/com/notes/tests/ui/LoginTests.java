package com.notes.tests.ui;

import com.notes.base.BaseTest;
import com.notes.config.ConfigReader;
import com.notes.pages.DashboardPage;
import com.notes.pages.LoginPage;
import com.notes.utils.ExcelReader;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;


@Epic("Notes App")
@Feature("UI – Login")
public class LoginTests extends BaseTest {

    private static final String EXCEL =
            System.getProperty("user.dir") + "/src/test/resources/LoginTestData.xlsx";

    @DataProvider(name = "loginData", parallel = true)
    public Iterator<Object[]> loginData() {
        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    @Test(dataProvider = "loginData",
            groups = {"ui", "login"},
            description = "Login with credentials from Excel LoginData sheet")
    @Story("Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Navigates to /login, enters credentials, asserts dashboard (PASS) or error (FAIL).")
    public void testLogin(Map<String, String> row) {
        String tcId     = row.get("TCID");
        String email    = row.get("Email");
        String password = row.get("Password");
        String expected = row.get("ExpectedResult");   // "PASS" or "FAIL"

        Allure.parameter("TCID",           tcId);
        Allure.parameter("Email",          email);
        Allure.parameter("ExpectedResult", expected);

        log.info("[{}] Login test: email={} expected={}", tcId, email, expected);

        // Navigate to login page  (browser already open via @BeforeMethod)
        getDriver().get(ConfigReader.getInstance().get("base.url") + "login");

        LoginPage loginPage = new LoginPage();

        Allure.step("Wait for login page to load", loginPage::waitForPageLoad);

        if ("PASS".equalsIgnoreCase(expected)) {
            // ── Happy path ──────────────────────────────────────────────────
            Allure.step("Enter credentials and submit", () ->
                    loginPage.login(email, password));

            Allure.step("Assert dashboard is loaded", () -> {
                DashboardPage dashboard = new DashboardPage();
                Assert.assertTrue(
                        dashboard.isDashboardLoaded(),
                        tcId + ": Dashboard (Add Note button) must be visible after valid login. "
                                + "email=" + email);
            });

            log.info("[{}] Login PASSED — dashboard reached for {}", tcId, email);

        } else {
            // ── Negative path ───────────────────────────────────────────────
            Allure.step("Enter invalid credentials and submit", () ->
                    loginPage.enterEmail(email)
                            .enterPassword(password)
                            .clickLoginExpectingFailure());

            Allure.step("Assert error message is displayed", () ->
                    Assert.assertTrue(
                            loginPage.isErrorDisplayed(),
                            tcId + ": Error message must appear for invalid credentials. "
                                    + "email=" + email + " pwd=" + password));

            log.info("[{}] Login PASSED (expected failure confirmed) for {}", tcId, email);
        }

        // Browser closed automatically by BaseTest @AfterMethod
    }
}
