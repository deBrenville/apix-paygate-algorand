package org.botstandards.onramp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import org.botstandards.apix.verification.sanctions.SanctionsMatcher;
import org.junit.jupiter.api.Test;

/**
 * Scaffold smoke test: the app boots, health is UP, the root info endpoint responds, and the
 * REAL {@link SanctionsMatcher} library (from apix-verification) is on the classpath and links.
 */
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

    @Test
    void realSanctionsMatcherLibraryLinks() {
        // Proves the apix-verification dependency resolves AND its behavior is available:
        // "Ácmé S.A." -> lowercase, diacritics stripped, "." removed, trailing legal-form "sa" stripped.
        assertEquals("acme", SanctionsMatcher.normalize("Ácmé S.A."));
    }
}
