package com.notes.tests.ui;

import com.notes.base.BaseTest;
import com.notes.config.ConfigReader;
import com.notes.pages.AddNotePage;
import com.notes.pages.DashboardPage;
import com.notes.pages.LoginPage;
import com.notes.utils.ExcelReader;
import com.notes.utils.WaitUtils;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;

@Epic("Notes App")
@Feature("UI - Note Creation")
public class NoteCreationTests extends BaseTest {

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/NoteCreationTestData.xlsx";

    @DataProvider(name = "noteData", parallel = true)
    public Iterator<Object[]> noteData() {

        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    private DashboardPage loginUser(
            String email,
            String password) {

        // Open login page
        getDriver().get(
                ConfigReader.getInstance().get("base.url") + "login");

        LoginPage loginPage = new LoginPage();

        // Wait for login page
        loginPage.waitForPageLoad();

        // Login
        DashboardPage dashboard =
                loginPage.login(email, password);

        // Verify dashboard loaded
        Assert.assertTrue(
                dashboard.isDashboardLoaded(),
                "Dashboard should load after login");

        return dashboard;
    }

    @Test(
            dataProvider = "noteData",
            groups = {"ui", "notes"},
            description = "Login and create note using Excel data"
    )
    @Story("Create Note")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Login using Excel credentials and create note")

    public void testCreateNote(Map<String, String> row) {

        String tcId      = row.get("TCID");
        String email     = row.get("Email");
        String password  = row.get("Password");
        String category  = row.get("Category");
        String title     = row.get("Title")+"_"+System.currentTimeMillis();
        String desc      = row.get("NoteDescription")+"_"+System.currentTimeMillis();
        String expected  = row.get("ExpectedResult");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Title", title);

        log.info(
                "[{}] Note creation started",
                tcId);

        // ───────────── LOGIN ─────────────

        DashboardPage dashboard = Allure.step(
                "Login and wait for dashboard",
                () -> loginUser(email, password));

        int notesBefore =
                dashboard.getNoteCount();

        log.info(
                "[{}] Notes before creation: {}",
                tcId,
                notesBefore);

        // ───────── OPEN ADD NOTE ─────────

        AddNotePage addNotePage = Allure.step(
                "Click Add Note",
                dashboard::clickAddNote);

        Allure.step(
                "Wait for Add Note modal",
                addNotePage::waitForPageLoad);

        // ───────── CREATE NOTE ─────────

        if ("PASS".equalsIgnoreCase(expected)) {

            DashboardPage updatedDashboard =
                    Allure.step(
                            "Fill note form and submit",
                            () -> addNotePage.createNote(
                                    category,
                                    title,
                                    desc));

            // Wait for note card
            Allure.step(
                    "Wait for note visibility",
                    () -> WaitUtils.waitForNotePresent(title));

            // Verify note visible
            Allure.step(
                    "Verify note present",
                    () -> Assert.assertTrue(
                            updatedDashboard.isNotePresent(title),
                            "Created note should appear on dashboard"));

            // Verify note count increased
            Allure.step(
                    "Verify note count increased",
                    () -> Assert.assertTrue(
                            updatedDashboard.getNoteCount() > notesBefore,
                            "Note count should increase"));

            log.info(
                    "[{}] Note creation successful",
                    tcId);

        } else {

            // Negative test
            Allure.step(
                    "Submit invalid note",
                    () -> {

                        addNotePage.clickCreateExpectingError();

                        Assert.assertTrue(
                                addNotePage.isErrorDisplayed(),
                                "Validation error should appear");
                    });

            log.info(
                    "[{}] Negative scenario validated",
                    tcId);
        }
    }
}