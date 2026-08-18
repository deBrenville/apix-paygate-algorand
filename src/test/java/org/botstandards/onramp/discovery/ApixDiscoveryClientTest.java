package org.botstandards.onramp.discovery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * HATEOAS discovery, offline: the agent enters at the registry root and follows only links
 * (services-search → the service's endpoint). It knows nothing but the entry URL and its goal.
 */
@QuarkusTest
class ApixDiscoveryClientTest {

    @Inject
    ApixDiscoveryClient discovery;

    @Test
    void followsRootThenSearchToOuterEndpoint() {
        String endpoint = discovery.endpointByCapability("demo.hello");
        assertTrue(endpoint.endsWith("/gw/hello"), "got: " + endpoint);
    }

    @Test
    void followsRootThenSearchToInnerEndpoint() {
        String endpoint = discovery.endpointByCapability("demo.hello.inner");
        assertTrue(endpoint.endsWith("/gw/hello-inner"), "got: " + endpoint);
    }
}
