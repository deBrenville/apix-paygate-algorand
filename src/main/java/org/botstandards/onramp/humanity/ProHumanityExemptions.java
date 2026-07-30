package org.botstandards.onramp.humanity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.botstandards.apix.verification.sanctions.SanctionsMatcher;
import org.botstandards.onramp.ledger.MatchProof;

/** Curated pro-humanity exemption list: humanity-serving subjects relieved of OFAC-only designations. */
@ApplicationScoped
public class ProHumanityExemptions {

    private final Map<String, MatchProof.Exemption> byNormalizedName = new HashMap<>();

    public ProHumanityExemptions() {
        load();
    }

    private void load() {
        String path = "fixtures/pro-humanity-exemptions.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture: " + path);
            }
            List<Row> rows = new ObjectMapper().readValue(in, new TypeReference<List<Row>>() {});
            for (Row r : rows) {
                byNormalizedName.put(
                        SanctionsMatcher.normalize(r.name()),
                        new MatchProof.Exemption(r.reason(), r.precedent()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load " + path + ": " + e.getMessage(), e);
        }
    }

    /** The exemption for this subject name, if it is on the humanity list (name-normalized match). */
    public Optional<MatchProof.Exemption> forName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedName.get(SanctionsMatcher.normalize(name)));
    }

    private record Row(String name, String reason, String precedent) {}
}
