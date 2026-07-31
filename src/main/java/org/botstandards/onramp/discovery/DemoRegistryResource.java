package org.botstandards.onramp.discovery;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Self-contained APIX discovery facade for the demo. Serves REAL APIX registry responses that were
 * captured once from a running apix-registry (see src/main/resources/demo-registry/) — so the demo
 * needs no live registry or database, and we never expose APIX. The response shape and semantics
 * are the registry's own; in production the agent queries the live api-index.org instead.
 */
@Path("/apix")
@Produces(MediaType.APPLICATION_JSON)
public class DemoRegistryResource {

    private static final String EMPTY = "{\"total\":0,\"page\":0,\"size\":20,\"_embedded\":{\"items\":[]}}";

    /** GET /apix/services?capability=… — the captured registry search response for that capability. */
    @GET
    @Path("/services")
    public Response services(@QueryParam("capability") String capability, @QueryParam("stage") String stage) {
        if (capability == null || !capability.matches("[a-z0-9.]+")) {
            return Response.ok(EMPTY).build();
        }
        String body = load("demo-registry/" + capability + ".json");
        return Response.ok(body != null ? body : EMPTY).build();
    }

    /** GET /apix — the captured HATEOAS discovery root. */
    @GET
    public Response root() {
        String body = load("demo-registry/root.json");
        return Response.ok(body != null ? body : "{}").build();
    }

    private static String load(String resource) {
        try (InputStream in = DemoRegistryResource.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
