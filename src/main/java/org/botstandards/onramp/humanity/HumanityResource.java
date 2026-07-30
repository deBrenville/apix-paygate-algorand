package org.botstandards.onramp.humanity;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.ledger.MatchProof;

/**
 * Upstream B — the BSF humanity value layer. Chains to the neutral ledger (A) over x402, then
 * applies the pro-humanity filter and returns the enriched match-proof. Stateless.
 *
 * <p>Private origin: only reachable via the gateway (shared forward secret). Subject in body only.
 */
@Path("/internal/humanity")
public class HumanityResource {

    @Inject
    UpstreamPayingClient payingClient;

    @Inject
    ProHumanityPolicy policy;

    @Inject
    OnrampConfig config;

    public record ScreenRequest(String name, String country) {}

    @POST
    @Path("/screen")
    @Blocking
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response screen(@HeaderParam("X-Onramp-Forward") String forwardSecret, ScreenRequest req) {
        if (!config.internalForwardSecret().equals(forwardSecret)) {
            return Response.status(403).entity(Map.of("error", "forbidden")).build();
        }
        if (req == null || req.name() == null || req.name().isBlank()) {
            return Response.status(400).entity(Map.of("error", "name is required")).build();
        }
        MatchProof ledger = payingClient.screen(req.name(), req.country());
        MatchProof enriched = policy.apply(ledger);
        return Response.ok(enriched).build();
    }
}
