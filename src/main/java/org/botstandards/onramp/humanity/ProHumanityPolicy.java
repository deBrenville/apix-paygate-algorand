package org.botstandards.onramp.humanity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.botstandards.onramp.ledger.MatchProof;

/**
 * The BSF value layer: given the neutral ledger's match-proof, relieve OFAC-only designations of
 * humanity-serving subjects. A UN/EU/SECO match always stands (exemption relieves only
 * jurisdiction-specific OFAC-only listings). Stateless; provenance is passed through as evidence.
 */
@ApplicationScoped
public class ProHumanityPolicy {

    private final ProHumanityExemptions exemptions;

    @Inject
    public ProHumanityPolicy(ProHumanityExemptions exemptions) {
        this.exemptions = exemptions;
    }

    public MatchProof apply(MatchProof ledger) {
        if (ledger.matches().isEmpty()) {
            return ledger; // CLEAR — nothing to weigh
        }
        boolean hasNonOfac = ledger.matches().stream().anyMatch(m -> !"OFAC".equals(m.register()));
        if (hasNonOfac) {
            return withOutcome(ledger, "MATCH", null); // multi-jurisdiction listing stands
        }
        // OFAC-only: exempt iff the subject is humanity-serving.
        return exemptions.forName(ledger.query().get("name"))
                .map(ex -> withOutcome(ledger, "MATCH_EXEMPT", ex))
                .orElseGet(() -> withOutcome(ledger, "MATCH", null));
    }

    private MatchProof withOutcome(MatchProof p, String outcome, MatchProof.Exemption exemption) {
        return new MatchProof(outcome, p.query(), p.matches(), exemption, p.screenedAt(), p.listSnapshot());
    }
}
