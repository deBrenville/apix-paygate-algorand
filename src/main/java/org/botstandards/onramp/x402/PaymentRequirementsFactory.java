package org.botstandards.onramp.x402;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.botstandards.onramp.gateway.OnrampConfig;

/** Builds the x402 PaymentRequirements a route advertises (asset/network from global config). */
@ApplicationScoped
public class PaymentRequirementsFactory {

    @Inject
    OnrampConfig config;

    public Map<String, Object> forRoute(OnrampConfig.Upstream route) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("scheme", "exact");
        req.put("network", X402AvmClient.TESTNET_CAIP2);
        req.put("asset", config.priceAssetId());
        req.put("amount", Long.toString(route.priceMicros()));
        req.put("payTo", route.payTo().orElse(""));
        req.put("maxTimeoutSeconds", 60);
        req.put("extra", Map.of("decimals", 6));
        return req;
    }
}
