package org.botstandards.onramp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Scaffold smoke test: the app boots, health is UP, and the root info endpoint responds. */
@QuarkusTest
class HealthResourceTest {

    @Test
    void healthIsUp() {
        given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void rootInfoResponds() {
        given().when().get("/").then().statusCode(200).body("status", equalTo("ok"));
    }
}
