package org.botstandards.onramp.bsm;

/** APIX Bot Service Manifest — the machine-readable discovery record for one wrapped route. */
public record Bsm(
        String service,
        String capability,
        String endpoint,
        Price price,
        Payment payment,
        Io io,
        String description) {

    public record Price(String amount, String asset, String network) {}

    public record Payment(String protocol, String scheme, String facilitator) {}

    public record Io(String requestSchemaRef, String responseSchemaRef) {}
}
