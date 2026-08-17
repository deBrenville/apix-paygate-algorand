package org.botstandards.onramp.tools;

import com.algorand.algosdk.account.Account;

/**
 * One-off local helper: generates a fresh Algorand account and prints its address + 25-word
 * mnemonic to YOUR console. TESTNET USE ONLY.
 *
 * <p>Run it yourself so the mnemonic never leaves your machine:
 * <pre>mvn -q compile exec:java -Dexec.mainClass=org.botstandards.onramp.tools.GenerateTestnetAccount</pre>
 *
 * <p>Then paste the address into the testnet dispenser to fund it, and put the mnemonic into a
 * git-ignored {@code .env} as {@code ONRAMP_AGENT_SENDER_MNEMONIC}. Never commit it.
 */
public final class GenerateTestnetAccount {

    private GenerateTestnetAccount() {}

    public static void main(String[] args) throws Exception {
        Account account = new Account();
        String address = account.getAddress().toString();
        String mnemonic = account.toMnemonic();

        System.out.println();
        System.out.println("=== Algorand TESTNET account (keep the mnemonic private) ===");
        System.out.println("Address : " + address);
        System.out.println("Mnemonic: " + mnemonic);
        System.out.println();
        System.out.println("Next:");
        System.out.println("  1) Fund this address with test ALGO: https://bank.testnet.algorand.network/");
        System.out.println("  2) Put into a git-ignored .env file:");
        System.out.println("       ONRAMP_AGENT_SENDER_MNEMONIC=\"" + "<the 25 words above>" + "\"");
        System.out.println("       ONRAMP_PAYTO_ADDRESS=\"" + address + "\"");
        System.out.println("     (payTo can be this same address — it pays itself in the demo.)");
        System.out.println();
    }
}
