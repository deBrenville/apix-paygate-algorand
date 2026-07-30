package org.botstandards.onramp.x402;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Faucet-free demo asset (run with -Dtest=MintDemoAssetTest): the agent creates a demo ASA
 * ("dUSD", 6 decimals), opts B into it, and sends B a starting balance — so the whole cascade
 * can run without any external faucet. Prints the new asset id to put into price-asset-id.
 */
@QuarkusTest
class MintDemoAssetTest {

    private static final String ALGOD = "https://testnet-api.algonode.cloud";
    private static final long TOTAL = 1_000_000_000_000L;   // 1,000,000 dUSD at 6 decimals
    private static final long TO_B = 100_000_000L;           // 100 dUSD to B

    @ConfigProperty(name = "onramp.payer-mnemonic")
    Optional<String> agentMnemonic;

    @ConfigProperty(name = "onramp.b-payer-mnemonic")
    Optional<String> bMnemonic;

    @Test
    void mintDistributeAndReport() throws Exception {
        Assumptions.assumeTrue(agentMnemonic.isPresent() && bMnemonic.isPresent(),
                "need agent + B mnemonics");
        AlgodClient algod = new AlgodClient(ALGOD, 443, "");
        Account agent = account(agentMnemonic.get());
        Account b = account(bMnemonic.get());

        long assetId = createAsset(algod, agent);
        System.out.println("[mint] dUSD asset id = " + assetId + "  (set onramp.price-asset-id to this)");

        optIn(algod, b, assetId);
        System.out.println("[mint] B opted into dUSD");

        transfer(algod, agent, b.getAddress().toString(), assetId, TO_B);
        System.out.println("[mint] sent 100 dUSD agent -> B");
        System.out.println("[mint] DONE. agent=" + agent.getAddress() + "  B=" + b.getAddress());
    }

    private long createAsset(AlgodClient algod, Account agent) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.AssetCreateTransactionBuilder()
                .sender(agent.getAddress())
                .assetTotal(TOTAL)
                .assetDecimals(6)
                .defaultFrozen(false)
                .assetUnitName("dUSD")
                .assetName("Demo USD")
                .manager(agent.getAddress())
                .reserve(agent.getAddress())
                .suggestedParams(params)
                .build();
        String txId = submit(algod, agent.signTransaction(txn));
        PendingTransactionResponse p = waitFor(algod, txId);
        return p.assetIndex;
    }

    private void optIn(AlgodClient algod, Account account, long assetId) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.AssetTransferTransactionBuilder()
                .sender(account.getAddress()).assetReceiver(account.getAddress())
                .assetIndex(assetId).assetAmount(0).suggestedParams(params).build();
        waitFor(algod, submit(algod, account.signTransaction(txn)));
    }

    private void transfer(AlgodClient algod, Account from, String to, long assetId, long amount) throws Exception {
        TransactionParametersResponse params = algod.TransactionParams().execute().body();
        Transaction txn = Transaction.AssetTransferTransactionBuilder()
                .sender(from.getAddress())
                .assetReceiver(new com.algorand.algosdk.crypto.Address(to))
                .assetIndex(assetId).assetAmount(amount).suggestedParams(params).build();
        waitFor(algod, submit(algod, from.signTransaction(txn)));
    }

    private String submit(AlgodClient algod, SignedTransaction signed) throws Exception {
        return algod.RawTransaction().rawtxn(Encoder.encodeToMsgPack(signed)).execute().body().txId;
    }

    private PendingTransactionResponse waitFor(AlgodClient algod, String txId) throws Exception {
        for (int i = 0; i < 15; i++) {
            Thread.sleep(1200);
            PendingTransactionResponse p = algod.PendingTransactionInformation(txId).execute().body();
            if (p != null && p.confirmedRound != null && p.confirmedRound > 0) {
                return p;
            }
        }
        throw new IllegalStateException("tx " + txId + " not confirmed");
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
