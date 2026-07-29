# APIX x402 Onramp — Design

**Working title:** APIX x402 Onramp (product) · **Agentic Sanctions Gate** (flagship instance)
**Date:** 2026-07-29
**Context:** WeAreDevelopers x402 Hackathon (Algorand Foundation). Submission deadline **2026-07-31, 23:00 CEST**. Jury-judged (no volume leaderboard). Prize pool $10,000.
**Judging criteria (evenly weighted):** (a) Use-case quality — x402 in the *core* payment flow; (b) Sustained potential / path to adoption; (c) Innovation.

## 1. Product & thesis

**Product:** a reusable **wrapper (API gateway)** that takes *any* existing HTTP API and, in one config step, makes it both **discoverable in the APIX agent index (BSM) and payable via x402 on Algorand** — with **zero paywall code** on the provider's side.

**Thesis / the multiplier:** payment on Algorand is already solved (GoPlausible ships x402 middleware). What Algorand's ecosystem lacks — and what only APIX brings — is **discovery**. The Onramp fuses the two into one zero-integration step. The value is **"zero-code discoverable *and* payable,"** not "easy payments." We build **on top of** the GoPlausible facilitator, not against it.

**The sanctions service is a PoC instance, not the product.** It is one upstream the Onramp fronts. A trivially-different second upstream, wrapped live in ~30 seconds, proves the genericity — that is the "jaw-drop" beat for the judges.

**Honest value boundary:** for a greenfield service already in a supported framework (FastAPI/Hono), GoPlausible middleware + a self-hosted manifest covers payment; for them the Onramp's marginal value is **discovery + no-redeploy hosting**. For legacy / any-stack / network-isolated origins, it is the whole package. Say this on the deck.

## 2. Trust boundary — how the paywall is actually enforced

The Onramp is a **reverse-proxy gateway**: all public traffic hits the Onramp, which enforces `402`/verify/settle and forwards **only paid** requests to the origin. The provider writes **no paywall logic** (this is the point — otherwise we add nothing over GoPlausible middleware).

For the paywall to protect revenue, the origin must be reachable **only** by the Onramp (else an agent bypasses it and calls the origin directly, unpaid). Three enforcement options, transparently presented:

1. **Network allowlist / private network** — origin not public; only the Onramp's egress IP allowed. *True zero-code*, requires the provider to control network placement.
2. **Signed forward header (shared secret)** — origin may stay public but rejects any request lacking the secret/signature the Onramp injects. *~1 line* at the origin; works anywhere. **← PoC default.**
3. **Co-host / sidecar** — origin bound to localhost behind the Onramp.

**"100% zero-code" holds only for option 1** (provider controls the network); option 2 is a one-header check. This bound goes on the deck to survive Q&A.

**Second trust fact (deck):** the Onramp sees request/response in cle­artext (for sanctions: the screened subject). Acceptable for a **neutral APIX/BSF operator** (the "Kontor" refining-intermediary model), but it is a stated trust assumption.

## 3. Values constraint — pro-humanity sanctions policy (BINDING, instance-level)

Per BSF standing decision (2026-07-27, `bsf-sanctions-policy-pro-humanity`): BSF is **not neutral** on sanctions lists. Organisations serving humanity as a whole (international courts, humanitarian/multilateral institutions) do **not** fall under BSF-accepted sanctions even if a single jurisdiction (e.g. the US via OFAC) lists them. Precedent: EU Blocking Regulation 2271/96.

Applies to the **sanctions instance**, not the generic Onramp:
- OFAC is included as a *source*, every designation returned **provenance-tagged** (which jurisdiction listed the subject), never an automatic block;
- a **pro-humanity exemption** returns `MATCH_EXEMPT` (with reason + full provenance) instead of `REFUSED` for a humanity-serving subject;
- worked example driving the demo: the ICC/ISGH-prosecutor case (US-sanctioned; not carried by EU, UN-SC, or Switzerland).

Full operationalisation (org-category taxonomy, override process) is a deferred BSF task, **out of scope**; this demonstrates the *principle* with one curated exemption, documented as such.

## 4. Architecture (all Quarkus / Java — single technology)

1. **`apix-x402-onramp` service (new standalone Quarkus app)** — the hackathon repo; separate from production `apix-registry` for neutrality/governance hygiene. Generic reverse-proxy gateway parameterised by a per-upstream config entry:
   `{ route, upstream_url, forward_secret, price, capability, schema_ref }`.
   For each configured upstream it exposes a public paywalled route, injects the forward secret to the origin, and publishes a BSM.

