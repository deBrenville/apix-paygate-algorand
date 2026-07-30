package org.botstandards.onramp.bsm;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The BSM discovery layer: per-route manifest + an index; unknown route → 404. */
@QuarkusTest
class BsmResourceTest {

    @Test
    void perRouteManifestAdvertisesDiscoveryAndPayment() {
        given().when().get("/.well-known/bsm/echo")
                .then().statusCode(200)
                .body("capability", is("demo.echo"))
                .body("endpoint", endsWith("/gw/echo"))
                .body("payment.protocol", is("x402"))
                .body("price.network", is("algorand-testnet"))
                .body("price.asset", is("768322928"));
    }

    @Test
    void indexListsRoutes() {
        given().when().get("/.well-known/bsm")
                .then().statusCode(200)
                .body("services.route", hasItem("echo"));
    }

    @Test
    void unknownRouteReturns404() {
        given().when().get("/.well-known/bsm/nope").then().statusCode(404);
    }
}
