package org.botstandards.onramp.ledger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.botstandards.apix.verification.sanctions.SanctionsMatch;
import org.botstandards.apix.verification.sanctions.SanctionsMatcher;
import org.botstandards.apix.verification.sanctions.SanctionsOutcome;
import org.botstandards.apix.verification.sanctions.SanctionsSubject;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.ledger.SanctionsFixtures.LoadedEntry;

/**
 * Upstream A — the neutral global sanctions ledger. Stateless: it screens a subject against every
 * register with the real {@link SanctionsMatcher}, aggregates one match per register, and returns
 * the §3a match-proof. No exemption logic here (that is the humanity layer's job).
 *
 * <p>Private origin: only reachable via the gateway, which injects the shared forward secret.
 */
@Path("/internal/ledger")
public class BasicLedgerResource {

    @Inject
    SanctionsFixtures fixtures;

    @Inject
    OnrampConfig config;

    private final SanctionsMatcher matcher = new SanctionsMatcher();

    public record ScreenRequest(String name, String country) {}

    @POST
    @Path("/screen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response screen(@HeaderParam("X-Onramp-Forward") String forwardSecret, ScreenRequest req) {
        if (!config.internalForwardSecret().equals(forwardSecret)) {
            return Response.status(403).entity(Map.of("error", "forbidden")).build();
        }
        if (req == null || req.name() == null || req.name().isBlank()) {
            return Response.status(400).entity(Map.of("error", "name is required")).build();
        }

        SanctionsSubject subject = new SanctionsSubject(req.name(), req.country(), null, true);
        List<MatchProof.ProofMatch> matches = new ArrayList<>();

        for (String register : fixtures.registers()) {
            List<LoadedEntry> loaded = fixtures.loaded(register);
            List<org.botstandards.apix.verification.sanctions.SanctionsListEntry> entries =
                    loaded.stream().map(LoadedEntry::entry).toList();
            SanctionsMatch m = matcher.match(subject, entries);
            if (m.outcome() == SanctionsOutcome.CLEAR || m.matchedEntry() == null) {
                continue;
            }
            LoadedEntry hit = loaded.stream().filter(le -> le.entry() == m.matchedEntry()).findFirst().orElse(null);
            matches.add(new MatchProof.ProofMatch(
                    register,
                    hit != null ? hit.id() : null,
                    strength(m.outcome()),
                    m.score(),
                    hit != null ? hit.sourceRecord() : Map.of()));
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("name", req.name());
        query.put("country", req.country() == null ? "" : req.country());

        MatchProof proof = new MatchProof(
                matches.isEmpty() ? "CLEAR" : "MATCH",
                query,
                matches,
                null,
                Instant.now().toString(),
                "fixtures-v1");
        return Response.ok(proof).build();
    }

    private static String strength(SanctionsOutcome outcome) {
        return outcome == SanctionsOutcome.HIT_STRONG ? "STRONG" : "WEAK";
    }
}
