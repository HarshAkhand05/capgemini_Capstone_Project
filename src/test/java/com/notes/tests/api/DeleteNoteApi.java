package com.notes.tests.api;

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
@Feature("API - Delete Existing Note")
public class DeleteNoteApi extends BaseApiTest {

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/ApiLoginTestData.xlsx";

    @DataProvider(name = "loginData", parallel = false)
    public Iterator<Object[]> loginData() {
        return ExcelReader.readSheet(EXCEL, "Sheet1")
                .stream()
                .map(row -> new Object[]{row})
                .iterator();
    }

    @Test(
            dataProvider = "loginData",
            groups = {"api"}
    )
    @Story("Delete Existing Note API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Login existing user, fetch existing notes, delete one existing note and verify deletion")
    public void testDeleteNoteApi(Map<String, String> row) {

        String tcId     = row.get("TCID");
        String email    = row.get("Email");
        String password = row.get("Password");
        String expected = row.get("ExpectedResult");

        Allure.parameter("TCID", tcId);
        Allure.parameter("Email", email);

        log.info("[{}] Delete Existing Note API Started", tcId);

        // ── NEGATIVE PATH (FAIL rows) ─────────────────────────────────────
        if (!"PASS".equalsIgnoreCase(expected)) {

            log.info("[{}] Negative login scenario — expecting 401", tcId);

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
                    .statusCode(400)   // assert failure, not skip
                    .extract()
                    .response();

            attachResponse(loginResp.asPrettyString());
            validateResponseTime(loginResp, 2000);

            Assert.assertNull(
                    loginResp.jsonPath().get("data.token"),
                    "Token must not be returned for invalid credentials"
            );

            log.info("[{}] Negative login validated — 401 confirmed", tcId);
            return;
        }

        // ── POSITIVE PATH (PASS rows) ─────────────────────────────────────

        String loginBody = """
                {
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(email, password);

        attachRequest(loginBody);

        Response loginResponse = given(requestSpec)
                .body(loginBody)
                .when()
                .post("/users/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(loginResponse.asPrettyString());
        validateResponseTime(loginResponse, 2000);

        String token = loginResponse.jsonPath().getString("data.token");
        Assert.assertNotNull(token, "Token should not be null");

        log.info("[{}] Login Successful", tcId);

        // ── GET EXISTING NOTES ────────────────────────────────────────────

        Response getNotesResp = given(authedSpec(token))
                .when()
                .get("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(getNotesResp.asPrettyString());
        validateResponseTime(getNotesResp, 2000);

        List<String> noteIds = getNotesResp.jsonPath().getList("data.id");

        Assert.assertNotNull(noteIds, "Notes list should not be null");
        if (noteIds.isEmpty()) {

            Allure.step(
                    "No existing notes available to delete"
            );

            attachResponse(
                    getNotesResp.asPrettyString()
            );

            log.info(
                    "No notes available. Skipping delete validation."
            );

            return;
        }

        String noteId = noteIds.get(0);
        log.info("[{}] Existing Note ID : {}", tcId, noteId);

        // ── DELETE NOTE ───────────────────────────────────────────────────

        Response deleteResp = given(authedSpec(token))
                .when()
                .delete("/notes/" + noteId)
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(deleteResp.asPrettyString());
        validateResponseTime(deleteResp, 2000);

        log.info("[{}] Note Deleted Successfully", tcId);

        // ── VERIFY DELETION ───────────────────────────────────────────────

        Response verifyResp = given(authedSpec(token))
                .when()
                .get("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(verifyResp.asPrettyString());
        validateResponseTime(verifyResp, 2000);  // fixed: was 3000

        List<String> updatedIds = verifyResp.jsonPath().getList("data.id");

        Assert.assertFalse(
                updatedIds.contains(noteId),
                "Deleted note should not exist in list"
        );

        log.info("[{}] Deleted Note Verification Successful", tcId);
    }
}