package org.botstandards.onramp.humanity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Upstream B — the outer "Hello World B" demo service: chains to A (mocked here) and nests its result. */
@QuarkusTest
class HumanityResourceTest {

    private static final String SECRET = "internal-s3cr3t";

    @InjectMock
    UpstreamPayingClient payingClient;

    @Test
    void outerServiceReturnsHelloWorldBWithNestedInnerResult() {
        // The cascade hop (B paying A) is mocked; this test asserts B's own shape + that it nests A.
        when(payingClient.callInner())
                .thenReturn(Map.of("service", "A", "message", "Hello World A"));

        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"hello\",\"country\":\"\"}")
                .when().post("/internal/humanity/screen")
                .then().statusCode(200)
                .body("service", is("B"))
                .body("message", is("Hello World B"))
                .body("innerResult", notNullValue())
                .body("innerResult.message", is("Hello World A"));
    }

    @Test
    void missingForwardSecretForbidden() {
        given().contentType("application/json")
                .body("{\"name\":\"hello\"}")
                .when().post("/internal/humanity/screen")
                .then().statusCode(403);
    }
}
