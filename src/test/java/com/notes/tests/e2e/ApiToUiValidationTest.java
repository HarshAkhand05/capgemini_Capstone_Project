package com.notes.tests.e2e;

import com.notes.base.BaseTest;
import com.notes.pages.DashboardPage;
import com.notes.pages.LoginPage;
import com.notes.utils.ExcelReader;
import com.notes.utils.ScreenshotUtils;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Epic("Notes App")
@Feature("DEF-002 — UI Sync Defect")
public class ApiToUiValidationTest extends BaseTest {

    private static final String API_BASE =
            "https://practice.expandtesting.com/notes/api";

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/NoteCreationTestData.xlsx";

    // =====================================================
    // DATA PROVIDER
    // =====================================================

    @DataProvider(name = "noteData", parallel = false)
    public Iterator<Object[]> noteData() {
        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    // =====================================================
    // INSTANT DOM CHECK — zero wait, no polling
    // findElements() never throws, returns empty list immediately
    // Used ONLY for before-refresh defect check
    // =====================================================

    private boolean isNotePresentNow(String title) {
        List<WebElement> matches = getDriver().findElements(
                By.xpath("//*[@data-testid='note-card-title' " +
                        "and contains(text(),'" + title + "')]")
        );
        return !matches.isEmpty() && matches.get(0).isDisplayed();
    }

    @Test(
            dataProvider = "noteData",
            groups = {"e2e"}
    )
    @Story("DEF-002 — API Note Not Visible in UI Without Refresh")
    @Severity(SeverityLevel.CRITICAL)
    @Description(
            "Validate note created via API " +
                    "does not appear in UI until refresh — " +
                    "FR-03 requires instant visibility"
    )
    public void testDef002ApiNoteNotVisibleWithoutRefresh(
            Map<String, String> row) {

        // =====================================================
        // READ DATA FROM EXCEL
        // =====================================================

        String tcId         = row.get("TCID");
        String email        = row.get("Email");
        String password     = row.get("Password");
        String category     = row.get("Category");
        String title        = row.get("Title") + "_" + System.currentTimeMillis();
        String description  = row.get("NoteDescription") + "_" + System.currentTimeMillis();
        String expected     = row.get("ExpectedResult");
        String creationDate = row.get("CreationDate");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Title", title);

        if (!"PASS".equalsIgnoreCase(expected)) {
            log.info("[{}] Invalid test data skipped", tcId);
            return;
        }

        // SoftAssert so BOTH before and after refresh results are captured
        SoftAssert softAssert = new SoftAssert();

        // =====================================================
        // STEP 1 — LOGIN via API to get token
        // =====================================================

        Allure.step("Step 1 - Login via API");

        String loginBody = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, password);

        Response loginResp = given()
                .baseUri(API_BASE)
                .contentType("application/json")
                .body(loginBody)
                .when()
                .post("/users/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        String token = loginResp.jsonPath().getString("data.token");
        Assert.assertNotNull(token, "Token should not be null");
        log.info("[{}] API login successful", tcId);

        // =====================================================
        // STEP 2 — LOGIN via UI and open Dashboard
        // =====================================================

        Allure.step("Step 2 - Login via UI");

        getDriver().get(
                "https://practice.expandtesting.com/notes/app/login"
        );

        LoginPage loginPage = new LoginPage();
        loginPage.waitForPageLoad();
        DashboardPage dashboard = loginPage.login(email, password);

        Assert.assertTrue(
                dashboard.isDashboardLoaded(),
                "Dashboard should load after UI login"
        );
        log.info("[{}] UI login successful, dashboard loaded", tcId);

        // =====================================================
        // STEP 3 — CREATE NOTE via API (while UI is open)
        // =====================================================

        Allure.step("Step 3 - Create note via API");

        String noteBody = """
                {
                  "title":"%s",
                  "description":"%s",
                  "category":"%s",
                  "createdAt":"%s"
                }
                """.formatted(title, description, category, creationDate);

        Response createResp = given()
                .baseUri(API_BASE)
                .contentType("application/json")
                .header("x-auth-token", token)
                .body(noteBody)
                .when()
                .post("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        Assert.assertTrue(
                createResp.jsonPath().getBoolean("success"),
                "Note should be created via API"
        );

        Allure.addAttachment(
                "Create Note API Response",
                createResp.asPrettyString()
        );
        log.info("[{}] Note created via API: {}", tcId, title);

        // =====================================================
        // STEP 4 — INSTANT DOM CHECK BEFORE REFRESH
        //
        // isNotePresentNow() calls driver.findElements() once
        // — no polling, no timeout, returns in <50ms
        // — if note absent → false immediately → DEF-002 confirmed
        // — if note present → true → UI auto-synced (defect not reproduced)
        //
        // We do NOT use dashboard.isNotePresent() here because
        // that method polls for up to 30 seconds via WaitUtils
        // which would waste time and mask the defect timing
        // =====================================================

        Allure.step("Step 4 - Instant DOM snapshot: note visibility BEFORE refresh");

        boolean visibleBeforeRefresh = isNotePresentNow(title); // <50ms, no wait

        byte[] screenshotBefore = ScreenshotUtils.takeScreenshotAsBytes(getDriver());
        Allure.addAttachment(
                "Screenshot BEFORE Refresh — Note: " + title,
                new ByteArrayInputStream(screenshotBefore)
        );

        log.info("[{}] Note visible BEFORE refresh (instant check): {}",
                tcId, visibleBeforeRefresh);

        // FR-03 DEFECT ASSERTION
        // WILL FAIL if UI does not auto-sync — that failure IS the defect
        softAssert.assertTrue(
                visibleBeforeRefresh,
                "DEFECT FR-03: Note '" + title + "' should appear in UI " +
                        "WITHOUT refresh after API creation. " +
                        "UI is not auto-syncing with API data."
        );

        // =====================================================
        // STEP 5 — REFRESH AND CHECK AGAIN
        //
        // isNotePresent() with full WaitUtils wait IS correct here
        // because the page is reloading and needs time to render
        // =====================================================

        Allure.step("Step 5 - Refresh page and check note visibility");

        getDriver().navigate().refresh();
        dashboard.isDashboardLoaded();

        boolean visibleAfterRefresh = dashboard.isNotePresent(title); // full wait OK here

        byte[] screenshotAfter = ScreenshotUtils.takeScreenshotAsBytes(getDriver());
        Allure.addAttachment(
                "Screenshot AFTER Refresh — Note: " + title,
                new ByteArrayInputStream(screenshotAfter)
        );

        log.info("[{}] Note visible AFTER refresh: {}", tcId, visibleAfterRefresh);

        softAssert.assertTrue(
                visibleAfterRefresh,
                "Note '" + title + "' must appear in UI AFTER page refresh"
        );

        // =====================================================
        // DEFECT LOGGING
        // =====================================================

        if (!visibleBeforeRefresh && visibleAfterRefresh) {
            log.warn("[{}] ⚠ DEFECT CONFIRMED — DEF-002: " +
                    "Note NOT visible without refresh. " +
                    "FR-03 requires instant visibility. Title: '{}'", tcId, title);
            Allure.step("⚠ DEFECT CONFIRMED: UI not auto-syncing. " +
                    "Note only appeared AFTER manual refresh.");

        } else if (visibleBeforeRefresh && visibleAfterRefresh) {
            log.info("[{}] ✔ DEFECT NOT REPRODUCED — " +
                    "Note visible before AND after refresh. FR-03 satisfied.", tcId);
            Allure.step("✔ Note visible before AND after refresh — FR-03 satisfied.");

        } else if (!visibleBeforeRefresh && !visibleAfterRefresh) {
            log.error("[{}] ✖ NOTE NOT VISIBLE AT ALL — " +
                    "Check API creation or login issue. Title: '{}'", tcId, title);
        }

        // =====================================================
        // FINAL — triggers all soft assertion failures at once
        // =====================================================
        softAssert.assertAll();
    }
}