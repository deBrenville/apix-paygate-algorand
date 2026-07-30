package org.botstandards.onramp.ledger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Upstream A: neutral per-register aggregation, match-proof, forward-secret guard. No exemption here. */
@QuarkusTest
class BasicLedgerResourceTest {

    private static final String SECRET = "internal-s3cr3t";

    @Test
    void multiRegisterActorReturnsAllMatches() {
        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"Viktor Malenkov\",\"country\":\"RU\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(200)
                .body("outcome", is("MATCH"))
                .body("matches.register", hasItem("UN"))
                .body("matches.register", hasItem("OFAC"));
    }

    @Test
    void ofacOnlyActorIsNeutralMatchHereNoExemption() {
        // The ISGH-like case: listed only by OFAC. The neutral ledger reports it as a plain MATCH.
        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"Amara Okonkwo\",\"country\":\"NG\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(200)
                .body("outcome", is("MATCH"))
                .body("matches.size()", is(1))
                .body("matches[0].register", is("OFAC"))
                .body("matches[0].sourceRecord.primaryName", is("Amara Okonkwo"));
    }

    @Test
    void unlistedNameIsClear() {
        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"John Smith\",\"country\":\"US\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(200)
                .body("outcome", is("CLEAR"))
                .body("matches.size()", is(0));
    }

    @Test
    void missingForwardSecretForbidden() {
        given().contentType("application/json")
                .body("{\"name\":\"Viktor Malenkov\",\"country\":\"RU\"}")
                .when().post("/internal/ledger/screen")
                .then().statusCode(403);
    }
}
