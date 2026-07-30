package org.botstandards.onramp.gateway;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * The gateway: reverse-proxies {@code POST /gw/{route}} to the configured upstream origin,
 * injecting the shared forward secret so the origin trusts only the gateway.
 *
 * <p>Payment enforcement is layered on separately as a request filter (Task 4) — this class
 * is the pass-through that runs once a request is allowed through.
 */
@Path("/gw")
public class ReverseProxyResource {

    @Inject
    UpstreamRegistry registry;

    private final HttpClient http = HttpClient.newHttpClient();

    @POST
    @Path("/{route}")
    @Blocking
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxy(@PathParam("route") String route, String body) {
        Optional<OnrampConfig.Upstream> upstream = registry.byRoute(route);
        if (upstream.isEmpty()) {
            return Response.status(404).entity("{\"error\":\"unknown route: " + route + "\"}").build();
        }
        OnrampConfig.Upstream cfg = upstream.get();
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.upstreamUrl()))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("X-Onramp-Forward", cfg.forwardSecret())
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return Response.status(res.statusCode())
                    .entity(res.body())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            return Response.status(502)
                    .entity("{\"error\":\"upstream unreachable\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
