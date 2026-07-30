package org.botstandards.onramp.humanity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.botstandards.onramp.ledger.MatchProof;
import org.botstandards.onramp.ledger.MatchProof.ProofMatch;
import org.junit.jupiter.api.Test;

/** Pure filter logic — no network. Exemption relieves only OFAC-only listings of humanity-serving subjects. */
class ProHumanityPolicyTest {

    private final ProHumanityPolicy policy = new ProHumanityPolicy(new ProHumanityExemptions());

    private static MatchProof ledger(String name, ProofMatch... matches) {
        return new MatchProof("MATCH", Map.of("name", name, "country", "XX"), List.of(matches), null, "t", "v");
    }

    private static ProofMatch match(String register) {
        return new ProofMatch(register, register.toLowerCase() + "-1", "STRONG", 1.0, Map.of("primaryName", "x"));
    }

    @Test
    void ofacOnlyExemptSubjectBecomesExempt() {
        MatchProof out = policy.apply(ledger("Amara Okonkwo", match("OFAC")));
        assertEquals("MATCH_EXEMPT", out.outcome());
        assertNotNull(out.exemption());
        assertNotNull(out.exemption().reason());
        assertNotNull(out.exemption().precedent());
        assertEquals("OFAC", out.matches().get(0).register()); // provenance passed through as evidence
    }

    @Test
    void ofacOnlyNonExemptSubjectStaysMatch() {
        MatchProof out = policy.apply(ledger("Dmitri Volkov", match("OFAC")));
        assertEquals("MATCH", out.outcome());
        assertNull(out.exemption());
    }

    @Test
    void anyNonOfacMatchStaysMatchEvenIfExempt() {
        MatchProof out = policy.apply(ledger("Amara Okonkwo", match("UN"), match("OFAC")));
        assertEquals("MATCH", out.outcome());
        assertNull(out.exemption());
    }

    @Test
    void noMatchesStayClear() {
        MatchProof clear = new MatchProof("CLEAR", Map.of("name", "John Smith", "country", "US"), List.of(), null, "t", "v");
        MatchProof out = policy.apply(clear);
        assertEquals("CLEAR", out.outcome());
    }
}
