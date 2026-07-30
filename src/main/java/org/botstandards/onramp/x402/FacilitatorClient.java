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

/** Talks to the GoPlausible x402 facilitator: verify (validate) then settle (broadcast on-chain). */
@ApplicationScoped
public class FacilitatorClient {

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
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.facilitatorUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(res.body());
        } catch (Exception e) {
            throw new RuntimeException("facilitator " + path + " failed: " + e.getMessage(), e);
        }
    }
}
