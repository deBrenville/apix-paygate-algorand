package org.botstandards.onramp.ledger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Upstream A — the inner "Hello World A" demo leaf: trivial greeting, guarded by the forward secret. */
@QuarkusTest
class BasicLedgerResourceTest {

    private static final String SECRET = "internal-s3cr3t";

    @Test
    void innerServiceReturnsHelloWorldA() {
        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"hello\",\"country\":\"\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(200)
                .body("service", is("A"))
                .body("message", is("Hello World A"));
    }

    @Test
    void emptyBodyIsAccepted() {
        // Content is irrelevant to the demo — an empty JSON body still gets the greeting.
        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(200)
                .body("message", is("Hello World A"));
    }

    @Test
    void missingForwardSecretForbidden() {
        given().contentType("application/json")
                .body("{\"name\":\"hello\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(403);
    }
}
