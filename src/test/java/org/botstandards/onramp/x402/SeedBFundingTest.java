package org.botstandards.onramp.x402;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Setup helper (run with -Dtest=SeedBFundingTest): seeds humanity B with ALGO from the agent
 * (when the external dispenser is unavailable) and opts B into the USDC ASA. Testnet only,
 * user-requested demo setup. Mnemonics never printed.
 */
@QuarkusTest
class SeedBFundingTest {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";
    private static final long USDC = 10_458_941L;
    private static final long SEED_MICRO_ALGOS = 4_000_000L; // 4 ALGO

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @ConfigProperty(name = "onramp.b-payer-mnemonic")
    Optional<String> bMnemonic;

    @Test
    void seedAndOptInB() throws Exception {
        Assumptions.assumeTrue(agentMnemonic.isPresent() && bMnemonic.isPresent(),
                "need ONRAMP_PAYER_MNEMONIC + ONRAMP_B_PAYER_MNEMONIC");
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        Account agent = account(agentMnemonic.get());
        Account b = account(bMnemonic.get());

        long bAlgo = balance(algod, b.getAddress());
        System.out.printf("[seed] B %s starts with ALGO=%.3f%n", b.getAddress(), bAlgo / 1_000_000.0);

        if (bAlgo < 1_000_000) {
            String payTx = send(algod, agent, b.getAddress(), SEED_MICRO_ALGOS);
            System.out.printf("[seed] sent 4 ALGO agent->B (tx %s)%n", payTx);
        }

        String optTx = optInUsdc(algod, b);
        System.out.printf("[seed] B opted into USDC (tx %s)%n", optTx);
        System.out.printf("[seed] B now ALGO=%.3f%n", balance(algod, b.getAddress()) / 1_000_000.0);
    }

    private static long balance(AlgodClient algod, Address addr) throws Exception {
        var info = algod.AccountInformation(addr).execute().body();
        return info != null && info.amount != null ? info.amount : 0L;
    }

    private String send(AlgodClient algod, Account from, Address to, long micro) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.PaymentTransactionBuilder()
                .sender(from.getAddress()).receiver(to).amount(micro).suggestedParams(params).build();
        return submit(algod, from.signTransaction(txn));
    }

    private String optInUsdc(AlgodClient algod, Account account) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.AssetTransferTransactionBuilder()
                .sender(account.getAddress()).assetReceiver(account.getAddress())
                .assetIndex(USDC).assetAmount(0).suggestedParams(params).build();
        return submit(algod, account.signTransaction(txn));
    }

    private String submit(AlgodClient algod, SignedTransaction signed) throws Exception {
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

    private static Account account(String raw) {
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
