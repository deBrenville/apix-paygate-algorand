package org.botstandards.onramp.humanity;

import com.algorand.algosdk.account.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.discovery.ApixDiscoveryClient;
import org.botstandards.onramp.ledger.MatchProof;
import org.botstandards.onramp.x402.X402PayingCaller;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Upstream B's outbound leg: discovers the neutral ledger (A) via the real APIX registry search
 * (by capability), then pays it over x402 — the payment terms come entirely from A's 402. B never
 * hardcodes A's URL or its payment address. Subject travels in the POST body only.
 */
@ApplicationScoped
public class UpstreamPayingClient {

    /** The capability B searches the registry for to find the neutral ledger. */
    private static final String LEDGER_CAPABILITY = "compliance.sanctions.ledger";

    @Inject
    ApixDiscoveryClient discovery;

    @Inject
    X402PayingCaller payingCaller;

    @ConfigProperty(name = "onramp.b-payer-mnemonic")
    Optional<String> bPayerMnemonic;

    private final ObjectMapper mapper = new ObjectMapper();

    public MatchProof screen(String name, String country) {
        String mnemonic = bPayerMnemonic
                .map(UpstreamPayingClient::unquote)
                .orElseThrow(() -> new IllegalStateException("ONRAMP_B_PAYER_MNEMONIC not set"));
        try {
            Account payer = new Account(mnemonic);
            String ledgerEndpoint = discovery.endpointByCapability(LEDGER_CAPABILITY);
            String body = mapper.writeValueAsString(
                    Map.of("name", name, "country", country == null ? "" : country));
            String responseJson = payingCaller.callPaid(
                    ledgerEndpoint, "?lawfulBasisAttested=true&purpose=sanctions-screening", body, payer);
            return mapper.readValue(responseJson, MatchProof.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cascade call to A failed: " + e.getMessage(), e);
        }
    }

    /** Strips surrounding quotes / normalizes whitespace from a .env mnemonic. Never logged. */
    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replaceAll("\\s+", " ");
    }
}
