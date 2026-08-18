package org.botstandards.onramp.humanity;

import com.algorand.algosdk.account.Account;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.botstandards.onramp.discovery.ApixDiscoveryClient;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.x402.X402PayingCaller;

/**
 * Upstream B's outbound leg — the server-side cascade payer. It discovers the inner service (A) via
 * the real APIX registry search (by capability), then pays it over x402: the payment terms come
 * entirely from A's 402 response, so B never hardcodes A's URL or its payment address. This is the
 * "server-side chaining" the demo showcases — one gated service consuming <em>and paying</em> another
 * in the background, on its own wallet ({@code ONRAMP_SERVICE_B_SENDER_MNEMONIC}).
 */
@ApplicationScoped
public class UpstreamPayingClient {

    /** The capability B searches the registry for to find the inner service. */
    private static final String INNER_CAPABILITY = "demo.hello.inner";

    @Inject
    ApixDiscoveryClient discovery;

    @Inject
    X402PayingCaller payingCaller;

    @Inject
    OnrampConfig config;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Discover the inner service by capability and settle its x402 price on B's own wallet, returning
     * the inner service's raw JSON response ("Hello World A"). The demo content is irrelevant; what the
     * cascade proves is that this hop is a real, gated, on-chain-settled call B makes on its own.
     */
    public Map<String, Object> callInner() {
        String mnemonic = config.serviceB().senderMnemonic()
                .map(UpstreamPayingClient::unquote)
                .orElseThrow(() -> new IllegalStateException("ONRAMP_SERVICE_B_SENDER_MNEMONIC not set"));
        try {
            Account payer = new Account(mnemonic);
            String innerEndpoint = discovery.endpointByCapability(INNER_CAPABILITY);
            String body = mapper.writeValueAsString(Map.of("name", "hello", "country", ""));
            String responseJson = payingCaller.callPaid(
                    innerEndpoint, "?lawfulBasisAttested=true&purpose=demo", body, payer);
            return mapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cascade call to inner service A failed: " + e.getMessage(), e);
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
