# Algorand Testnet Wallet Setup (for the x402 demo)

You do this once. Claude never sees or commits your mnemonic.

## What we need at the end

- **One funded testnet account** (the "agent" that pays): holds test ALGO + test USDCa.
- **A `payTo` address** that has opted into the USDC ASA (can be the *same* account paying itself, which is the simplest for a demo).
- The paying account's **25-word mnemonic**, placed in `ONRAMP_PAYER_MNEMONIC` (env var or an untracked file) — never committed.

## Steps

1. **Create a testnet account.**
   - Easiest: install **Pera Wallet** (mobile/desktop), create a new account, and switch the network to **TestNet** (Settings → Node Settings → TestNet).
   - Save the 25-word mnemonic somewhere private.

2. **Fund it with test ALGO.**
   - Open the official dispenser: **https://bank.testnet.algorand.network/** (or **https://dispenser.testnet.aws.algodev.network/**).
   - Paste your account address, request ALGO. A few ALGO is plenty (covers transaction fees).

3. **Opt into USDC (testnet ASA) and get some.**
   - Testnet USDC is an ASA. In Pera: **Add asset → search "USDC" → opt in** (this sends a 0-amount opt-in txn; needs a little ALGO, which you now have).
   - Get test USDC from a testnet USDC faucet (e.g. the **Circle testnet faucet** for Algorand, or a community dispenser). Even ~1–2 USDC is enough — each screening call costs ~0.05.
   - *Note:* the exact USDC testnet **ASA ID** I will pin during the spike against `facilitator.goplausible.xyz/docs` and put it in `application.yaml`. You don't need to look it up — just opt into "USDC" in Pera on TestNet.

4. **Hand me the mnemonic — safely.**
   - Put it in a file the repo ignores, e.g. create `apix-x402-onramp/.env` with:
     ```
     ONRAMP_PAYER_MNEMONIC="word1 word2 ... word25"
     ONRAMP_PAYTO_ADDRESS="<your account address>"
     ```
   - `.env`, `*.mnemonic`, and `secrets/` are already git-ignored. I read the env var at runtime; I never print it or commit it.

## Why both ALGO and USDC?

- **ALGO** pays the tiny network transaction fees.
- **USDC (USDCa)** is what the agent actually pays the service per call, via x402.

That's it. Once `ONRAMP_PAYER_MNEMONIC` is set and the account is funded, the spike (Task 2) and the live demo (Task 8) can run for real. Everything else I build in parallel against stubs.
