package org.botstandards.onramp.x402;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.jboss.logging.Logger;

/** Talks to the GoPlausible x402 facilitator: verify (validate) then settle (broadcast on-chain). */
@ApplicationScoped
public class FacilitatorClient {

    private static final Logger LOG = Logger.getLogger(FacilitatorClient.class);

    /** The public facilitator occasionally resets a connection; a couple of retries make it robust. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 800;

    @Inject
    OnrampConfig config;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public record VerifyResult(boolean isValid, String reason) {}

    public record SettleResult(boolean success, String txId) {}

    /** POST /verify — validates a signed payment without settling. */
    public VerifyResult verify(Map<String, Object> body) {
        JsonNode r = post("/verify", body);
        return new VerifyResult(r.path("isValid").asBoolean(false), r.path("invalidReason").asText(null));
    }

    /** POST /settle — broadcasts the transfer on-chain. */
    public SettleResult settle(Map<String, Object> body) {
        JsonNode r = post("/settle", body);
        return new SettleResult(r.path("success").asBoolean(false), r.path("transaction").asText(null));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String payload;
        try {
            payload = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("facilitator " + path + " failed: cannot serialize body: " + e.getMessage(), e);
        }

        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(config.facilitatorUrl() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                return mapper.readTree(res.body());
            } catch (Exception e) {
                // Transport-level failure (e.g. "Connection reset") — no HTTP response was received.
                // Safe to retry: settle re-submits the *same* signed Algorand transaction, which the
                // ledger dedupes by txid within its validity window, so there is no double payment.
                last = new RuntimeException("facilitator " + path + " failed: " + e, e);
                if (attempt < MAX_ATTEMPTS) {
                    LOG.warnf("facilitator %s attempt %d/%d failed (%s) — retrying",
                            path, attempt, MAX_ATTEMPTS, e);
                    try {
                        Thread.sleep(BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last;
    }
}
