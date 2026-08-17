package org.botstandards.onramp.humanity;

import com.algorand.algosdk.account.Account;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.jboss.logging.Logger;

/**
 * Startup guard for Service B's wallet. Service B settles hop 2 (B → Service C) by signing with
 * {@code ONRAMP_SERVICE_B_SENDER_MNEMONIC}, while Agent A pays hop 1 to the public address in
 * {@code ONRAMP_SERVICE_B_SENDER_ADDRESS}. Those two are, by design, the same wallet — this check
 * enforces that: it derives the address from the mnemonic and fails startup loudly on any mismatch,
 * rather than letting Agent A pay one address while Service B signs with another and the cascade
 * breaks mid-call in a live demo.
 *
 * <p>The redundancy between address and mnemonic is deliberate (the address stays verifiable in the
 * {@code .env} without touching the secret); this guard is what makes that redundancy safe.
 *
 * <p>Skips silently when no mnemonic is set (local build / tests). Presence in production is enforced
 * separately by the deploy pre-flight. Only the derived public address is ever logged — never the mnemonic.
 */
@ApplicationScoped
public class ServiceBWalletCheck {

    private static final Logger LOG = Logger.getLogger(ServiceBWalletCheck.class);

    @Inject
    OnrampConfig config;

    void onStart(@Observes StartupEvent ev) throws Exception {
        Optional<String> senderMnemonic = config.serviceB().senderMnemonic();
        if (senderMnemonic.isEmpty() || senderMnemonic.get().isBlank()) {
            LOG.warn("Service B sender-mnemonic not set (ONRAMP_SERVICE_B_SENDER_MNEMONIC) — "
                    + "cascade hop 2 will fail until it is. Skipping wallet consistency check.");
            return;
        }
        String derived = new Account(unquote(senderMnemonic.get())).getAddress().toString();
        String configured = config.serviceB().senderAddress().trim();
        if (!derived.equals(configured)) {
            throw new IllegalStateException(
                    "Service B wallet mismatch: ONRAMP_SERVICE_B_SENDER_MNEMONIC derives address "
                    + derived + " but ONRAMP_SERVICE_B_SENDER_ADDRESS is " + configured
                    + ". Agent A would pay one address while Service B signs with another. "
                    + "Fix the .env so both name the same wallet.");
        }
        LOG.infof("Service B wallet verified: sender-address matches the signing mnemonic (%s).", derived);
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
