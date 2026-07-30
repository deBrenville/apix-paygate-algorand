package org.botstandards.onramp.x402;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.botstandards.onramp.gateway.OnrampConfig;
import org.botstandards.onramp.gateway.UpstreamRegistry;

/**
 * Enforces x402 payment on {@code /gw/{route}} before the reverse proxy runs.
 *
 * <p>No/invalid {@code X-PAYMENT} → {@code 402} with the route's PaymentRequirements. A present
 * payment is verified and settled via the facilitator; only on success does the request continue.
 * Routes with {@code price-micros == 0} are free and skipped.
 */
@Provider
@PreMatching
public class X402GatewayFilter implements ContainerRequestFilter {

    private static final String GW_PREFIX = "gw/";

    @Inject
    UpstreamRegistry registry;

    @Inject
    PaymentRequirementsFactory requirementsFactory;

    @Inject
    FacilitatorClient facilitator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith(GW_PREFIX)) {
            return;
        }
        String route = path.substring(GW_PREFIX.length());
        Optional<OnrampConfig.Upstream> up = registry.byRoute(route);
        if (up.isEmpty()) {
            return; // let the resource return 404
        }
        OnrampConfig.Upstream cfg = up.get();

        // Loggable, non-PII data-processing attestation (audit trail in access logs). The subject
        // stays in the body; only this boolean rides in the URL where it is safe to log.
        if (cfg.requiresAttestation()) {
            String attested = ctx.getUriInfo().getQueryParameters().getFirst("lawfulBasisAttested");
            if (!"true".equalsIgnoreCase(attested)) {
                abortAttestation(ctx);
                return;
            }
        }

        if (cfg.priceMicros() <= 0) {
            return; // free route
        }

        Map<String, Object> requirements = requirementsFactory.forRoute(cfg);

        String xPayment = ctx.getHeaderString("X-PAYMENT");
        if (xPayment == null || xPayment.isBlank()) {
            abort(ctx, error402("payment required", requirements));
            return;
        }

        Map<String, Object> paymentPayload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(xPayment, Map.class);
            paymentPayload = parsed;
        } catch (Exception e) {
            abort(ctx, error402("malformed X-PAYMENT", requirements));
            return;
        }

        Map<String, Object> facReq = new LinkedHashMap<>();
        facReq.put("x402Version", 2);
        facReq.put("paymentPayload", paymentPayload);
        facReq.put("paymentRequirements", requirements);

        FacilitatorClient.VerifyResult verify = facilitator.verify(facReq);
        if (!verify.isValid()) {
            abort(ctx, error402("payment invalid: " + verify.reason(), requirements));
            return;
        }
        FacilitatorClient.SettleResult settle = facilitator.settle(facReq);
        if (!settle.success()) {
            abort(ctx, error402("settlement failed", requirements));
            return;
        }
        // Paid. Log the on-chain settlement (visible per hop in the demo) and expose the tx.
        io.quarkus.logging.Log.infof("x402 settled: route=%s amount=%s tx=%s",
                route, requirements.get("amount"), settle.txId());
        ctx.getHeaders().add("X-Onramp-Settled-Tx", settle.txId());
    }

    private Map<String, Object> error402(String message, Map<String, Object> requirements) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("x402Version", 2);
        body.put("error", message);
        body.put("accepts", List.of(requirements));
        return body;
    }

    private void abortAttestation(ContainerRequestContext ctx) {
        Map<String, Object> body = Map.of(
                "error", "data-processing attestation required",
                "hint", "add ?lawfulBasisAttested=true — you (the caller) are the controller with a lawful basis");
        try {
            ctx.abortWith(Response.status(422)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(mapper.writeValueAsString(body))
                    .build());
        } catch (Exception e) {
            ctx.abortWith(Response.status(422).build());
        }
    }

    private void abort(ContainerRequestContext ctx, Map<String, Object> body) {
        try {
            ctx.abortWith(Response.status(402)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(mapper.writeValueAsString(body))
                    .build());
        } catch (Exception e) {
            ctx.abortWith(Response.status(500).build());
        }
    }
}
