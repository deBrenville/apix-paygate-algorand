package org.botstandards.onramp.x402;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an x402 "exact" payment for the Algorand Virtual Machine (client side).
 *
 * <p>Native-ALGO, no fee abstraction: one signed payment transaction, {@code paymentIndex 0},
 * the payer covers its own network fee. See {@code docs/x402-wire-notes.md}.
 */
public class X402AvmClient {

    /** Algorand TestNet CAIP-2 network identifier. */
    public static final String TESTNET_CAIP2 = "algorand:SGO1GKSzyE7IEPItTxCByw9x8FmnrCDexi9/cOUJOiI=";

    private static final int X402_VERSION = 2;
    private static final String SCHEME = "exact";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Builds and signs an ASA (asset-transfer) payment and returns the x402 {@code payload}
     * object (already containing the base64(msgpack(signedTxn))). The GoPlausible facilitator
     * requires an {@code axfer} — a plain ALGO {@code pay} is rejected.
     */
    public Map<String, Object> buildPayload(
            Account payer,
            String payTo,
            long amount,
            long assetId,
            TransactionParametersResponse params)
            throws Exception {
        Transaction txn = Transaction.AssetTransferTransactionBuilder()
                .sender(payer.getAddress())
                .assetReceiver(new Address(payTo))
                .assetIndex(assetId)
                .assetAmount(amount)
                .noteUTF8("x402-payment-v2")
                .suggestedParams(params)
                .build();

        SignedTransaction signed = payer.signTransaction(txn);
        String b64 = Base64.getEncoder().encodeToString(Encoder.encodeToMsgPack(signed));

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("paymentGroup", List.of(b64));
        inner.put("paymentIndex", 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("x402Version", X402_VERSION);
        payload.put("scheme", SCHEME);
        payload.put("network", TESTNET_CAIP2);
        payload.put("payload", inner);
        return payload;
    }

    /** The PaymentRequirements a resource server would advertise for this payment (native ALGO). */
    public Map<String, Object> paymentRequirements(String payTo, long amountMicros, String asset) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("scheme", SCHEME);
        req.put("network", TESTNET_CAIP2);
        req.put("asset", asset);
        req.put("amount", Long.toString(amountMicros));
        req.put("payTo", payTo);
        req.put("maxTimeoutSeconds", 60);
        req.put("extra", Map.of("decimals", 6));
        return req;
    }

    /** Assembles the facilitator /verify and /settle request body ({@code paymentPayload} + {@code paymentRequirements}). */
    public Map<String, Object> facilitatorRequest(
            Map<String, Object> payload, Map<String, Object> requirements) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("x402Version", X402_VERSION);
        body.put("paymentPayload", payload);
        body.put("paymentRequirements", requirements);
        return body;
    }

    /** Serializes the payload to the raw-JSON value carried in the {@code X-PAYMENT} header. */
    public String toHeaderValue(Map<String, Object> payload) throws Exception {
        return mapper.writeValueAsString(payload);
    }
}
