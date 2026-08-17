# Algorand Testnet Wallet Setup (for the x402 demo)

You do this once. **No app store, no wallet app needed.** Claude never sees or commits your mnemonic.

## What we need at the end

- **One funded testnet account** (the "agent" that pays), holding **test ALGO**.
- Its **25-word mnemonic** in `ONRAMP_AGENT_SENDER_MNEMONIC` (a git-ignored `.env`), never committed.

That's it. The demo prices calls in **native ALGO** (`asset:"0"`), so there is **no USDC opt-in and no USDC faucet** — just test ALGO. (We can switch the price asset to USDCa later via config if you get test USDC easily; the "pay-per-call on Algorand" story holds either way.)

## Steps

1. **Generate a testnet account (you run this — the mnemonic stays on your machine):**
   ```bash
   mvn -q compile exec:java -Dexec.mainClass=org.botstandards.onramp.tools.GenerateTestnetAccount
   ```
   It prints an **Address** and a **25-word Mnemonic**.
   *(Alternative if you prefer a UI, no app store: the web wallet **https://lute.app** or the **Kibisis** browser extension — create a TestNet account and reveal its mnemonic.)*

2. **Fund it with test ALGO.**
   Open **https://bank.testnet.algorand.network/**, paste the Address from step 1, request ALGO. A few ALGO is plenty (covers fees + demo payments).

3. **Give me the mnemonic — safely.** Create `apix-x402-onramp/.env` (already git-ignored):
   ```
   ONRAMP_AGENT_SENDER_MNEMONIC="word1 word2 ... word25"
   ONRAMP_PAYTO_ADDRESS="<the address from step 1>"
   ```
   `.env`, `*.mnemonic`, and `secrets/` are git-ignored. I read the env var at runtime; I never print or commit it. (`payTo` can be the same address — it pays itself in the demo.)

## Why only ALGO?

- **ALGO** pays both the tiny network fees *and* the per-call price in the demo (native-asset x402).
- No ASA opt-in is required for native ALGO — that's the whole reason we avoid USDC for the demo.

Once `ONRAMP_AGENT_SENDER_MNEMONIC` is set and the account is funded, the spike (Task 2) and the live demo (Task 8) can run for real. Everything else is built in parallel against stubs.
