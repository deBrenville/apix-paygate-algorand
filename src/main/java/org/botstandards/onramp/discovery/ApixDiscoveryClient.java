package org.botstandards.onramp.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.botstandards.onramp.gateway.OnrampConfig;

/**
 * Real APIX discovery: searches the registry by capability (GET /services?capability=…&stage=…)
 * and returns the endpoint of the first matching service. This is how an agent finds a service it
 * did not know in advance — not by a hardcoded URL.
 */
@ApplicationScoped
public class ApixDiscoveryClient {

    @Inject
    OnrampConfig config;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** The endpoint URL of the first registered service that advertises {@code capability}. */
    public String endpointByCapability(String capability) {
        String url = config.registryUrl() + "/services?capability="
                + URLEncoder.encode(capability, StandardCharsets.UTF_8)
                + "&stage=" + config.registryStage();
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode items = mapper.readTree(res.body()).path("_embedded").path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new IllegalStateException("APIX discovery: no service for capability " + capability);
            }
            return items.get(0).path("endpoint").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("APIX discovery failed for " + capability + ": " + e.getMessage(), e);
        }
    }
}
