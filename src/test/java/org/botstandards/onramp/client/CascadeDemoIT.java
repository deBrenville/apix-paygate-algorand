package org.botstandards.onramp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.algorand.algosdk.account.Account;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Optional;
import org.botstandards.onramp.discovery.ApixDiscoveryClient;
import org.botstandards.onramp.x402.X402PayingCaller;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The live cascade over REAL APIX discovery (run with -Dx402.live=true): the agent searches the
 * registry by capability to find the outer service B, pays B over x402; B searches for and pays the
 * inner service A; A returns "Hello World A"; B nests it under its own "Hello World B" and returns.
 * Two real on-chain settlements per call. Discovery is served by the self-contained facade (captured
 * real registry responses); the facilitator is the live GoPlausible one.
 */
@QuarkusTest
@TestProfile(CascadeLiveProfile.class)
class CascadeDemoIT {

    @ConfigProperty(name = "onramp.agent.sender-mnemonic")
    Optional<String> agentMnemonic;

    @Inject
    ApixDiscoveryClient discovery;

    @Inject
    X402PayingCaller caller;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cascadeReturnsHelloWorldBNestingHelloWorldA() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "live cascade — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_AGENT_SENDER_MNEMONIC");

        Account agent = new Account(unquote(agentMnemonic.get()));
        String outerEndpoint = discovery.endpointByCapability("demo.hello");

        String json = caller.callPaid(
                outerEndpoint, "?lawfulBasisAttested=true",
                "{\"name\":\"hello\",\"country\":\"\"}", agent);

        JsonNode result = mapper.readTree(json);
        // Outer greeting (hop 1: agent -> B settled) nesting the inner greeting (hop 2: B -> A settled).
        assertEquals("Hello World B", result.path("message").asText());
        assertEquals("Hello World A", result.path("innerResult").path("message").asText());
    }

    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replaceAll("\\s+", " ");
    }
}
