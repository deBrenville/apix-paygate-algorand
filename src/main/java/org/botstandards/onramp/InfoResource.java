package org.botstandards.onramp;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Root info endpoint — a trivial liveness/identity signal for the gateway. */
@Path("/")
public class InfoResource {

    @ConfigProperty(name = "onramp.app-name")
    String appName;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> info() {
        return Map.of("name", appName, "status", "ok");
    }
}
