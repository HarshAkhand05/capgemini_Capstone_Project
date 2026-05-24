package com.notes.tests.e2e;

import com.notes.base.BaseTest;
import com.notes.config.ConfigReader;
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
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Epic("Notes App")
@Feature("E2E - API to UI Validation")
public class ApiToUiDeleteValidationTests extends BaseTest {

    private BaseApiTest api;

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/ApiLoginTestData.xlsx";

    @BeforeClass(alwaysRun = true)
    public void setupHybrid() {

        api = new BaseApiTest();

        api.setupApi();
    }

    @DataProvider(name = "loginData", parallel = false)
    public Iterator<Object[]> loginData() {

        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    @Test(
            dataProvider = "loginData",
            groups = {"e2e"}
    )
    @Story("Deleted note must disappear from UI")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Delete note using API and verify note disappears from UI")

    public void testDeletedNoteDisappearsFromUi(
            Map<String, String> row) {

        String tcId =
                row.get("TCID");

        String email =
                row.get("Email");

        String password =
                row.get("Password");

        String expected =
                row.get("ExpectedResult");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);

        if (!"PASS".equalsIgnoreCase(expected)) {

            log.info("[{}] Skipped invalid test data", tcId);

            return;
        }

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
        // GET EXISTING NOTES
        // =====================================

        Response getNotesResp =
                given(api.authedSpec(token))
                        .when()
                        .get("/notes")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        api.attachResponse(getNotesResp.asPrettyString());

        api.validateResponseTime(getNotesResp, 3000);

        List<String> noteIds =
                getNotesResp.jsonPath()
                        .getList("data.id");

        List<String> noteTitles =
                getNotesResp.jsonPath()
                        .getList("data.title");

        Assert.assertFalse(
                noteIds.isEmpty(),
                "No existing notes available"
        );

        // Take first note
        String noteId =
                noteIds.get(0);

        String noteTitle =
                noteTitles.get(0);

        // =====================================
        // DELETE NOTE VIA API
        // =====================================

        Response deleteResp =
                given(api.authedSpec(token))
                        .when()
                        .delete("/notes/" + noteId)
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();

        api.attachResponse(deleteResp.asPrettyString());

        api.validateResponseTime(deleteResp, 3000);

        // =====================================
        // LOGIN UI
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

        // Refresh dashboard
        getDriver().navigate().refresh();

        WaitUtils.waitForPageLoad();

        // =====================================
        // VERIFY NOTE REMOVED FROM UI
        // =====================================

        Assert.assertFalse(
                dashboard.isNotePresent(noteTitle),

                "Deleted note must disappear from UI"
        );

        log.info(
                "[{}] Deleted note successfully removed from UI",
                tcId);
    }
}