2. **x402 payment filter (JAX-RS `ContainerRequestFilter`)**:
   - No / invalid `X-PAYMENT` → `402` with x402 payment-requirements JSON (Algorand **testnet** USDCa, e.g. 0.05 USDCa, GoPlausible facilitator).
   - Valid `X-PAYMENT` → GoPlausible facilitator REST (`/verify`, then `/settle`) → on success, reverse-proxy to `upstream_url` with the signed forward header.
   - Server side is language-agnostic HTTP to the facilitator — no Python/TS SDK needed.

3. **BSM publisher** — per upstream, generates + serves the APIX Bot Service Manifest (`/.well-known/…`) advertising `capability`, price, x402 terms, endpoint, and I/O schema. This is the discovery layer = the APIX-unique differentiator.

4. **Flagship upstream: Agentic Sanctions Gate** — a thin origin that calls the **real `SanctionsMatcher` from `apix-verification`** over small curated list fixtures (UN/EU/SECO samples already in the tree + a small OFAC fixture incl. the exemption case). Returns `CLEAR | MATCH | MATCH_EXEMPT` with provenance. No DB / import pipeline for the demo.

5. **Second upstream (genericity proof)** — a throwaway origin (e.g. a "premium summarize/echo" endpoint), wrapped by adding one config entry, demoed live to show the Onramp generalises.

6. **Agent demo client (Java, Algorand Java SDK `algosdk`)** — reads the BSM → calls the route (unpaid) → gets `402` → signs a testnet USDCa payment → retries with `X-PAYMENT` → receives the outcome → acts. This run IS the demo video.

### Data flow

```
Agent → GET BSM (discover route + price + x402 terms)
      → POST <route> {payload}                      (no payment)
      ← 402 + payment-requirements (testnet USDCa, facilitator)
Agent → sign testnet USDCa txn (algosdk)
      → POST <route> {payload} + X-PAYMENT
Onramp→ facilitator /verify + /settle → reverse-proxy to upstream (+ forward secret)
Upstream (sanctions instance) → SanctionsMatcher.screen()
      ← 200 { outcome, provenance[], matchDetail }
Agent → decision
```

## 5. Network decision: Testnet

Testnet USDCa — no real funds, fast; this jury track judges submitted materials, not on-chain volume. "Sustained potential" addressed on a slide: mainnet is a config flip (facilitator supports both). *(The separate Global x402 Challenge would require mainnet volume — not this track.)*

## 6. Primary technical risk — spike FIRST

**Client-side x402/AVM payment encoding in Java.** GoPlausible ships client helpers in TS/Python; the `X-PAYMENT` payload for the AVM "exact" scheme must be built by hand in Java (signed Algorand asset-transfer txn + scheme-conformant encoding). One underdocumented step — validate before building anything else.

**Mitigation (keeps "one technology"):** if the Java client encoding stalls, only the **demo client** (not a judged artifact) may use a tiny TS/Python helper to produce the payload; the **service itself stays 100% Quarkus**.

## 7. Build order (layered; each layer independently shippable)

- **Spike (first):** Java client produces a facilitator-accepted `X-PAYMENT` for a trivial testnet payment. Go/no-go on the pure-Java client.
- **Layer 1 (must-have):** Quarkus Onramp + x402 filter + reverse-proxy + BSM publisher + sanctions upstream (UN/EU/SECO fixtures) + Java agent client + one-command demo runner.
- **Layer 2 (differentiator, time-boxed):** OFAC fixture + provenance + pro-humanity exemption + ISGH worked example; the **second upstream** wrapped live.
- **Always:** deck (5–6 slides), demo-runner narration/caption script, README (English).

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

Mainnet; full sanctions-list import + scheduled refresh; DB persistence; the full pro-humanity review/override process; a self-service tenant/onboarding portal (onboarding = config entry / admin call only); multi-request billing tiers; production hardening beyond a reachable demo.

## 11. Demo narrative (the video, 3–5 min)

1. **Problem (15s):** an autonomous agent needs a paid API — no human to sign up, enter a card, click a CAPTCHA.
2. **Discover (30s):** agent reads the APIX BSM, learns the route + price + Algorand payment terms — machine-native, no docs.
3. **Pay & screen (60s):** call → `402` → agent signs testnet USDCa → retry → sanctions result. The core x402 flow.
4. **Values beat (45s):** OFAC match → `MATCH_EXEMPT` under BSF pro-humanity policy, provenance recorded — "ledger, not blind judge."
5. **Multiplier beat (45s):** wrap a *second*, unrelated API in ~30 seconds of config → instantly discoverable + payable. This is the point: any provider, one step, onto Algorand + into the agent index.
6. **Close (15s):** APIX brings discovery; x402/Algorand brings payment; the Onramp fuses them.
