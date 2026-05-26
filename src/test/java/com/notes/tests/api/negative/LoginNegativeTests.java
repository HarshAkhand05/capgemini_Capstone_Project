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
@Feature("Login Negative Tests")
public class LoginNegativeTests extends BaseApiTest {

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/NegativeTestData.xlsx";

    @DataProvider(name = "negativeData")
    public Iterator<Object[]> negativeData() {
        return ExcelReader
                .readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    // =========================================================
    // TC-NEG-LOGIN-01 | FR-09
    // LOGIN WITH EMPTY BODY
    // =========================================================

    @Test(groups = {"api", "negative"})
    @Story("Login With Empty Body")
    @Severity(SeverityLevel.NORMAL)
    @Description("FR-09: POST /users/login with empty body must return 400")
    public void testLoginEmptyBody() {

        Allure.parameter("Scenario", "Empty Login Body");

        log.info("TC-NEG-LOGIN-01: Login with empty body");

        String body = "{}";
        attachRequest(body);

        Response resp = given(requestSpec)
                .body(body)
                .when()
                .post("/users/login")
                .then()
                .statusCode(400)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false for empty body"
        );

        String message = resp.jsonPath().getString("message");
        Assert.assertNotNull(message, "Error message must be present");
        Assert.assertFalse(message.isEmpty(), "Error message must not be empty");

        log.info("PASS — Empty body login returned 400: {}", message);

        Allure.step("✅ TC-NEG-LOGIN-01 PASSED — Empty body correctly rejected with 400");
    }

    // =========================================================
    // TC-NEG-LOGIN-02 | FR-09
    // LOGIN WITH WRONG PASSWORD
    //
    // ⚠ DEF-002 — KNOWN DEFECT
    // Expected : 401 Unauthorized (industry standard for wrong credentials)
    // Actual   : 400 Bad Request  (Notes API non-standard behaviour)
    //
    // Industry Standard:
    //   401 Unauthorized → Authentication failed (wrong credentials)
    //   400 Bad Request  → Malformed request (missing/invalid fields)
    //
    // This test is intentionally set to assert 401 to EXPOSE the defect.
    // The test will FAIL and the Allure report will show DEF-002 evidence.
    // =========================================================

    @Test(dataProvider = "negativeData", groups = {"api", "negative"})
    @Story("Login With Wrong Password — DEF-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description(
            "DEF-002 | FR-09: POST /users/login with wrong password SHOULD return 401 Unauthorized. " +
                    "Industry standard: 401 = authentication failure. " +
                    "Actual behaviour: Notes API returns 400 Bad Request — this is a defect."
    )
    public void testLoginWrongPassword(Map<String, String> row) {

        String tcId           = row.get("TCID");
        String email          = row.get("Email");
        String invalidPassword = row.get("InvalidPassword");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);
        Allure.parameter("Invalid Password", invalidPassword);
        Allure.parameter("Scenario", "Wrong Password — DEF-002");
        Allure.parameter("Defect ID", "DEF-002");
        Allure.parameter("Expected Status", "401 Unauthorized");
        Allure.parameter("Actual Status", "400 Bad Request");

        log.info("[{}] TC-NEG-LOGIN-02: Login with wrong password — DEF-002 validation", tcId);
        log.info("[{}] Sending POST /users/login with email={} and WRONG password", tcId, email);

        String body = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, invalidPassword);

        attachRequest(body);

        // ── Step 1: Call the API ──
        Response resp = given(requestSpec)
                .body(body)
                .when()
                .post("/users/login")
                .then()
                .extract()       // ← extract without asserting status — we handle it below
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        int actualStatus = resp.statusCode();

        log.info("[{}] API returned status code: {}", tcId, actualStatus);

        // ── Step 2: Log defect evidence to Allure ──
        Allure.addAttachment(
                "🐛 DEF-002 — Defect Evidence",
                "text/plain",
                "Defect ID      : DEF-002\n" +
                        "Test Case      : TC-NEG-LOGIN-02\n" +
                        "Requirement    : FR-09 — Negative scenarios\n" +
                        "Endpoint       : POST /users/login\n" +
                        "Input          : Valid email + Wrong password\n\n" +
                        "Expected Status: 401 Unauthorized\n" +
                        "  Reason       : Industry standard HTTP — 401 means\n" +
                        "                 authentication failed due to wrong credentials\n\n" +
                        "Actual Status  : " + actualStatus + "\n" +
                        "  Reason       : Notes API returns 400 Bad Request\n" +
                        "                 which incorrectly implies malformed request\n\n" +
                        "Impact         : Misleading error code for API consumers\n" +
                        "                 Security tools may not flag this as auth failure\n\n" +
                        "Severity       : Medium\n" +
                        "Priority       : P2\n" +
                        "Status         : Open\n"
        );

        // ── Step 3: Assert 401 — this WILL FAIL exposing the defect ──
        log.warn("[{}] DEF-002: Asserting 401 — API actually returns {} — DEFECT EXPOSED",
                tcId, actualStatus);

        // Log what the API actually returned
        Allure.step("API returned status: " + actualStatus +
                " | Expected: 401 | DEF-002 — Wrong status code for invalid credentials");

        // This assertion FAILS intentionally to prove the defect
        Assert.assertEquals(
                actualStatus,
                401,
                "\n========================================\n" +
                        "DEF-002 — DEFECT IDENTIFIED\n" +
                        "========================================\n" +
                        "Endpoint  : POST /users/login\n" +
                        "Input     : Valid email + Wrong password\n" +
                        "Expected  : 401 Unauthorized\n" +
                        "Actual    : " + actualStatus + " Bad Request\n" +
                        "Root Cause: Notes API uses 400 for wrong credentials\n" +
                        "            Industry standard requires 401\n" +
                        "Severity  : Medium | Priority: P2\n" +
                        "========================================"
        );

        // ── Step 4: Verify token is NOT generated (runs only if above passes) ──
        Object token = resp.jsonPath().get("data.token");
        Assert.assertNull(
                token,
                "Token must NOT be generated for wrong password"
        );

        log.info("[{}] PASS — Wrong password returned expected status, no token generated", tcId);
    }
}