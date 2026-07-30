package org.botstandards.onramp.humanity;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import java.util.List;
import java.util.Map;
import org.botstandards.onramp.ledger.MatchProof;
import org.botstandards.onramp.ledger.MatchProof.ProofMatch;
import org.junit.jupiter.api.Test;

/** Upstream B: applies the humanity filter to A's (mocked) ledger result. No network here. */
@QuarkusTest
class HumanityResourceTest {

    private static final String SECRET = "internal-s3cr3t";

    @InjectMock
    UpstreamPayingClient payingClient;

    private static MatchProof ledger(String name, ProofMatch... matches) {
        return new MatchProof("MATCH", Map.of("name", name, "country", "XX"), List.of(matches), null, "t", "v");
    }

    private static ProofMatch ofac(String name) {
        return new ProofMatch("OFAC", "ofac-x", "STRONG", 1.0, Map.of("primaryName", name));
    }

    @Test
    void isghCaseBecomesExempt() {
        when(payingClient.screen("Amara Okonkwo", "NG"))
                .thenReturn(ledger("Amara Okonkwo", ofac("Amara Okonkwo")));

        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"Amara Okonkwo\",\"country\":\"NG\"}")
                .when().post("/internal/humanity/screen")
                .then().statusCode(200)
                .body("outcome", is("MATCH_EXEMPT"))
                .body("exemption.reason", notNullValue())
                .body("matches[0].register", is("OFAC"));
    }

    @Test
    void ofacOnlyNonExemptStaysMatch() {
        when(payingClient.screen("Dmitri Volkov", "RU"))
                .thenReturn(ledger("Dmitri Volkov", ofac("Dmitri Volkov")));

        given().header("X-Onramp-Forward", SECRET).contentType("application/json")
                .body("{\"name\":\"Dmitri Volkov\",\"country\":\"RU\"}")
                .when().post("/internal/humanity/screen")
                .then().statusCode(200).body("outcome", is("MATCH"));
    }

    @Test
    void missingForwardSecretForbidden() {
        given().contentType("application/json")
                .body("{\"name\":\"Amara Okonkwo\",\"country\":\"NG\"}")
                .when().post("/internal/humanity/screen")
                .then().statusCode(403);
    }
}
