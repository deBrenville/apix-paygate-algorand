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

    /** Shared secret the gateway injects to internal origins; they reject requests without it. */
    String internalForwardSecret();

    /** This app's own public base URL (where /gw/{route} lives) — used for the cascade's second hop. */
    @WithDefault("http://localhost:8080")
    String selfBaseUrl();

    /** The route the outer service discovers and pays downstream (the inner service). */
    @WithDefault("hello-inner")
    String downstreamRoute();

    /** The APIX registry base URL agents search for discovery (GET /services?capability=…).
     *  Defaults to the self-contained demo facade (captured real responses); override to a live
     *  registry (e.g. http://localhost:8180 or https://api-index.org) to discover for real. */
    @WithDefault("http://localhost:8080/apix")
    String registryUrl();

    /** Lifecycle stage to search in the registry (demo services register at DEVELOPMENT). */
    @WithDefault("DEVELOPMENT")
    String registryStage();

    List<Upstream> upstreams();

    /** Cascade wallets — Service B (outer, route "hello") and Service C (inner, "hello-inner"). */
    ServiceB serviceB();

    ServiceC serviceC();

    /** Service B (outer): receives hop 1 from Agent A and signs hop 2 to Service C. */
    interface ServiceB {
        /** Service B's wallet — the hop-1 payTo and the signer of hop 2. Verified against the mnemonic at startup. */
        String senderAddress();

        /** Where Agent A pays on hop 1; defaults to the sender wallet unless explicitly split. */
        String receiverAddress();

        /** Service B's signing key for hop 2 (env-only, boot-critical). Empty in local build / tests. */
        Optional<String> senderMnemonic();
    }

    /** Service C (inner): a pure receiver in the server — signs nothing, so only an address. */
    interface ServiceC {
        /** Where Service B pays on hop 2. */
        String receiverAddress();
    }

    /** One wrapped API: a public paywalled route that reverse-proxies to a private origin. */
    interface Upstream {
        /** Public route segment, e.g. "hello" → POST /gw/hello. */
        String route();

        /** Private origin URL the gateway forwards paid requests to. */
        String upstreamUrl();

        /** Shared secret injected as X-Onramp-Forward so the origin only trusts the gateway. */
        String forwardSecret();

        /** Algorand address that receives payment for this route. Required for paid routes. */
        Optional<String> payTo();

        /** Price in the asset's minor units (e.g. USDC has 6 decimals → 10000 = 0.01 USDC). 0 = free route (no payment). */
        @WithDefault("0")
        long priceMicros();

        /** If true, the gateway requires a loggable {@code ?lawfulBasisAttested=true} (non-PII audit). */
        @WithDefault("false")
        boolean requiresAttestation();

        /** APIX capability advertised in the BSM, e.g. "demo.hello.inner". */
        String capability();

        String description();

        Optional<String> schemaRef();
    }
}
