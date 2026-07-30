package org.botstandards.onramp.client;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.gateway.UpstreamRegistry;
import org.botstandards.onramp.x402.X402AvmClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The live cascade (run with -Dx402.live=true): the agent pays B over x402; B discovers and pays
 * A (the neutral ledger) over x402; A returns the match-proof; B applies the pro-humanity filter.
 * For the ISGH subject (OFAC-only + humanity-serving) the end result is MATCH_EXEMPT. Two real
 * on-chain settlements. Asset is the faucet-free dUSD; A's receiver defaults to the agent address.
 */
@QuarkusTest
@TestProfile(CascadeLiveProfile.class)
class CascadeDemoIT {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @Inject
    UpstreamRegistry registry;

    @Inject
    OnrampConfig config;

    @Test
    void cascadeExemptsIsghCase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "live cascade — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_PAYER_MNEMONIC");

        OnrampConfig.Upstream humanity = registry.byRoute("humanity").orElseThrow();
        String bPayTo = humanity.payTo().orElseThrow();
        long amount = humanity.priceMicros();
        long asset = Long.parseLong(config.priceAssetId());

        Account agent = new Account(unquote(agentMnemonic.get()));
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        TransactionParametersResponse params = algod.TransactionParams().execute().body();

        X402AvmClient x402 = new X402AvmClient();
        Map<String, Object> payload = x402.buildPayload(agent, bPayTo, amount, asset, params);
        String xPayment = x402.toHeaderValue(payload);

        given()
                .header("X-PAYMENT", xPayment)
                .contentType("application/json")
                .body("{\"name\":\"Amara Okonkwo\",\"country\":\"NG\"}")
                .when()
                .post("/gw/humanity?lawfulBasisAttested=true")
                .then()
                .statusCode(200)
                .body("outcome", is("MATCH_EXEMPT"))
                .body("exemption.reason", notNullValue())
                .body("matches[0].register", is("OFAC"));
    }

    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replaceAll("\\s+", " ");
    }
}
