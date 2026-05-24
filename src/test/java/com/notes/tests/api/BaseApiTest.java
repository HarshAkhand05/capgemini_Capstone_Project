package com.notes.tests.api;

import com.notes.config.ConfigReader;
import io.qameta.allure.Attachment;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;

public class BaseApiTest {

    protected static final Logger log =
            LoggerFactory.getLogger(BaseApiTest.class);

    public static RequestSpecification requestSpec;


    @BeforeClass(alwaysRun = true)
    public void setupApi() {

        String baseUrl =
                ConfigReader.getInstance().get("api.base.url");

        RestAssured.baseURI = baseUrl;

        requestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setContentType(ContentType.JSON)
                .addHeader("Accept", "application/json")
                .addFilter(new AllureRestAssured())
                .log(LogDetail.ALL)
                .build();

        log.info("API Base URL : {}", baseUrl);
    }


    public RequestSpecification authedSpec(String token) {

        return new RequestSpecBuilder()
                .addRequestSpecification(requestSpec)
                .addHeader("x-auth-token", token)
                .build();
    }


    public void validateResponseTime(
            Response response,
            long maxTime) {

        long actualTime = response.time();

        log.info("Response Time : {} ms", actualTime);

        attachResponseTime(actualTime);

        Assert.assertTrue(
                actualTime < maxTime,
                "API response exceeded limit. Actual : "
                        + actualTime + " ms"
        );
    }


    @Attachment(
            value = "API Response",
            type = "application/json"
    )
    public String attachResponse(String response) {
        return response;
    }


    @Attachment(
            value = "API Request",
            type = "application/json"
    )
    public String attachRequest(String request) {
        return request;
    }


    @Attachment(value = "Response Time")
    public String attachResponseTime(long time) {
        return "Response Time = " + time + " ms";
    }
}