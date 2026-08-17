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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.botstandards.onramp.gateway.OnrampConfig;

/**
 * Upstream B — the outer demo service ("Hello World B"). Before answering the agent, it chains to the
 * inner service (A) over x402 on its own wallet (see {@link UpstreamPayingClient}), then returns its
 * own greeting with A's nested result. This is the whole point of the demo: a gated service that
 * itself consumes and pays another gated service in the background — two on-chain settlements per call.
 *
 * <p>Private origin: only reachable via the gateway (shared forward secret). Any JSON body is accepted.
 */
@Path("/internal/humanity")
public class HumanityResource {

    @Inject
    UpstreamPayingClient payingClient;

    @Inject
    OnrampConfig config;

    /** Optional demo payload; the outer service ignores it (content is irrelevant to the demo). */
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
        Map<String, Object> inner = payingClient.callInner();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "B");
        body.put("message", "Hello World B");
        body.put("note", "outer service — before answering you, I discovered service A and paid it over x402");
        body.put("innerResult", inner);
        body.put("settledAt", Instant.now().toString());
        return Response.ok(body).build();
    }
}
