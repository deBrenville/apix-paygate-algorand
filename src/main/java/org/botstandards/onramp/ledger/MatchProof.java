package org.botstandards.onramp.ledger;

import java.util.List;
import java.util.Map;

/**
 * Stateless screening result returned as match-proof (evidence, not a verdict): the neutral
 * ledger returns every per-register match with the public source record + a similarity score.
 * The caller keeps this as its own record; nothing is persisted server-side.
 */
public record MatchProof(
        String outcome,
        Map<String, String> query,
        List<ProofMatch> matches,
        Exemption exemption,
        String screenedAt,
        String listSnapshot) {

    /** One register's match: which list, which entry, how strong, and the public record. */
    public record ProofMatch(
            String register, String entryId, String strength, double score, Map<String, Object> sourceRecord) {}

    /** Present only when the humanity layer downgrades an OFAC-only match. */
    public record Exemption(String reason, String precedent) {}
}
