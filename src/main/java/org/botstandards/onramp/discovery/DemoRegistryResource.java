package org.botstandards.onramp.discovery;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Self-contained APIX discovery facade for the demo. Serves REAL APIX registry responses that were
 * captured once from a running apix-registry (see src/main/resources/demo-registry/) — so the demo
 * needs no live registry or database, and we never expose APIX. At serve time BOTH captured bases are
 * rewritten to the current request's base URI: the registry base ({@code :8180}) → this facade, and
 * the gateway base ({@code :8080}) → this app's own {@code /gw} — so the HATEOAS links AND the service
 * {@code endpoint}s stay navigable whether the demo runs on localhost or behind Caddy at
 * demo.api-index.org (which requires Quarkus X-Forwarded handling so getBaseUri() is the public URL).
 * In production the agent queries the live api-index.org instead — same shape, same links.
 */
@Path("/apix")
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
public class DemoRegistryResource {

    /** The registry base URL baked into the captured responses; rewritten to this facade on serve. */
    private static final String CAPTURED_REGISTRY_BASE = "http://localhost:8180";

    /** The gateway base baked into the captured service endpoints; rewritten to this app on serve. */
    private static final String CAPTURED_GATEWAY_BASE = "http://localhost:8080";

    private static final String EMPTY = "{\"total\":0,\"page\":0,\"size\":20,\"_embedded\":{\"items\":[]}}";

    /** GET /apix/services?capability=… — the captured registry search response for that capability. */
    @GET
    @Path("/services")
    public Response services(
            @QueryParam("capability") String capability, @QueryParam("stage") String stage,
            @Context UriInfo uriInfo) {
        if (capability == null || !capability.matches("[a-z0-9.]+")) {
            return Response.ok(EMPTY).build();
        }
        String body = load("demo-registry/" + capability + ".json");
        String out = body != null ? rebase(body, uriInfo) : EMPTY;
        return Response.ok(out.getBytes(StandardCharsets.UTF_8)).build();
    }

    /** GET /apix — the captured HATEOAS discovery root (entry point; follow _links.services-search). */
    @GET
    public Response root(@Context UriInfo uriInfo) {
        String body = load("demo-registry/root.json");
        String out = body != null ? rebase(body, uriInfo) : "{}";
        return Response.ok(out.getBytes(StandardCharsets.UTF_8)).build();
    }

    /** Rewrites the captured registry + gateway bases to the current request's base URI, so both the
     *  HATEOAS links (→ this facade) and the service endpoints (→ this app's /gw) stay navigable. */
    private static String rebase(String body, UriInfo uriInfo) {
        String base = uriInfo.getBaseUri().toString().replaceAll("/+$", "");
        return body
                .replace(CAPTURED_REGISTRY_BASE, base + "/apix")
                .replace(CAPTURED_GATEWAY_BASE, base);
    }

    private static String load(String resource) {
        try (InputStream in = DemoRegistryResource.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
