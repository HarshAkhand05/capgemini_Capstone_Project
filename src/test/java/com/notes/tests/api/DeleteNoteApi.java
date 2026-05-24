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

    /**
     * Read login data from Excel
     */
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
    public void testDeleteNoteApi(
            Map<String, String> row) {

        // =========================================
        // TEST DATA
        // =========================================

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

        log.info("[{}] Delete Existing Note API Started", tcId);

        // Skip FAIL rows
        if (!"PASS".equalsIgnoreCase(expected)) {

            log.info("[{}] Skipped because login data is invalid", tcId);

            return;
        }

        // =========================================
        // LOGIN API
        // =========================================

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

        // Extract token
        String token =
                loginResponse.jsonPath().getString("data.token");

        Assert.assertNotNull(
                token,
                "Token should not be null"
        );

        log.info("[{}] Login Successful", tcId);

        // =========================================
        // GET EXISTING NOTES
        // =========================================

        Response getNotesResp = given(authedSpec(token))
                .when()
                .get("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(getNotesResp.asPrettyString());

        validateResponseTime(getNotesResp, 2000);

        // Extract note IDs
        List<String> noteIds =
                getNotesResp.jsonPath().getList("data.id");

        // Verify notes exist
        Assert.assertNotNull(
                noteIds,
                "Notes list should not be null"
        );

        Assert.assertFalse(
                noteIds.isEmpty(),
                "No existing notes available to delete"
        );

        // Take first existing note ID
        String noteId = noteIds.get(0);

        log.info("[{}] Existing Note ID : {}", tcId, noteId);

        // =========================================
        // DELETE EXISTING NOTE
        // =========================================

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

        // =========================================
        // VERIFY NOTE DELETED
        // =========================================

        Response verifyResp = given(authedSpec(token))
                .when()
                .get("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(verifyResp.asPrettyString());

        validateResponseTime(verifyResp, 3000);

        List<String> updatedIds =
                verifyResp.jsonPath().getList("data.id");

        Assert.assertFalse(
                updatedIds.contains(noteId),
                "Deleted note should not exist"
        );

        log.info("[{}] Deleted Note Verification Successful", tcId);
    }
}