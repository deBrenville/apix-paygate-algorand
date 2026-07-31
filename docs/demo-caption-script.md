# Demo video — caption / voiceover script (~3 min)

**How to record:** run the one command below and screen-record the terminal. The six BEAT
headers and the two `x402 settled` server-log lines carry the story — you can read these captions
as voiceover, or drop them in as on-screen text. No editing required.

```bash
mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true
```

You (the presenter) only need: this repo, the funded testnet `.env`, and ~3 minutes.

---

## 0 · Title card (10s)
> **APIX x402 Onramp** — make any API discoverable *and* payable for AI agents, in one config step.
> A live demo on Algorand testnet. Real USDC. No human in the loop.

## BEAT 1 · Discover (25s)
> An autonomous agent needs a paid API. There's no human to sign up, enter a card, or click a CAPTCHA.
> It **searches the APIX registry by capability** — it knows only its goal, not a URL — and follows
> the links to the matching service's endpoint. Machine-native discovery, no docs, no portal.

## BEAT 2 · The pay-gate (20s)
> The agent calls the service. The gateway answers **HTTP 402 Payment Required** with the exact
> terms. This is the x402 protocol: pay first, then get the resource.

## BEAT 3 · Settle on-chain (30s)
> The agent signs a **USDC payment on Algorand** and retries. Watch the server log: **`x402 settled`**
> — a real on-chain transaction. And immediately a **second** `x402 settled` line…

## BEAT 4 · The cascade (25s)
> …because the service the agent paid (**B**, the BSF humanity layer) is *itself* an agent: it
> **discovered and paid** the neutral sanctions ledger **A** over x402. **Two paid hops, machine to
> machine.** This is composable agentic commerce.

## BEAT 5 · The result — ledger, not blind judge (35s)
> The neutral ledger reports the subject is on the **OFAC** list — and nothing else. The BSF humanity
> layer recognises an international-court prosecutor sanctioned by a single jurisdiction, not carried
> by the UN, EU, or Switzerland, and returns **`MATCH_EXEMPT`** — with the OFAC record kept as
> evidence and the precedent (EU Blocking Regulation 2271/96). **A ledger, not a blind judge.**

## BEAT 6 · Economics + close (25s)
> The agent paid 0.03 USDC; the humanity layer paid 0.01 to the ledger and kept a **0.02 margin** —
> value-add pricing, on-chain. **APIX brings discovery; x402/Algorand brings payment; the Onramp
> fuses them and composes services into paid, discoverable chains.**
> And this runs as a real product — any provider joins with one config entry.

## Optional closing card (10s)
> **Zero-code discoverable *and* payable.** APIX x402 Onramp.
