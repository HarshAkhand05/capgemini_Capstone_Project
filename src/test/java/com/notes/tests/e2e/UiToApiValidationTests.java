package com.notes.tests.e2e;

import com.notes.base.BaseTest;
import com.notes.config.ConfigReader;
import com.notes.pages.AddNotePage;
import com.notes.pages.DashboardPage;
import com.notes.pages.LoginPage;
import com.notes.tests.api.BaseApiTest;
import com.notes.utils.ExcelReader;
import com.notes.utils.WaitUtils;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Epic("Notes App")
@Feature("E2E - UI to API Validation")
public class UiToApiValidationTests extends BaseTest {

    private BaseApiTest api;

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/NoteCreationTestData.xlsx";

    @BeforeClass(alwaysRun = true)
    public void setupHybrid() {

        api = new BaseApiTest();

        api.setupApi();
    }

    @DataProvider(name = "noteData", parallel = false)
    public Iterator<Object[]> noteData() {

        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    @Test(
            dataProvider = "noteData",
            groups = {"e2e"}
    )
    @Story("UI-created note must appear in API")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Create note from UI and validate same note exists in GET /notes API")

    public void testUiCreatedNoteAppearsInApi(
            Map<String, String> row) {

        String tcId =
                row.get("TCID");

        String email =
                row.get("Email");

        String password =
                row.get("Password");

        String category =
                row.get("Category");

        String title =
                row.get("Title")
                        + "_"
                        + System.currentTimeMillis();

        String description =
                row.get("NoteDescription")
                        + "_"
                        + System.currentTimeMillis();

        String expected =
                row.get("ExpectedResult");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Title", title);

        if (!"PASS".equalsIgnoreCase(expected)) {

            log.info("[{}] Skipped invalid test data", tcId);

            return;
        }

        // =====================================
        // UI LOGIN
        // =====================================

        getDriver().get(
                ConfigReader.getInstance()
                        .get("base.url") + "login");

        LoginPage loginPage = new LoginPage();

        loginPage.waitForPageLoad();

        DashboardPage dashboard =
                loginPage.login(email, password);

        Assert.assertTrue(
                dashboard.isDashboardLoaded(),
                "Dashboard should load after login"
        );

        // =====================================
        // CREATE NOTE VIA UI
        // =====================================

        AddNotePage addNotePage =
                dashboard.clickAddNote();

        addNotePage.waitForPageLoad();

        DashboardPage updatedDashboard =
                addNotePage.createNote(
                        category,
                        title,
                        description
                );

        WaitUtils.waitForNotePresent(title);

        Assert.assertTrue(
                updatedDashboard.isNotePresent(title),
                "Created note should appear in UI"
        );

        // =====================================
        // API LOGIN
        // =====================================

        String loginBody = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, password);

        api.attachRequest(loginBody);

        Response loginResponse =
                given(BaseApiTest.requestSpec)
                        .body(loginBody)
                        .when()
                        .post("/users/login")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        api.attachResponse(loginResponse.asPrettyString());

        api.validateResponseTime(loginResponse, 2000);

        String token =
                loginResponse.jsonPath()
                        .getString("data.token");

        Assert.assertNotNull(
                token,
                "Token should not be null"
        );

        // =====================================
        // GET NOTES API
        // =====================================

        Response notesResponse =
                given(api.authedSpec(token))
                        .when()
                        .get("/notes")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        api.attachResponse(notesResponse.asPrettyString());

        api.validateResponseTime(notesResponse, 3000);

        // =====================================
        // VALIDATE UI NOTE EXISTS IN API
        // =====================================

        Assert.assertTrue(
                notesResponse.asPrettyString()
                        .contains(title),

                "UI-created note must appear in API"
        );

        Assert.assertTrue(
                notesResponse.asPrettyString()
                        .contains(description),

                "UI-created description must appear in API"
        );

        log.info(
                "[{}] UI-created note validated successfully in API",
                tcId);
    }
}