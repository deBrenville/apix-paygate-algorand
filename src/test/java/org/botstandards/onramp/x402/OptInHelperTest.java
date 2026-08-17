package org.botstandards.onramp.x402;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.AssetHolding;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Setup + diagnostics (run with -Dtest=OptInHelperTest): for each .env account (Agent A, Service B,
 * and — via the opt-in-only slot — Service C) prints its address, ALGO balance, and USDC opt-in/
 * balance, and opts it into the USDC testnet ASA when it has ALGO but is not yet opted in. Service C
 * is a pure receiver in the running server (no mnemonic there); its mnemonic is supplied ONLY for
 * this one-off opt-in via ONRAMP_SERVICE_C_OPTIN_MNEMONIC. Mnemonics are never printed.
 */
@QuarkusTest
class OptInHelperTest {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";
    private static final long USDC = 10_458_941L;

    @ConfigProperty(name = "onramp.agent.sender-mnemonic")
    Optional<String> agentMnemonic;

    @ConfigProperty(name = "onramp.service-b.sender-mnemonic")
    Optional<String> bMnemonic;

    /** Opt-in-only: Service C signs nothing in the server, so its mnemonic lives here just for setup. */
    @ConfigProperty(name = "onramp.service-c.optin-mnemonic")
    Optional<String> cMnemonic;

    @Test
    void statusAndOptIn() {
        Assumptions.assumeTrue(Boolean.getBoolean("x402.live"), "on-chain setup — run with -Dx402.live=true");
        Assumptions.assumeTrue(agentMnemonic.isPresent(), "set ONRAMP_AGENT_SENDER_MNEMONIC");
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        handle(algod, mnemonicAccount(agentMnemonic.get()), "agent");
        bMnemonic.ifPresent(m -> handle(algod, mnemonicAccount(m), "B"));
        cMnemonic.ifPresent(m -> handle(algod, mnemonicAccount(m), "C"));
    }

    private void handle(AlgodClient algod, Account account, String label) {
        try {
            var info = algod.AccountInformation(account.getAddress()).execute().body();
            long microAlgos = info != null && info.amount != null ? info.amount : 0L;
            AssetHolding usdc = info == null || info.assets == null ? null
                    : info.assets.stream().filter(a -> a.assetId != null && a.assetId == USDC).findFirst().orElse(null);
            String usdcStr = usdc != null
                    ? String.format("%.3f", usdc.amount.doubleValue() / 1_000_000.0) : "-";
            System.out.printf("[status] %-5s %s  ALGO=%.3f  USDC-optedIn=%s  USDC=%s%n",
                    label, account.getAddress(), microAlgos / 1_000_000.0, usdc != null, usdcStr);

            if (usdc == null && microAlgos > 200_000) {
                String txId = optIn(algod, account);
                System.out.printf("[opt-in] %-5s submitted USDC opt-in (tx %s)%n", label, txId);
            } else if (usdc == null) {
                System.out.printf("[opt-in] %-5s SKIPPED — needs ALGO first (fund via dispenser)%n", label);
            }
        } catch (Exception e) {
            System.out.printf("[error]  %-5s %s%n", label, e.getMessage());
        }
    }

    private String optIn(AlgodClient algod, Account account) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.AssetTransferTransactionBuilder()
                .sender(account.getAddress()).assetReceiver(account.getAddress())
                .assetIndex(USDC).assetAmount(0).suggestedParams(params).build();
        SignedTransaction signed = account.signTransaction(txn);
        String txId = algod.RawTransaction().rawtxn(Encoder.encodeToMsgPack(signed)).execute().body().txId;
        for (int i = 0; i < 12; i++) {
            Thread.sleep(1200);
            var p = algod.PendingTransactionInformation(txId).execute().body();
            if (p != null && p.confirmedRound != null && p.confirmedRound > 0) {
                break;
            }
        }
        return txId;
    }

    private static Account mnemonicAccount(String raw) {
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        try {
            return new Account(s.replaceAll("\\s+", " "));
        } catch (Exception e) {
            throw new IllegalStateException("bad mnemonic: " + e.getMessage(), e);
        }
    }
}
