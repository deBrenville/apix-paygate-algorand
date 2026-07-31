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
 * needs no live registry or database, and we never expose APIX. The captured registry base URL is
 * re-based to this facade at serve time, so an agent can follow the HATEOAS links straight into it;
 * the service {@code endpoint} URLs (the real gateway) are left untouched. In production the agent
 * queries the live api-index.org instead — same shape, same links.
 */
@Path("/apix")
@Produces(MediaType.APPLICATION_JSON)
public class DemoRegistryResource {

    /** The registry base URL baked into the captured responses; rewritten to this facade on serve. */
    private static final String CAPTURED_REGISTRY_BASE = "http://localhost:8180";

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
        return Response.ok(body != null ? rebase(body, uriInfo) : EMPTY).build();
    }

    /** GET /apix — the captured HATEOAS discovery root (entry point; follow _links.services-search). */
    @GET
    public Response root(@Context UriInfo uriInfo) {
        String body = load("demo-registry/root.json");
        return Response.ok(body != null ? rebase(body, uriInfo) : "{}").build();
    }

    /** Rewrites the captured registry base URL to this facade's base, so HATEOAS links stay navigable. */
    private static String rebase(String body, UriInfo uriInfo) {
        String facadeBase = uriInfo.getBaseUri().toString().replaceAll("/+$", "") + "/apix";
        return body.replace(CAPTURED_REGISTRY_BASE, facadeBase);
    }

    private static String load(String resource) {
        try (InputStream in = DemoRegistryResource.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
