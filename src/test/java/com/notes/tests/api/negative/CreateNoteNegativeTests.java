package com.notes.tests.api.negative;

import com.notes.tests.api.BaseApiTest;
import com.notes.utils.ExcelReader;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Epic("Notes App")
@Feature("Create Note Negative Tests")
public class CreateNoteNegativeTests extends BaseApiTest {

    // =========================================================
    // VALID LOGIN DATA — APILoginTestData.xlsx
    // Used to get a real token before testing create note
    // =========================================================

    private static final String LOGIN_EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/APILoginTestData.xlsx";

    // =========================================================
    // NEGATIVE TEST DATA — NegativeTestData.xlsx
    // Used for invalid note bodies
    // =========================================================

    private static final String NEGATIVE_EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/NegativeTestData.xlsx";

    // =========================================================
    // LOGIN DATA PROVIDER — reads from APILoginTestData.xlsx
    // =========================================================

    @DataProvider(name = "loginData")
    public Iterator<Object[]> loginData() {
        return ExcelReader
                .readSheet(LOGIN_EXCEL, "Sheet1")
                .stream()
                .filter(row -> "PASS".equalsIgnoreCase(row.get("ExpectedResult")))
                .map(row -> new Object[]{row})
                .iterator();
    }

    // =========================================================
    // NEGATIVE DATA PROVIDER — reads from NegativeTestData.xlsx
    // =========================================================

    @DataProvider(name = "negativeData")
    public Iterator<Object[]> negativeData() {
        return ExcelReader
                .readSheet(NEGATIVE_EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    // =========================================================
    // TC-NEG-CREATE-01 | FR-09
    // CREATE NOTE WITH EMPTY BODY
    // Login  → APILoginTestData.xlsx (valid credentials)
    // Input  → empty body {}
    // Expect → 400, success=false, no note created
    // =========================================================

    @Test(dataProvider = "loginData", groups = {"api", "negative"})
    @Story("Create Note Empty Body")
    @Severity(SeverityLevel.CRITICAL)
    @Description("FR-09: POST /notes with empty body must return 400 — nothing should be created")
    public void testCreateNoteEmptyBody(Map<String, String> loginRow) {

        String tcId     = loginRow.get("TCID");
        String email    = loginRow.get("Email");
        String password = loginRow.get("Password");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Scenario", "Create Note Empty Body");

        log.info("[{}] TC-NEG-CREATE-01: Create note with empty body", tcId);

        // ── Step 1: Login using APILoginTestData.xlsx ──
        String loginBody = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, password);

        attachRequest(loginBody);

        Response loginResp = given(requestSpec)
                .body(loginBody)
                .when()
                .post("/users/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(loginResp.asPrettyString());
        validateResponseTime(loginResp, 2000);

        String token = loginResp.jsonPath().getString("data.token");
        Assert.assertNotNull(token, "Token must not be null");

        log.info("[{}] Login successful", tcId);

        // ── Step 2: POST /notes with empty body ──
        String emptyBody = "{}";
        attachRequest(emptyBody);

        Response resp = given(authedSpec(token))
                .body(emptyBody)
                .when()
                .post("/notes")
                .then()
                .statusCode(400)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false — note must NOT be created"
        );

        String message = resp.jsonPath().getString("message");
        Assert.assertNotNull(message, "Validation error message must be present");
        Assert.assertFalse(message.isEmpty(), "Validation message must not be empty");

        log.info("[{}] PASS — Empty body returned 400: {} — no note created", tcId, message);
    }

    // =========================================================
    // TC-NEG-CREATE-02 | FR-09
    // CREATE NOTE WITH MISSING TITLE
    // Login  → APILoginTestData.xlsx (valid credentials)
    // Input  → body without title field
    // Expect → 400, success=false, no note created
    // =========================================================

    @Test(dataProvider = "loginData", groups = {"api", "negative"})
    @Story("Create Note Missing Title")
    @Severity(SeverityLevel.CRITICAL)
    @Description("FR-09: POST /notes without title must return 400 — title is required")
    public void testCreateNoteMissingTitle(Map<String, String> loginRow) {

        String tcId     = loginRow.get("TCID");
        String email    = loginRow.get("Email");
        String password = loginRow.get("Password");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Scenario", "Create Note Missing Title");

        log.info("[{}] TC-NEG-CREATE-02: Create note without title", tcId);

        // ── Step 1: Login using APILoginTestData.xlsx ──
        String loginBody = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, password);

        attachRequest(loginBody);

        Response loginResp = given(requestSpec)
                .body(loginBody)
                .when()
                .post("/users/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(loginResp.asPrettyString());
        validateResponseTime(loginResp, 2000);

        String token = loginResp.jsonPath().getString("data.token");
        Assert.assertNotNull(token, "Token must not be null");

        log.info("[{}] Login successful", tcId);

        // ── Step 2: POST /notes without title field ──
        String bodyNoTitle = """
                {
                  "category": "Home",
                  "description": "Missing title negative test"
                }
                """;

        attachRequest(bodyNoTitle);

        Response resp = given(authedSpec(token))
                .body(bodyNoTitle)
                .when()
                .post("/notes")
                .then()
                .statusCode(400)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false — note must NOT be created without title"
        );

        String message = resp.jsonPath().getString("message");
        Assert.assertNotNull(message, "Validation error message must be present");

        log.info("[{}] PASS — Missing title returned 400: {} — no note created", tcId, message);
    }

    // =========================================================
    // TC-NEG-CREATE-03 | FR-09
    // CREATE NOTE WITHOUT TOKEN
    // No login — testing unauthorized note creation
    // Expect → 401 Unauthorized
    // =========================================================

    @Test(groups = {"api", "negative"})
    @Story("Create Note Without Token")
    @Severity(SeverityLevel.CRITICAL)
    @Description("FR-09: POST /notes without token must return 401 — unauthorized access")
    public void testCreateNoteWithoutToken() {

        Allure.parameter("Scenario", "Create Note Without Token");

        log.info("TC-NEG-CREATE-03: Create note without token");

        String body = """
                {
                  "category": "Home",
                  "title": "Unauthorized Note",
                  "description": "This should not be created"
                }
                """;

        attachRequest(body);

        // No token — use plain requestSpec
        Response resp = given(requestSpec)
                .body(body)
                .when()
                .post("/notes")
                .then()
                .statusCode(401)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false — note must NOT be created without token"
        );

        log.info("PASS — Create note without token returned 401 — no note created");
    }
}