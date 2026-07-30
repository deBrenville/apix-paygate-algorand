package org.botstandards.onramp.client;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** For the live cascade IT: use the real GoPlausible facilitator (other tests use the stub). */
public class CascadeLiveProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("onramp.facilitator-url", "https://facilitator.goplausible.xyz");
    }
}
