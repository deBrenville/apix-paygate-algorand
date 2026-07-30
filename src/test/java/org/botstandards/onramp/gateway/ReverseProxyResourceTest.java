package org.botstandards.onramp.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The gateway forwards to the configured origin with the shared forward secret; unknown route → 404. */
@QuarkusTest
class ReverseProxyResourceTest {

    static WireMockServer wm;

    @BeforeAll
    static void startStub() {
        wm = new WireMockServer(8089);
        wm.start();
        wm.stubFor(post(urlEqualTo("/echo"))
                .withHeader("X-Onramp-Forward", equalTo("s3cr3t"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));
    }

    @AfterAll
    static void stopStub() {
        wm.stop();
    }

    @Test
    void forwardsToOriginWithSecretAndReturnsItsResponse() {
        given().contentType("application/json").body("{\"hi\":1}")
                .when().post("/gw/echo")
                .then().statusCode(200).body("ok", is(true));

        wm.verify(postRequestedFor(urlEqualTo("/echo"))
                .withHeader("X-Onramp-Forward", equalTo("s3cr3t")));
    }

    @Test
    void unknownRouteReturns404() {
        given().contentType("application/json").body("{}")
                .when().post("/gw/nope")
                .then().statusCode(404).body("error", org.hamcrest.Matchers.containsString("nope"));
    }
}
