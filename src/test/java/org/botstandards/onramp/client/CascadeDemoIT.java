package org.botstandards.onramp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
 * registry by capability to find B, pays B over x402; B searches for and pays the neutral ledger A;
 * A returns the match-proof; B applies the pro-humanity filter -> MATCH_EXEMPT for the ISGH case.
 * Two real on-chain settlements. Discovery is served by the self-contained facade (captured real
 * registry responses); the facilitator is the live GoPlausible one.
 */
@QuarkusTest
@TestProfile(CascadeLiveProfile.class)
class CascadeDemoIT {

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @Inject
    ApixDiscoveryClient discovery;

    @Inject
    X402PayingCaller caller;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cascadeExemptsIsghCase() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "live cascade — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "need ONRAMP_PAYER_MNEMONIC");

        Account agent = new Account(unquote(agentMnemonic.get()));
        String humanityEndpoint = discovery.endpointByCapability("compliance.sanctions.screen.humanity");

        String json = caller.callPaid(
                humanityEndpoint, "?lawfulBasisAttested=true",
                "{\"name\":\"Amara Okonkwo\",\"country\":\"NG\"}", agent);

        JsonNode result = mapper.readTree(json);
        assertEquals("MATCH_EXEMPT", result.path("outcome").asText());
        assertFalse(result.path("exemption").path("reason").asText().isBlank());
        assertEquals("OFAC", result.path("matches").path(0).path("register").asText());
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
