package org.botstandards.onramp.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/** Looks up configured upstreams by route. */
@ApplicationScoped
public class UpstreamRegistry {

    private final OnrampConfig config;

    public UpstreamRegistry(OnrampConfig config) {
        this.config = config;
    }

    public List<OnrampConfig.Upstream> all() {
        return config.upstreams();
    }

    public Optional<OnrampConfig.Upstream> byRoute(String route) {
        return config.upstreams().stream().filter(u -> u.route().equals(route)).findFirst();
    }
}
