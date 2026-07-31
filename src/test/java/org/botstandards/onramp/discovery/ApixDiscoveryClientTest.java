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
    void followsRootThenSearchToHumanityEndpoint() {
        String endpoint = discovery.endpointByCapability("compliance.sanctions.screen.humanity");
        assertTrue(endpoint.endsWith("/gw/humanity"), "got: " + endpoint);
    }

    @Test
    void followsRootThenSearchToLedgerEndpoint() {
        String endpoint = discovery.endpointByCapability("compliance.sanctions.ledger");
        assertTrue(endpoint.endsWith("/gw/sanctions-basic"), "got: " + endpoint);
    }
}
