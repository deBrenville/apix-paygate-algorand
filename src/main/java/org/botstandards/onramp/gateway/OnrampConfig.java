package org.botstandards.onramp.gateway;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/** Typed view of the {@code onramp.*} configuration, including the wrapped upstreams. */
@ConfigMapping(prefix = "onramp")
public interface OnrampConfig {

    String appName();

    String network();

    String facilitatorUrl();

    /** Price asset ASA id ("0" = native ALGO, but the facilitator requires an ASA — use USDC). */
    String priceAssetId();

    List<Upstream> upstreams();

    /** One wrapped API: a public paywalled route that reverse-proxies to a private origin. */
    interface Upstream {
        /** Public route segment, e.g. "humanity" → POST /gw/humanity. */
        String route();

        /** Private origin URL the gateway forwards paid requests to. */
        String upstreamUrl();

        /** Shared secret injected as X-Onramp-Forward so the origin only trusts the gateway. */
        String forwardSecret();

        /** Price in the asset's minor units (e.g. USDC has 6 decimals → 10000 = 0.01 USDC). */
        @WithDefault("0")
        long priceMicros();

        /** APIX capability advertised in the BSM, e.g. "compliance.sanctions.screen". */
        String capability();

        String description();

        Optional<String> schemaRef();
    }
}
