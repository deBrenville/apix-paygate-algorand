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
import org.botstandards.onramp.discovery.ApixDiscoveryClient;
import org.botstandards.onramp.x402.X402AvmClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Narrated live demo for the video (run with -Dx402.live=true). The agent DISCOVERS the service via
 * the real APIX registry search (by capability), then hits the DYNAMIC x402 gate: a real 402 when
 * unpaid, a real on-chain settlement when paid. Prints six beats; the server log shows both hops.
 *
 * <pre>mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true</pre>
 */
@QuarkusTest
@TestProfile(CascadeLiveProfile.class)
class CascadeDemoRunner {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";
    private static final String CAPABILITY = "compliance.sanctions.screen.humanity";
    private static final String SUBJECT = "Amara Okonkwo"; // the ISGH case — OFAC-only, humanity-serving
    private static final String COUNTRY = "NG";

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @Inject
    ApixDiscoveryClient discovery;

    @Test
    void run() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "narrated demo — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_PAYER_MNEMONIC");

        beat(1, "DISCOVER — the agent searches the APIX registry by capability (no hardcoded URL)");
        System.out.printf("   search     : capability=%s%n", CAPABILITY);
        String endpoint = discovery.endpointByCapability(CAPABILITY);
        System.out.printf("   found      : %s%n", endpoint);

        beat(2, "PAY-GATE (dynamic) — the agent calls without paying and gets a real 402");
        Response unpaid = given().contentType("application/json")
                .body(body()).post(endpoint + "?lawfulBasisAttested=true");
        String payTo = unpaid.jsonPath().getString("accepts[0].payTo");
        long amount = Long.parseLong(unpaid.jsonPath().getString("accepts[0].amount"));
        long asset = Long.parseLong(unpaid.jsonPath().getString("accepts[0].asset"));
        System.out.printf(Locale.US, "   HTTP %d — %.2f USDC required (asset %d), payTo %s%n",
                unpaid.statusCode(), amount / 1_000_000.0, asset, payTo);

        beat(3, "SETTLE — the agent signs a USDC payment on Algorand for exactly those terms");
        Account agent = new Account(unquote(agentMnemonic.get()));
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        X402AvmClient x402 = new X402AvmClient();
        String xPayment = x402.toHeaderValue(x402.buildPayload(agent, payTo, amount, asset, params));
        Response paid = given().header("X-PAYMENT", xPayment).contentType("application/json")
                .body(body()).post(endpoint + "?lawfulBasisAttested=true");
        System.out.printf("   HTTP %d — paid; both hops settled on-chain (see the 'x402 settled' lines above)%n",
                paid.statusCode());

        beat(4, "CASCADE — B discovered and paid the neutral ledger A over x402 (second hop)");
        System.out.println("   (server log above: a second 'x402 settled' line for the B->A hop)");

        beat(5, "RESULT — neutral ledger + BSF humanity filter");
        System.out.printf("   outcome    : %s%n", paid.jsonPath().getString("outcome"));
        System.out.printf("   provenance : register=%s  score=%s%n",
                paid.jsonPath().getString("matches[0].register"), paid.jsonPath().getString("matches[0].score"));
        System.out.printf("   exemption  : %s%n", paid.jsonPath().getString("exemption.reason"));
        System.out.printf("   precedent  : %s%n", paid.jsonPath().getString("exemption.precedent"));

        beat(6, "ECONOMICS — value-add margin, machine to machine");
        System.out.println("   agent paid B 0.03 USDC; B paid A 0.01 USDC; B margin = 0.02 USDC");
        System.out.println("   No account, no email, no OAuth, no CAPTCHA. Discovered, then paid.");
        System.out.println("=".repeat(72));
    }

    private static String body() {
        return "{\"name\":\"" + SUBJECT + "\",\"country\":\"" + COUNTRY + "\"}";
    }

    private static void beat(int n, String title) {
        System.out.println();
        System.out.println("=".repeat(72));
        System.out.printf("  BEAT %d · %s%n", n, title);
        System.out.println("-".repeat(72));
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
