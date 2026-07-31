package org.botstandards.onramp.client;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * For the live cascade demo: use the real GoPlausible facilitator (other tests use the stub), and
 * quiet the Quarkus startup banner so the recorded terminal shows only the beats. The gateway's own
 * "x402 settled" lines are category {@code org.botstandards...} and stay at INFO.
 */
public class CascadeLiveProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "onramp.facilitator-url", "https://facilitator.goplausible.xyz",
                "quarkus.banner.enabled", "false",
                "quarkus.log.category.\"io.quarkus\".level", "WARN");
    }
}
