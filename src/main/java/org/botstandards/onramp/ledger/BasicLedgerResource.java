package org.botstandards.onramp.ledger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.botstandards.onramp.gateway.OnrampConfig;

/**
 * Upstream A — the inner demo service ("Hello World A"): the 0-hop gated leaf of the cascade.
 * Reachable only via the gateway (which injects the shared forward secret) and settled over x402
 * by the outer service B. The content is deliberately trivial — the demo proves the <em>payment
 * cascade</em> and server-side chaining, not any domain logic. The real sanctions/humanity services
 * live in the same repo (the {@code sanctions}/{@code humanity} domain classes) for later, non-demo use.
 *
 * <p>Private origin: only reachable via the gateway. No payload is required; any JSON body is accepted.
 */
@Path("/internal/ledger")
public class BasicLedgerResource {

    @Inject
    OnrampConfig config;

    /** Optional demo payload; the inner service ignores it (content is irrelevant to the demo). */
    public record ScreenRequest(String name, String country) {}

    @POST
    @Path("/screen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response screen(@HeaderParam("X-Onramp-Forward") String forwardSecret, ScreenRequest req) {
        if (!config.internalForwardSecret().equals(forwardSecret)) {
            return Response.status(403).entity(Map.of("error", "forbidden")).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "A");
        body.put("message", "Hello World A");
        body.put("note", "inner service — you reached me only because service B discovered and paid me over x402");
        body.put("settledAt", Instant.now().toString());
        return Response.ok(body).build();
    }
}
