package org.botstandards.onramp.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.botstandards.apix.verification.sanctions.SanctionsEntryType;
import org.botstandards.apix.verification.sanctions.SanctionsListEntry;

/**
 * Loads small curated sanctions-list fixtures grouped by register (UN/EU/SECO/OFAC).
 *
 * <p>Stands in for the full list import for the demo; the real basic ledger would swap these
 * fixtures for the imported feeds (the registry already has the parsers). Each entry keeps a
 * stable id and a public "source record" returned as match-proof evidence.
 */
@ApplicationScoped
public class SanctionsFixtures {

    /** Registers in order; each backed by a fixtures/<lower>-sample.json resource. */
    private static final List<String> REGISTERS = List.of("UN", "EU", "SECO", "OFAC");

    private final Map<String, List<LoadedEntry>> byRegister = new LinkedHashMap<>();

    public SanctionsFixtures() {
        ObjectMapper mapper = new ObjectMapper();
        for (String register : REGISTERS) {
            byRegister.put(register, load(mapper, register));
        }
    }

    private List<LoadedEntry> load(ObjectMapper mapper, String register) {
        String path = "fixtures/" + register.toLowerCase() + "-sample.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture: " + path);
            }
            List<FixtureEntry> raw = mapper.readValue(in, new TypeReference<List<FixtureEntry>>() {});
            List<LoadedEntry> loaded = new ArrayList<>();
            for (FixtureEntry fe : raw) {
                List<String> aliases = fe.aliases() == null ? List.of() : fe.aliases();
                SanctionsListEntry entry = new SanctionsListEntry(
                        register, SanctionsEntryType.valueOf(fe.type()), fe.primaryName(),
                        aliases, fe.country(), fe.lei());
                Map<String, Object> sourceRecord = new LinkedHashMap<>();
                sourceRecord.put("primaryName", fe.primaryName());
                sourceRecord.put("aliases", aliases);
                sourceRecord.put("country", fe.country());
                if (fe.lei() != null) {
                    sourceRecord.put("lei", fe.lei());
                }
                loaded.add(new LoadedEntry(fe.id(), entry, sourceRecord));
            }
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("failed to load fixture " + path + ": " + e.getMessage(), e);
        }
    }

    public List<String> registers() {
        return REGISTERS;
    }

    public List<LoadedEntry> loaded(String register) {
        return byRegister.getOrDefault(register, List.of());
    }

    /** Raw fixture shape as stored in JSON (register comes from the file, not the record). */
    public record FixtureEntry(
            String id, String type, String primaryName, List<String> aliases, String country, String lei) {}

    /** A loaded entry: its stable id, the matcher entry, and the public source record for proof. */
    public record LoadedEntry(String id, SanctionsListEntry entry, Map<String, Object> sourceRecord) {}
}
