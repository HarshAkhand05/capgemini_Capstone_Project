package com.notes.tests.api;

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
@Feature("API - GET Notes")
public class NotesApiTests extends BaseApiTest {

    private static final String EXCEL =
            System.getProperty("user.dir")
                    + "/src/test/resources/ApiLoginTestData.xlsx";

    @DataProvider(name = "loginData", parallel = true)
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
    @Story("GET Notes API")
    @Severity(SeverityLevel.CRITICAL)
    public void testGetNotesApi(
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

        log.info("[{}] GET Notes API Started", tcId);

        // Skip negative login rows
        if (!"PASS".equalsIgnoreCase(expected)) {

            log.info("[{}] Skipped because login is invalid", tcId);

            return;
        }

        // Login API
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

        // Extract token
        String token =
                loginResponse.jsonPath().getString("data.token");

        Assert.assertNotNull(
                token,
                "Token should not be null"
        );

        log.info("[{}] Login Successful", tcId);

        // GET /notes API
        Response notesResponse = given(authedSpec(token))
                .when()
                .get("/notes")
                .then()
                .statusCode(200)
                .extract()
                .response();

        attachResponse(notesResponse.asPrettyString());

        // Validate note list exists
        Assert.assertNotNull(
                notesResponse.jsonPath().getList("data"),
                "Notes list should not be null"
        );

        // Validate response time
        validateResponseTime(notesResponse, 2000);

        log.info("[{}] GET /notes validated successfully", tcId);
    }
}