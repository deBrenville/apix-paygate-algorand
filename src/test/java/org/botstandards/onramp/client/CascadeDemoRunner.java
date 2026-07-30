package org.botstandards.onramp.client;

import static io.restassured.RestAssured.given;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.gateway.UpstreamRegistry;
import org.botstandards.onramp.x402.X402AvmClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Narrated live demo for the video (run with -Dx402.live=true). Drives the full cascade and prints
 * the six beats to the console; the Quarkus server log shows both on-chain settlements per hop.
 *
 * <pre>mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true</pre>
 */
@QuarkusTest
@TestProfile(CascadeLiveProfile.class)
class CascadeDemoRunner {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";
    private static final String SUBJECT = "Amara Okonkwo"; // the ISGH case — OFAC-only, humanity-serving
    private static final String COUNTRY = "NG";

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @Inject
    UpstreamRegistry registry;

    @Inject
    OnrampConfig config;

    @Test
    void run() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "narrated demo — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_PAYER_MNEMONIC");

        OnrampConfig.Upstream humanity = registry.byRoute("humanity").orElseThrow();
        OnrampConfig.Upstream basic = registry.byRoute("sanctions-basic").orElseThrow();
        long asset = Long.parseLong(config.priceAssetId());
        double bPrice = humanity.priceMicros() / 1_000_000.0;
        double aPrice = basic.priceMicros() / 1_000_000.0;

        beat(1, "DISCOVER — the agent reads the APIX manifest (no docs, machine-native)");
        Response bsm = given().get("/.well-known/bsm/humanity");
        System.out.printf("   capability : %s%n", bsm.jsonPath().getString("capability"));
        System.out.printf("   endpoint   : %s%n", bsm.jsonPath().getString("endpoint"));
        System.out.printf(Locale.US, "   price      : %.2f USDC (asset %s) on %s%n",
                Long.parseLong(bsm.jsonPath().getString("price.amount")) / 1_000_000.0,
                bsm.jsonPath().getString("price.asset"), bsm.jsonPath().getString("price.network"));
        System.out.printf("   payment    : %s / %s%n", bsm.jsonPath().getString("payment.protocol"),
                bsm.jsonPath().getString("payment.scheme"));

        beat(2, "PAY-GATE — the agent calls without paying and gets 402");
        Response unpaid = given().contentType("application/json")
                .body(body()).post("/gw/humanity?lawfulBasisAttested=true");
        System.out.printf(Locale.US, "   HTTP %d — %.2f USDC required, payTo %s%n", unpaid.statusCode(),
                Long.parseLong(unpaid.jsonPath().getString("accepts[0].amount")) / 1_000_000.0,
                unpaid.jsonPath().getString("accepts[0].payTo"));

        beat(3, "SETTLE — the agent signs a USDC payment on Algorand and retries");
        Account agent = new Account(unquote(agentMnemonic.get()));
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        X402AvmClient x402 = new X402AvmClient();
        Map<String, Object> payload = x402.buildPayload(agent, humanity.payTo().orElseThrow(),
                humanity.priceMicros(), asset, params);
        Response paid = given().header("X-PAYMENT", x402.toHeaderValue(payload))
                .contentType("application/json").body(body())
                .post("/gw/humanity?lawfulBasisAttested=true");
        System.out.printf("   HTTP %d — paid; both hops settled on-chain (see the 'x402 settled' lines above)%n",
                paid.statusCode());

        beat(4, "CASCADE — B discovered and paid the neutral ledger A over x402 (second hop)");
        System.out.println("   (see the server log above: a second 'x402 settled' line for B->A)");

        beat(5, "RESULT — neutral ledger + BSF humanity filter");
        System.out.printf("   outcome    : %s%n", paid.jsonPath().getString("outcome"));
        System.out.printf("   provenance : register=%s  score=%s%n",
                paid.jsonPath().getString("matches[0].register"), paid.jsonPath().getString("matches[0].score"));
        System.out.printf("   exemption  : %s%n", paid.jsonPath().getString("exemption.reason"));
        System.out.printf("   precedent  : %s%n", paid.jsonPath().getString("exemption.precedent"));

        beat(6, "ECONOMICS — value-add margin, machine to machine");
        System.out.printf(Locale.US, "   agent paid B %.2f USDC; B paid A %.2f USDC; B margin = %.2f USDC%n",
                bPrice, aPrice, bPrice - aPrice);
        System.out.println("   No account, no email, no OAuth, no CAPTCHA. Two paid, discoverable hops.");
        System.out.println("═".repeat(72));
    }

    private static String body() {
        return "{\"name\":\"" + SUBJECT + "\",\"country\":\"" + COUNTRY + "\"}";
    }

    private static void beat(int n, String title) {
        System.out.println();
        System.out.println("═".repeat(72));
        System.out.printf("  BEAT %d · %s%n", n, title);
        System.out.println("─".repeat(72));
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
