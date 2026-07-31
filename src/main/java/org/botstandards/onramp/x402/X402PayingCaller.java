package org.botstandards.onramp.x402;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Calls an x402-protected endpoint the honest way: POST once, and only if the endpoint answers
 * 402 does it read the payment terms FROM that 402 (payTo/amount/asset), sign a payment, and retry.
 * Nothing about the payment is known in advance — it is entirely 402-driven.
 */
@ApplicationScoped
public class X402PayingCaller {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final X402AvmClient x402 = new X402AvmClient();

    /** POST the JSON body to {@code endpoint+query}; pay via x402 if a 402 is returned; return the 200 body. */
    public String callPaid(String endpoint, String query, String bodyJson, Account payer) {
        try {
            String url = endpoint + query;
            HttpResponse<String> first = post(url, bodyJson, null);
            if (first.statusCode() / 100 == 2) {
                return first.body(); // free route
            }
            if (first.statusCode() != 402) {
                throw new IllegalStateException("unexpected " + first.statusCode() + " from " + url + ": " + first.body());
            }
            JsonNode accepts = mapper.readTree(first.body()).path("accepts").path(0);
            String payTo = accepts.path("payTo").asText();
            long amount = Long.parseLong(accepts.path("amount").asText());
            long asset = Long.parseLong(accepts.path("asset").asText());

            AlgodClient algod = new AlgodClient(ALGOD, 443, "");
            TransactionParametersResponse params = algod.TransactionParams().execute().body();
            String xPayment = x402.toHeaderValue(x402.buildPayload(payer, payTo, amount, asset, params));

            HttpResponse<String> paid = post(url, bodyJson, xPayment);
            if (paid.statusCode() / 100 != 2) {
                throw new IllegalStateException("paid call " + paid.statusCode() + " from " + url + ": " + paid.body());
            }
            return paid.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("x402 paid call failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> post(String url, String body, String xPayment) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (xPayment != null) {
            b.header("X-PAYMENT", xPayment);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
