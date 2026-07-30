package org.botstandards.onramp.x402;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The x402 filter: 402 without payment; verify+settle then proxy on valid payment; 402 on invalid. */
@QuarkusTest
class X402GatewayFilterTest {

    static WireMockServer wm;

    private static final String VALID_PAYMENT =
            "{\"x402Version\":2,\"scheme\":\"exact\",\"payload\":{\"paymentGroup\":[\"VALIDTX\"],\"paymentIndex\":0}}";
    private static final String INVALID_PAYMENT =
            "{\"x402Version\":2,\"scheme\":\"exact\",\"payload\":{\"paymentGroup\":[\"INVALID\"],\"paymentIndex\":0}}";

    @BeforeAll
    static void startStub() {
        wm = new WireMockServer(8089);
        wm.start();
        // Facilitator: invalid when the payment carries the INVALID marker, valid otherwise.
        wm.stubFor(post(urlEqualTo("/verify")).atPriority(1).withRequestBody(containing("INVALID"))
                .willReturn(json("{\"isValid\":false,\"invalidReason\":\"bad payment\"}")));
        wm.stubFor(post(urlEqualTo("/verify")).atPriority(5)
                .willReturn(json("{\"isValid\":true,\"invalidReason\":null}")));
        wm.stubFor(post(urlEqualTo("/settle"))
                .willReturn(json("{\"success\":true,\"transaction\":\"TXSETTLED1\"}")));
        // Origin behind the gateway.
        wm.stubFor(post(urlEqualTo("/echo")).withHeader("X-Onramp-Forward", equalTo("s3cr3t"))
                .willReturn(json("{\"ok\":true}")));
    }

    @AfterAll
    static void stopStub() {
        wm.stop();
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @Test
    void noPaymentYields402WithRequirements() {
        given().contentType("application/json").body("{\"name\":\"x\"}")
                .when().post("/gw/paid-echo")
                .then().statusCode(402)
                .body("accepts[0].payTo", is("Q2XTPIACCO27OZ7ROPPT5SU5HLAZCELVGEGGSGRIMQBP5TBXHOHZWN6GOY"))
                .body("accepts[0].amount", is("10000"));
    }

    @Test
    void validPaymentVerifiesSettlesAndProxies() {
        given().contentType("application/json").header("X-PAYMENT", VALID_PAYMENT).body("{\"name\":\"x\"}")
                .when().post("/gw/paid-echo")
                .then().statusCode(200).body("ok", is(true));

        wm.verify(postRequestedFor(urlEqualTo("/verify")));
        wm.verify(postRequestedFor(urlEqualTo("/settle")));
        wm.verify(postRequestedFor(urlEqualTo("/echo")));
    }

    @Test
    void invalidPaymentYields402() {
        given().contentType("application/json").header("X-PAYMENT", INVALID_PAYMENT).body("{\"name\":\"x\"}")
                .when().post("/gw/paid-echo")
                .then().statusCode(402).body("error", containsString("invalid"));
    }

    @Test
    void attestationRequiredRouteRejectsWithoutFlag() {
        given().contentType("application/json").body("{\"name\":\"x\"}")
                .when().post("/gw/attested-echo")
                .then().statusCode(422).body("error", containsString("attestation"));
    }

    @Test
    void attestationRequiredRoutePassesWithFlag() {
        given().contentType("application/json").body("{\"name\":\"x\"}")
                .when().post("/gw/attested-echo?lawfulBasisAttested=true")
                .then().statusCode(200).body("ok", is(true));
    }
}
