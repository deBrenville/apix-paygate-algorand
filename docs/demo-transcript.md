# Demo transcript — captured live run

**Recorded:** 2026-07-31 · Algorand **testnet** · real **USDC** (ASA `10458941`) · no human in the loop.
**Command:** `./demo/run-demo.sh` (`mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true`)
**Runtime:** ~8 seconds.

This is a faithful transcript of the on-screen terminal output (the Maven/Quarkus boot lines that wrap
it are omitted). The six `BEAT` headers and the two `x402 settled` lines are the story — use them as
on-screen text or read them as voiceover. Full narration in [demo-caption-script.md](demo-caption-script.md).

---

```text
========================================================================
  BEAT 1 · DISCOVER — the agent searches the APIX registry by capability (no hardcoded URL)
------------------------------------------------------------------------
   search     : capability=compliance.sanctions.screen.humanity
   found      : http://localhost:8080/gw/humanity

========================================================================
  BEAT 2 · PAY-GATE (dynamic) — the agent calls without paying and gets a real 402
------------------------------------------------------------------------
   HTTP 402 — 0.03 USDC required (asset 10458941), payTo GVRXJLXF6OJLRIV46CRIXLG3DX3RJYBBAGRKNNYLHWOAHG57GKWHDH6ZAE

========================================================================
  BEAT 3 · SETTLE — the agent signs a USDC payment on Algorand for exactly those terms
------------------------------------------------------------------------
   x402 settled: route=humanity        amount=30000  tx=5WQORFBCGNT5NTIKUZCLLBALLSV7M5ZLRUY63PEICXWUGACASYXA
   x402 settled: route=sanctions-basic amount=10000  tx=3EOU2NXF254HLBYIHXHPXEXMWKG3QBTQL7S4DPBUTH2DCSWMAK3Q
   HTTP 200 — paid; both hops settled on-chain (see the 'x402 settled' lines above)

========================================================================
  BEAT 4 · CASCADE — B discovered and paid the neutral ledger A over x402 (second hop)
------------------------------------------------------------------------
   (server log above: a second 'x402 settled' line for the B->A hop)

========================================================================
  BEAT 5 · RESULT — neutral ledger + BSF humanity filter
------------------------------------------------------------------------
   outcome    : MATCH_EXEMPT
   provenance : register=OFAC  score=1.0
   exemption  : Represents an international-court prosecutor sanctioned by a single jurisdiction
                (US/OFAC) over an ICC investigation; not carried by the UN Security Council, the EU,
                or Switzerland. Humanity-serving institution under BSF pro-humanity policy.
   precedent  : EU Blocking Regulation (EC) No 2271/96

========================================================================
  BEAT 6 · ECONOMICS — value-add margin, machine to machine
------------------------------------------------------------------------
   agent paid B 0.03 USDC; B paid A 0.01 USDC; B margin = 0.02 USDC
   No account, no email, no OAuth, no CAPTCHA. Discovered, then paid.
========================================================================
```

## On-chain proof (this run)

Two real testnet settlements, verifiable in any Algorand explorer:

| Hop | Route | Amount | Transaction |
|---|---|---|---|
| ① agent → B | `humanity` | 0.03 USDC | [`5WQORFBC…ASYXA`](https://lora.algokit.io/testnet/transaction/5WQORFBCGNT5NTIKUZCLLBALLSV7M5ZLRUY63PEICXWUGACASYXA) |
| ② B → A | `sanctions-basic` | 0.01 USDC | [`3EOU2NXF…AK3Q`](https://lora.algokit.io/testnet/transaction/3EOU2NXF254HLBYIHXHPXEXMWKG3QBTQL7S4DPBUTH2DCSWMAK3Q) |

*Note: `payTo` and the tx IDs change every run; the structure (0.03 in, 0.01 out, 0.02 margin,
MATCH_EXEMPT) is stable.*
