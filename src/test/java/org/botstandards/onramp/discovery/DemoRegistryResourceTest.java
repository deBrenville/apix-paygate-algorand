package org.botstandards.onramp.discovery;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The self-contained APIX discovery facade serves the captured registry responses by capability. */
@QuarkusTest
class DemoRegistryResourceTest {

    @Test
    void searchByCapabilityReturnsCapturedService() {
        given().when().get("/apix/services?capability=demo.hello&stage=DEVELOPMENT")
                .then().statusCode(200)
                .body("total", is(1))
                .body("_embedded.items[0].endpoint", containsString("/gw/hello"))
                // UTF-8 round-trip guard: the em-dash must survive serialization (regression: was mojibake).
                .body("_embedded.items[0].name", containsString("Hello World B — outer"));
    }

    @Test
    void innerCapabilityResolvesToInnerService() {
        given().when().get("/apix/services?capability=demo.hello.inner&stage=DEVELOPMENT")
                .then().statusCode(200)
                .body("_embedded.items[0].endpoint", containsString("/gw/hello-inner"));
    }

    @Test
    void unknownCapabilityReturnsEmpty() {
        given().when().get("/apix/services?capability=nope.nope")
                .then().statusCode(200).body("total", is(0));
    }

    @Test
    void pathTraversalIsRejected() {
        given().when().get("/apix/services?capability=..%2Fsecret")
                .then().statusCode(200).body("total", is(0));
    }
}
