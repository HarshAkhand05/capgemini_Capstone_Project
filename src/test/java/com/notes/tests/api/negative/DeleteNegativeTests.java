package com.notes.tests.api.negative;

import com.notes.tests.api.BaseApiTest;
import com.notes.utils.ExcelReader;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Epic("Notes App")
@Feature("Delete Negative Tests")
public class DeleteNegativeTests extends BaseApiTest {

    // =========================================================
    // VALID LOGIN TEST DATA — APILoginTestData.xlsx
    // Used for tests that need a real token first
    // =========================================================

    private static final String LOGIN_EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/APILoginTestData.xlsx";

    // =========================================================
    // NEGATIVE TEST DATA — NegativeTestData.xlsx
    // Used for fake note IDs and invalid credentials
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
    // TC-NEG-DEL-01 | FR-09
    // DELETE NOTE WITHOUT TOKEN
    // Uses NegativeTestData.xlsx for DeleteNoteId
    // No login required — testing unauthorized access
    // =========================================================

    @Test(
            dataProvider = "negativeData",
            groups = {"api", "negative"}
    )
    @Story("Delete Without Token")
    @Severity(SeverityLevel.CRITICAL)
    @Description("FR-09: DELETE /notes without token must return 401 Unauthorized")
    public void testDeleteNoteWithoutToken(Map<String, String> row) {

        String tcId        = row.get("TCID");
        String deleteNoteId = row.get("DeleteNoteId");

        Allure.parameter("TCID", tcId);
        Allure.parameter("DeleteNoteId", deleteNoteId);

        log.info("[{}] TC-NEG-DEL-01: DELETE note without token, noteId={}", tcId, deleteNoteId);

        // No token — use plain requestSpec
        Response resp = given(requestSpec)
                .when()
                .delete("/notes/" + deleteNoteId)
                .then()
                .statusCode(401)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false when deleting without token"
        );

        log.info("[{}] PASS — Delete without token returned 401", tcId);
    }

    // =========================================================
    // TC-NEG-DEL-02 | FR-09
    // DELETE NON-EXISTENT NOTE
    // Uses APILoginTestData.xlsx for valid login
    // Uses NegativeTestData.xlsx for fake note IDs
    // =========================================================

    @Test(
            dataProvider = "loginData",
            groups = {"api", "negative"}
    )
    @Story("Delete Non Existent Note")
    @Severity(SeverityLevel.NORMAL)
    @Description("FR-09: DELETE /notes with non-existent note ID must return 400 or 404")
    public void testDeleteNonExistentNote(Map<String, String> loginRow) {

        String tcId     = loginRow.get("TCID");
        String email    = loginRow.get("Email");
        String password = loginRow.get("Password");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);

        log.info("[{}] TC-NEG-DEL-02: DELETE non-existent note", tcId);

        // =====================================================
        // STEP 1 — LOGIN using APILoginTestData.xlsx
        // =====================================================

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

        Assert.assertNotNull(
                token,
                "Login token must not be null"
        );

        log.info("[{}] Login successful", tcId);

        // =====================================================
        // STEP 2 — READ FAKE NOTE IDs from NegativeTestData.xlsx
        // =====================================================

        List<Map<String, String>> negativeRows =
                ExcelReader.readSheet(NEGATIVE_EXCEL, "Sheet1");

        for (Map<String, String> negativeRow : negativeRows) {

            String fakeNoteId = negativeRow.get("FakeNoteId");

            if (fakeNoteId == null || fakeNoteId.isBlank()) {
                log.warn("[{}] FakeNoteId is empty in NegativeTestData — skipping row", tcId);
                continue;
            }

            log.info("[{}] Attempting DELETE with fakeNoteId={}", tcId, fakeNoteId);

            // =================================================
            // STEP 3 — DELETE with fake note ID
            // =================================================

            Response resp = given(authedSpec(token))
                    .when()
                    .delete("/notes/" + fakeNoteId)
                    .then()
                    .extract()
                    .response();

            attachResponse(resp.asPrettyString());
            validateResponseTime(resp, 2000);

            int status = resp.statusCode();

            Assert.assertTrue(
                    status == 400 || status == 404,
                    "Expected 400 or 404 for fakeNoteId="
                            + fakeNoteId + " but got: " + status
            );

            Assert.assertEquals(
                    resp.jsonPath().getBoolean("success"),
                    false,
                    "success must be false for non-existent note"
            );

            log.info("[{}] PASS — fakeNoteId={} returned {} as expected",
                    tcId, fakeNoteId, status);
        }
    }

    // =========================================================
    // TC-NEG-DEL-03 | FR-09
    // DELETE NOTE WITH INVALID TOKEN
    // Uses NegativeTestData.xlsx for DeleteNoteId
    // No valid login — testing with a fake token
    // =========================================================

    @Test(
            dataProvider = "negativeData",
            groups = {"api", "negative"}
    )
    @Story("Delete With Invalid Token")
    @Severity(SeverityLevel.CRITICAL)
    @Description("FR-09: DELETE /notes with invalid token must return 401 Unauthorized")
    public void testDeleteNoteInvalidToken(Map<String, String> row) {

        String tcId        = row.get("TCID");
        String deleteNoteId = row.get("DeleteNoteId");

        Allure.parameter("TCID", tcId);
        Allure.parameter("DeleteNoteId", deleteNoteId);

        log.info("[{}] TC-NEG-DEL-03: DELETE note with invalid token, noteId={}", tcId, deleteNoteId);

        // Use a fake/invalid token — authedSpec with garbage value
        Response resp = given(authedSpec("invalid_token_xyz_999"))
                .when()
                .delete("/notes/" + deleteNoteId)
                .then()
                .statusCode(401)
                .extract()
                .response();

        attachResponse(resp.asPrettyString());
        validateResponseTime(resp, 2000);

        Assert.assertEquals(
                resp.jsonPath().getBoolean("success"),
                false,
                "success must be false when using invalid token"
        );

        log.info("[{}] PASS — Delete with invalid token returned 401", tcId);
    }
}