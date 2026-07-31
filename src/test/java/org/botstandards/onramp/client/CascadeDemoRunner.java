package org.botstandards.onramp.client;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Optional;
import org.botstandards.onramp.discovery.ApixDiscoveryClient;
import org.botstandards.onramp.x402.X402AvmClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Narrated live demo for the video. The agent DISCOVERS the service via the real APIX registry search
 * (by capability), then hits the DYNAMIC x402 gate: a real 402 when unpaid, a real on-chain settlement
 * when paid, cascading into a second hop (B → A).
 *
 * <p>Each beat is an <b>independently runnable</b> test so you can record and narrate one at a time,
 * then run {@link #run()} as the finale — proof the whole cascade runs autonomously, no interaction:
 *
 * <pre>
 * mvn -o test "-Dtest=CascadeDemoRunner#discover"                        # BEAT 1  (local)
 * mvn -o test "-Dtest=CascadeDemoRunner#payGate"                         # BEAT 2  (local)
 * mvn -o test "-Dtest=CascadeDemoRunner#settle"   "-Dx402.live=true"     # BEATS 3-6 (on-chain)
 * mvn -o test "-Dtest=CascadeDemoRunner#run"      "-Dx402.live=true"     # ALL, autonomous
 * </pre>
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

    // ---- individually runnable beats (narrate each) --------------------------------------------

    /** BEAT 1 — discovery only. No chain, no payment. */
    @Test
    void discover() {
        beatDiscover();
    }

    /** BEAT 2 — the dynamic 402 only. No chain, no payment. */
    @Test
    void payGate() {
        beatPayGate(resolveEndpoint());
    }

    /** BEATS 3-6 — pay once; both hops settle on-chain and the humanity filter returns its verdict. */
    @Test
    void settle() throws Exception {
        requireLive();
        beatSettle(resolveEndpoint());
    }

    // ---- the finale: everything, no interaction ------------------------------------------------

    /** All beats end to end — the "it runs by itself" take. */
    @Test
    void run() throws Exception {
        requireLive();
        String endpoint = beatDiscover();
        beatPayGate(endpoint);
        beatSettle(endpoint);
    }

    // ---- beats (shared by the single-beat tests and the full run) ------------------------------

    private String beatDiscover() {
        beat(1, "DISCOVER — the agent searches the APIX registry by capability (no hardcoded URL)",
                "The agent finds the service by capability in the APIX registry — no URL is hardcoded.");
        System.out.printf("   search     : capability=%s%n", CAPABILITY);
        String endpoint = resolveEndpoint();
        System.out.printf("   found      : %s%n", endpoint);
        return endpoint;
    }

    private void beatPayGate(String endpoint) {
        beat(2, "PAY-GATE (dynamic) — the agent calls without paying and gets a real 402",
                "The unpaid call is refused with HTTP 402 and the exact price. x402 means: pay first.");
        Response unpaid = unpaidCall(endpoint);
        System.out.printf(Locale.US, "   HTTP %d — %.2f USDC required (asset %s), payTo %s%n",
                unpaid.statusCode(),
                Long.parseLong(unpaid.jsonPath().getString("accepts[0].amount")) / 1_000_000.0,
                unpaid.jsonPath().getString("accepts[0].asset"),
                unpaid.jsonPath().getString("accepts[0].payTo"));
        assertEquals(402, unpaid.statusCode(), "unpaid call must be gated with 402");
    }

    private void beatSettle(String endpoint) throws Exception {
        Response terms = unpaidCall(endpoint); // the 402 carries payTo/amount/asset
        String payTo = terms.jsonPath().getString("accepts[0].payTo");
        long amount = Long.parseLong(terms.jsonPath().getString("accepts[0].amount"));
        long asset = Long.parseLong(terms.jsonPath().getString("accepts[0].asset"));

        beat(3, "SETTLE — the agent signs a USDC payment on Algorand for exactly those terms",
                "The agent signs a USDC payment on Algorand. Two 'x402 settled' lines = two real on-chain transactions.");
        Account agent = new Account(unquote(agentMnemonic.get()));
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        X402AvmClient x402 = new X402AvmClient();
        String xPayment = x402.toHeaderValue(x402.buildPayload(agent, payTo, amount, asset, params));
        Response paid = given().header("X-PAYMENT", xPayment).contentType("application/json")
                .body(body()).post(endpoint + "?lawfulBasisAttested=true");
        int status = paid.statusCode();
        String outcome = paid.jsonPath().getString("outcome");
        String register = paid.jsonPath().getString("matches[0].register");
        String score = paid.jsonPath().getString("matches[0].score");
        if (status == 200) {
            System.out.println("   HTTP 200 — paid; both hops settled on-chain (see the 'x402 settled' lines above)");
        } else {
            System.out.printf("   HTTP %d — settlement did NOT complete (see the errors above)%n", status);
        }

        beat(4, "CASCADE — B discovered and paid the neutral ledger A over x402 (second hop)",
                "The service it paid (B) is itself an agent — it discovers and pays a second service (A) over x402.");
        System.out.println("   (server log above: a second 'x402 settled' line for the B->A hop)");

        beat(5, "RESULT — neutral ledger + BSF humanity filter",
                "The neutral ledger flags OFAC; the humanity layer returns MATCH_EXEMPT, keeping the record as evidence.");
        System.out.printf("   outcome    : %s%n", outcome);
        System.out.printf("   provenance : register=%s  score=%s%n", register, score);
        System.out.printf("   exemption  : %s%n", paid.jsonPath().getString("exemption.reason"));
        System.out.printf("   precedent  : %s%n", paid.jsonPath().getString("exemption.precedent"));

        beat(6, "ECONOMICS — value-add margin, machine to machine",
                "The agent paid 0.03, B paid 0.01 and kept 0.02 — value-add pricing, machine to machine.");
        System.out.println("   agent paid B 0.03 USDC; B paid A 0.01 USDC; B margin = 0.02 USDC");
        System.out.println("   No account, no email, no OAuth, no CAPTCHA. Discovered, then paid.");
        System.out.println("=".repeat(72));

        // Hard gate — a broken take must fail RED, not narrate a green lie.
        // 200 + MATCH_EXEMPT is only reachable when BOTH x402 hops settled: the second settlement
        // (B->A) is a precondition for A returning the OFAC match that B then exempts.
        assertEquals(200, status, "paid call did not return 200 — a payment hop failed to settle");
        assertEquals("MATCH_EXEMPT", outcome, "cascade did not complete — A unreachable or policy not applied");
        assertEquals("OFAC", register, "provenance lost — expected the OFAC match from neutral ledger A");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void requireLive() {
        assumeTrue(Boolean.getBoolean("x402.live"), "on-chain beat — run with -Dx402.live=true");
        assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_PAYER_MNEMONIC");
    }

    private String resolveEndpoint() {
        return discovery.endpointByCapability(CAPABILITY);
    }

    private Response unpaidCall(String endpoint) {
        return given().contentType("application/json").body(body()).post(endpoint + "?lawfulBasisAttested=true");
    }

    private static String body() {
        return "{\"name\":\"" + SUBJECT + "\",\"country\":\"" + COUNTRY + "\"}";
    }

    private static void beat(int n, String title, String caption) {
        System.out.println();
        System.out.println("=".repeat(72));
        System.out.printf("  BEAT %d · %s%n", n, title);
        System.out.println("-".repeat(72));
        System.out.printf("  > %s%n", caption); // plain-language subtitle for the silent screen recording
        System.out.println();
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
