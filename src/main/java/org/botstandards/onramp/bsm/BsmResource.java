package org.botstandards.onramp.bsm;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.gateway.UpstreamRegistry;

/** Publishes the discovery layer: an index of wrapped routes and a BSM per route. */
@Path("/.well-known/bsm")
@Produces(MediaType.APPLICATION_JSON)
public class BsmResource {

    @Inject
    UpstreamRegistry registry;

    @Inject
    OnrampConfig config;

    @GET
    public Map<String, Object> index(@Context UriInfo uri) {
        List<Map<String, String>> services = registry.all().stream()
                .map(u -> Map.of(
                        "route", u.route(),
                        "capability", u.capability(),
                        "bsm", uri.getBaseUri() + ".well-known/bsm/" + u.route()))
                .toList();
        return Map.of("service", config.appName(), "services", services);
    }

    @GET
    @Path("/{route}")
    public Response one(@PathParam("route") String route, @Context UriInfo uri) {
        Optional<OnrampConfig.Upstream> up = registry.byRoute(route);
        if (up.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "unknown route: " + route)).build();
        }
        OnrampConfig.Upstream u = up.get();
        Bsm bsm = new Bsm(
                config.appName(),
                u.capability(),
                uri.getBaseUri() + "gw/" + route,
                new Bsm.Price(Long.toString(u.priceMicros()), config.priceAssetId(), config.network()),
                new Bsm.Payment("x402", "exact", config.facilitatorUrl()),
                new Bsm.Io(u.schemaRef().orElse(null), null),
                u.description());
        return Response.ok(bsm).build();
    }
}
