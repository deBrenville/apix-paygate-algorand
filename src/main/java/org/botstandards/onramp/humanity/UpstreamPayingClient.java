package org.botstandards.onramp.humanity;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.gateway.UpstreamRegistry;
import org.botstandards.onramp.ledger.MatchProof;
import org.botstandards.onramp.x402.X402AvmClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Upstream B's outbound leg: discovers Upstream A's paid route and pays it over x402 (the second
 * hop of the cascade), using B's own testnet account. Returns A's neutral match-proof.
 *
 * <p>The subject travels in the POST body only (never a query parameter).
 */
@ApplicationScoped
public class UpstreamPayingClient {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";

    @Inject
    OnrampConfig config;

    @Inject
    UpstreamRegistry registry;

    @ConfigProperty(name = "onramp.b-payer-mnemonic")
    Optional<String> bPayerMnemonic;

    private final X402AvmClient x402 = new X402AvmClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public MatchProof screen(String name, String country) {
        OnrampConfig.Upstream route = registry.byRoute(config.humanityDownstreamRoute())
                .orElseThrow(() -> new IllegalStateException(
                        "downstream route not configured: " + config.humanityDownstreamRoute()));
        String payTo = route.payTo()
                .filter(a -> !a.isBlank())
                .orElseThrow(() -> new IllegalStateException("downstream route has no payTo (ONRAMP_A_PAYTO_ADDRESS)"));
        String mnemonic = bPayerMnemonic
                .map(UpstreamPayingClient::unquote)
                .orElseThrow(() -> new IllegalStateException("ONRAMP_B_PAYER_MNEMONIC not set"));

        try {
            Account payer = new Account(mnemonic);
            AlgodClient algod = new AlgodClient(ALGOD, 443, "");
            TransactionParametersResponse params = algod.TransactionParams().execute().body();

            Map<String, Object> payload = x402.buildPayload(
                    payer, payTo, route.priceMicros(), Long.parseLong(config.priceAssetId()), params);
            String xPayment = x402.toHeaderValue(payload);

            // Propagate the caller's data-processing attestation to A (loggable, non-PII).
            String url = config.selfBaseUrl() + "/gw/" + route.route()
                    + "?lawfulBasisAttested=true&purpose=sanctions-screening";
            String body = mapper.writeValueAsString(
                    Map.of("name", name, "country", country == null ? "" : country));

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-PAYMENT", xPayment)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("downstream " + url + " returned " + res.statusCode() + ": " + res.body());
            }
            return mapper.readValue(res.body(), MatchProof.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cascade call to A failed: " + e.getMessage(), e);
        }
    }

    /** Strips surrounding quotes / normalizes whitespace from a .env mnemonic. Never logged. */
    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replaceAll("\\s+", " ");
    }
}
