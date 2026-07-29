# Agentic Sanctions Gate — Design

**Date:** 2026-07-29
**Context:** WeAreDevelopers x402 Hackathon (Algorand Foundation). Submission deadline **2026-07-31, 23:00 CEST**. Jury-judged (no volume leaderboard). Prize pool $10,000.
**Judging criteria (evenly weighted):** (a) Use-case quality — x402 in the *core* payment flow, not a bolt-on; (b) Sustained potential; (c) Innovation.

## 1. Goal

A pay-per-call **sanctions-screening API that autonomous agents discover via an open manifest and pay for with x402 on Algorand** — no account, no email, no OAuth. The differentiator over a plain paid endpoint: **discovery + settlement**, both machine-native. An agent finds the service through an APIX Bot Service Manifest (BSM), learns the price from the manifest, pays per request via x402, and receives a **provenance-tagged** screening result.

**Non-goal:** promoting "the whole APIX project." The submission is one narrow, honest slice; a single deck slide places it as one service in a global agent-service index.

## 2. Why this is a strong fit

- x402 *is* the core flow: the product is a paywalled compliance check; payment is the point, not decoration (criterion a).
- Reuses the **real** APIX screening engine (`apix-verification` `SanctionsMatcher`) — a live, ongoing system, not a throwaway demo (criterion b).
- **Innovation (criterion c):** discovery-via-manifest + a **pro-humanity exemption layer** ("ledger, not blind judge"). No other entrant will have a values-aware, provenance-tagged sanctions gate.

## 3. Values constraint — pro-humanity sanctions policy (BINDING)

Per BSF standing decision (2026-07-27, `bsf-sanctions-policy-pro-humanity`): BSF is **not neutral** on sanctions lists. Organisations whose purpose serves humanity as a whole (international courts, humanitarian/multilateral institutions) do **not** fall under BSF-accepted sanctions, even if a single jurisdiction (e.g. the US via OFAC) lists them. Precedent: EU Blocking Regulation 2271/96.

**Consequence for this design:** OFAC is included as a *source*, but:
- every designation is returned **provenance-tagged** (which jurisdiction listed the subject), never as an automatic block;
- a **pro-humanity exemption** is applied: a subject that is OFAC-listed but flagged humanity-serving returns `MATCH_EXEMPT` with the exemption reason and full provenance recorded — not `REFUSED`.
- Worked example driving the demo: the ICC/ISGH-prosecutor case (US-sanctioned; not carried by EU, UN-SC, or Switzerland).

Full operationalisation (org-category taxonomy, review/override process) remains a deferred BSF task and is **out of scope** here; this design demonstrates the *principle* with one curated exemption, documented as such.

## 4. Architecture (all Quarkus / Java — single technology)

Four components; each independently understandable and testable.

1. **`agentic-sanctions-gate` service (new standalone Quarkus app)** — the hackathon repo; separate from the production `apix-registry` for neutrality/governance hygiene. Depends on the `apix-verification` library for the real `SanctionsMatcher` and sanctions value types. Loads **small curated list fixtures** (UN/EU/SECO samples already in the tree + a small OFAC fixture including the exemption example) into memory — no DB, no import pipeline needed for the demo.

2. **x402 payment filter (JAX-RS `ContainerRequestFilter`)** in front of `POST /v1/screen`:
   - No / invalid `X-PAYMENT` → `402 Payment Required` with x402 payment-requirements JSON (Algorand **testnet** USDCa, amount e.g. 0.05 USDCa, GoPlausible facilitator).
   - Valid `X-PAYMENT` → call GoPlausible facilitator REST (`/verify`, then `/settle`) → on success, proceed to screening.
   - Server side is language-agnostic HTTP to the facilitator — no Python/TS SDK needed.

3. **BSM manifest (static JSON, served at a well-known path)** — advertises capability `compliance.sanctions.screen`, price, x402 payment terms, endpoint, and request/response schema. This is the discovery layer = the APIX differentiator.

4. **Agent demo client (Java, Algorand Java SDK `algosdk`)** — an autonomous agent that: reads the BSM → `POST /v1/screen` (unpaid) → receives `402` → signs a testnet USDCa payment → retries with `X-PAYMENT` → receives the outcome → acts (proceed / block / proceed-with-exemption-note). This client run IS the demo video.

### Data flow

```
Agent → GET /.well-known/bsm (discover) 
      → POST /v1/screen {subject}          (no payment)
      ← 402 + payment-requirements (testnet USDCa, facilitator)
Agent → sign testnet USDCa txn (algosdk)
      → POST /v1/screen {subject} + X-PAYMENT
Gate  → facilitator /verify + /settle → SanctionsMatcher.screen()
      ← 200 { outcome: CLEAR | MATCH | MATCH_EXEMPT, provenance[], matchDetail }
Agent → decision
```

## 5. Network decision: Testnet

Testnet USDCa — no real funds, fast, and this jury track judges submitted materials (deck/video/repo/live URL), not on-chain volume. "Sustained potential" is addressed on a slide: mainnet is a config flip (facilitator supports both). *(The separate Global x402 Challenge would require mainnet volume — not this track.)*

## 6. Primary technical risk — spike FIRST

**Client-side x402/AVM payment encoding in Java.** GoPlausible ships client helpers in TS/Python; the `X-PAYMENT` payload for the AVM "exact" scheme must be built by hand in Java (signed Algorand asset-transfer txn + scheme-conformant encoding). This is the one underdocumented step and must be validated before anything else is built.

**Mitigation (keeps "one technology"):** if the Java client encoding becomes a time sink, only the **demo client** (not a judged artifact) may use a tiny TS/Python helper to produce the payload; the **service itself stays 100% Quarkus**. The graded product remains single-stack.

## 7. Build order (layered; each layer independently shippable)

- **Spike (first):** Java client produces a facilitator-accepted `X-PAYMENT` for a trivial testnet payment. Go/no-go on pure-Java client.
- **Layer 1 (must-have):** Quarkus service + x402 filter + `SanctionsMatcher` screening (UN/EU/SECO fixtures) + BSM + Java agent client + one-command demo runner.
- **Layer 2 (differentiator, time-boxed after L1 green):** OFAC fixture + provenance tagging + pro-humanity exemption + the ISGH worked example in the demo runner.
- **Always:** deck (5–6 slides), demo runner narration/caption script, README (English).

## 8. Deliverables → submission form

| Form field | Owner | Source |
|---|---|---|
| Project name / one-liner / description | Claude drafts, Carsten approves | this spec |
| Team members | Carsten | — |
| Pitch deck (5–6 slides) | Claude drafts | `docs/deck/` |
| Demo video (3–5 min) | **Carsten records** | one-command demo runner + caption script |
| GitHub repo URL | Claude builds (English) | this repo |
| Live demo URL | Claude deploys | Carsten's VPS / tunnel |
| X project profile | Carsten | — |

## 9. What only Carsten can provide (Claude's hard limits)

- Algorand **testnet wallet + testnet USDCa** from a faucet (Claude never touches keys/wallets — security rule).
- Recording the **video**; the **X profile handle**; **team details**; **submitting the form** (irreversible action).

## 10. Out of scope (YAGNI)

Mainnet; full OFAC/UN/EU/SECO list import + scheduled refresh; DB persistence; the full pro-humanity review/override process; multi-request billing tiers; production hardening beyond a reachable demo.
