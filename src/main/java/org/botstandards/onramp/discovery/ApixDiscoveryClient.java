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
 * Real, HATEOAS-driven APIX discovery. The agent knows exactly two things: the registry ENTRY URL
 * and its GOAL (a capability). It then only follows links: GET the entry → follow the
 * {@code services-search} link → search by capability → follow the first service's {@code endpoint}.
 * No service URL and no payment detail is hardcoded.
 */
@ApplicationScoped
public class ApixDiscoveryClient {

    @Inject
    OnrampConfig config;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** The endpoint URL of the first registered service that advertises {@code capability}. */
    public String endpointByCapability(String capability) {
        try {
            // 1. Enter at the registry root and follow the services-search link (a URI template).
            JsonNode root = getJson(config.registryUrl());
            String searchTemplate = root.path("_links").path("services-search").path("href").asText();
            if (searchTemplate.isBlank()) {
                throw new IllegalStateException("no services-search link at " + config.registryUrl());
            }
            // 2. Expand the template {?capability,stage,…} with our goal.
            String searchBase = searchTemplate.replaceAll("\\{[^}]*}$", "");
            String searchUrl = searchBase + "?capability=" + enc(capability) + "&stage=" + config.registryStage();

            // 3. Follow the search and take the first service's endpoint link.
            JsonNode items = getJson(searchUrl).path("_embedded").path("items");
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

    private JsonNode getJson(String url) throws Exception {
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(res.body());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
