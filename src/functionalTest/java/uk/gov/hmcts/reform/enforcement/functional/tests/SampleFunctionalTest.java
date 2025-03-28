package uk.gov.hmcts.reform.enforcement.functional.tests;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import net.serenitybdd.annotations.Steps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.enforcement.functional.steps.ApiSteps;

import static io.restassured.RestAssured.given;

@ExtendWith(SerenityJUnit5Extension.class)
class SampleFunctionalTest {

    private static final String BASE_URL = System.getenv("TEST_URL");

    @Autowired
    protected AuthTokenGenerator s2sAuthTokenGenerator;

    @Steps
    ApiSteps apiSteps;

    @BeforeEach
    void setUp() {
        apiSteps.setupBaseUrl(BASE_URL);
    }

    @Test
    void functionalTest() {
        String s2sToken = s2sAuthTokenGenerator.generate();
        Response response = given()
            .contentType(ContentType.JSON)
            .header(ServiceAuthFilter.AUTHORISATION, s2sToken)
            .when()
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.asString().startsWith("Welcome"));
    }

    @Test
    void testHealth() {
        apiSteps.getHealth();
    }

    @Test
    @Tag("Functional")
    void testHealth2() {
        apiSteps.getHealth();
    }
}
