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

## 3. Micro-service cascade — neutral ledger + separate humanity layer

The screening is split into **two chained services**, both wrapped by the Onramp. This cleanly isolates the BSF values decision and is the demo's headline (service chaining + value-add pricing over x402).

**Service A — Basic Global Sanctions Ledger (neutral, stateless).** Loads *all* lists (UN/EU/SECO/OFAC) **unfiltered** and answers with the ledger only — no value judgment. Returning OFAC data here is **not** a values decision: it neutrally relays a public list ("ledger, not judge"). Response = the match-proof shape in §3a. Price e.g. 0.01 USDC.

**Service B — BSF Humanity Layer (value-add, stateless).** Acts as an x402 *client*: discovers Service A via its BSM, pays it, receives the ledger result, then applies the **pro-humanity filter** and returns the enriched result. Price e.g. 0.03 USDC (0.01 cost to A + 0.02 margin for the exemption intelligence — shown live in the demo).

Per BSF standing decision (2026-07-27, `bsf-sanctions-policy-pro-humanity`): BSF is **not neutral** on sanctions lists. Organisations serving humanity as a whole (international courts, humanitarian/multilateral institutions) do **not** fall under BSF-accepted sanctions even if a single jurisdiction (e.g. the US via OFAC) lists them. Precedent: EU Blocking Regulation 2271/96. Service B operationalises this:
- if a match's **only** register is OFAC **and** the subject is on the curated humanity-exemption list → downgrade that match to exempt;
- if, after that, **no** non-exempt match remains → outcome `MATCH_EXEMPT` (with reason + the underlying provenance passed through from A); a remaining UN/EU/SECO match stays `MATCH`;
- worked example driving the demo: the ICC/ISGH-prosecutor case (US-sanctioned; not carried by EU, UN-SC, or Switzerland).

Full operationalisation (org-category taxonomy, override process) is a deferred BSF task, **out of scope**; this demonstrates the *principle* with one curated exemption.

### 3a. Stateless + match-proof (both services)

Both services are **stateless**: a match is computed and returned, **never persisted or logged**. The only state is the loaded sanctions-list entries (input data). Rationale (see legal analysis): the record-keeping duty (OFAC 31 CFR 501.601 · EU AMLD Art. 40 · FATF R.11, all ~5y) binds the *obligated caller*, not a stateless lookup tool; statelessness also avoids becoming a GDPR controller of screened subjects' PII.

The response returns the **source data as match-proof** so the caller can keep its own record — framed as **evidence, not a verdict** (returning public list data + a similarity score; the caller makes the final determination):

```json
{ "outcome": "CLEAR | MATCH | MATCH_EXEMPT",
  "query": { "name": "...", "country": "..." },
  "matches": [ { "register": "UN|EU|SECO|OFAC", "entryId": "...", "strength": "STRONG|WEAK",
                 "score": 0.97, "sourceRecord": { "primaryName": "...", "aliases": [], "country": "...", "listRef": "..." } } ],
  "exemption": null,
  "screenedAt": "<timestamp>", "listSnapshot": "<version>" }
```

DSGVO note: the source records are **already published by the authorities for exactly this purpose**; company records are largely outside GDPR; person records are covered but justified by Art. 6(1)(c)/(f) + public-source + purpose-consistency. The real risk is a **false positive**, mitigated by evidence-framing + the score (never asserting the query subject *is* the listed person).

**Transport invariant (PII):** the screened subject travels **only in the POST request body**, never in query parameters or the URL path. Query strings leak into access/proxy/CDN logs, `Referer` headers, and browser history — which would undermine statelessness and data-minimisation. Keeping the subject in the body means default access logs (which record method + URL, not the body) stay PII-free. The gateway sees the body in cleartext (the Kontor trust point); mitigations: TLS in transit, no request-body logging, nothing persisted.

## 4. Architecture (all Quarkus / Java — single technology)

1. **`apix-x402-onramp` service (new standalone Quarkus app)** — the hackathon repo; separate from production `apix-registry` for neutrality/governance hygiene. Generic reverse-proxy gateway parameterised by a per-upstream config entry:
   `{ route, upstream_url, forward_secret, price, capability, schema_ref }`.
   For each configured upstream it exposes a public paywalled route, injects the forward secret to the origin, and publishes a BSM.

2. **x402 payment filter (JAX-RS `ContainerRequestFilter`)**:
   - No / invalid `X-PAYMENT` → `402` with x402 payment-requirements JSON (Algorand **testnet** USDCa, e.g. 0.05 USDCa, GoPlausible facilitator).
   - Valid `X-PAYMENT` → GoPlausible facilitator REST (`/verify`, then `/settle`) → on success, reverse-proxy to `upstream_url` with the signed forward header.
   - Server side is language-agnostic HTTP to the facilitator — no Python/TS SDK needed.

3. **BSM publisher** — per upstream, generates + serves the APIX Bot Service Manifest (`/.well-known/…`) advertising `capability`, price, x402 terms, endpoint, and I/O schema. This is the discovery layer = the APIX-unique differentiator.

4. **Upstream A — Basic Global Sanctions Ledger** — a thin, stateless origin that calls the **real `SanctionsMatcher` from `apix-verification`** over curated list fixtures (UN/EU/SECO samples already in the tree + an OFAC fixture incl. the ISGH case). Returns the §3a match-proof (`CLEAR | MATCH`, no exemption logic). Neutral. Production path: swap fixtures for the full list import (registry already has the parsers).

5. **Upstream B — BSF Humanity Layer** — a stateless origin that is *itself* an x402 **client**: it discovers Upstream A via its BSM, pays A over x402 (settling on-chain), receives A's ledger result, applies the pro-humanity filter (§3), and returns the enriched result (`CLEAR | MATCH | MATCH_EXEMPT`) with A's provenance passed through. Uses `X402AvmClient` (proven in the spike) with **B's own funded testnet account**.

6. **Agent demo client (Java, `algosdk`)** — discovers **B** via BSM → calls unpaid → `402` → signs a testnet USDC payment → retries with `X-PAYMENT` → receives the enriched outcome → acts. This run IS the demo video. The margin (B charges 0.03, pays A 0.01) is surfaced in the output.

### Data flow (the cascade — two x402 hops over one Onramp)

```
Agent → GET BSM(B) discover
      → POST /gw/humanity {name,country}                       (no payment)
      ← 402  (0.03 USDC, facilitator)
Agent → sign USDC axfer → POST /gw/humanity + X-PAYMENT
Onramp→ verify+settle (agent→B) → proxy to Upstream B
  B   → GET BSM(A) discover → POST /gw/sanctions (no payment) ← 402 (0.01 USDC)
      → sign USDC axfer (B's account) → POST /gw/sanctions + X-PAYMENT
  Onramp→ verify+settle (B→A) → proxy to Upstream A
    A → SanctionsMatcher.match() per register → §3a match-proof
  B   → apply pro-humanity filter → enriched match-proof
Onramp← 200 enriched result → Agent → decision
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

- **Three testnet accounts** (Claude never touches keys/wallets — security rule):
  - **Agent** — signs payment to B. `.env`: `ONRAMP_AGENT_SENDER_MNEMONIC`. Funded: USDC + ALGO, opted into USDC.
  - **B (BSF Humanity)** — receives from agent, signs payment to A. `.env`: `ONRAMP_SERVICE_B_SENDER_MNEMONIC` (its payTo = its own address, derived). Funded: USDC (buffer) + ALGO, opted in.
  - **A (Basic Ledger)** — receiver only, no signing in-app. `.env`: `ONRAMP_SERVICE_C_RECEIVER_ADDRESS` (address only). Needs opt-in + a little ALGO, **no USDC**.
  - The APIX wrapper holds no funds in the core demo; an APIX-fee recipient address (`ONRAMP_APIX_PAYTO_ADDRESS`) is only needed if the platform fee is routed on-chain (stretch).
- Recording the **video**; the **X profile handle**; **team details**; **submitting the form** (irreversible action).

## 10. Out of scope (YAGNI)

Mainnet; full sanctions-list import + scheduled refresh; DB persistence; the full pro-humanity review/override process; a self-service tenant/onboarding portal (onboarding = config entry / admin call only); multi-request billing tiers; production hardening beyond a reachable demo.

## 11. Demo narrative (the video, 3–5 min)

1. **Problem (15s):** an autonomous agent needs a paid API — no human to sign up, enter a card, click a CAPTCHA.
2. **Discover (30s):** agent reads the APIX BSM for the humanity service, learns route + price + Algorand terms — machine-native, no docs.
3. **Pay & screen (50s):** call → `402` → agent signs testnet USDC → retry → result. The core x402 flow.
4. **Cascade beat (50s):** reveal that the humanity service itself *discovered and paid* the neutral basic ledger over x402 — a second on-chain hop — and kept a margin (charges 0.03, pays 0.01). Service chaining + value-add pricing, machine to machine.
5. **Values beat (45s):** the basic ledger neutrally reports an OFAC match (ledger, no judge); the humanity layer downgrades it to `MATCH_EXEMPT` (ISGH case), passing through the provenance as evidence — the value-add made visible.
6. **Close (20s):** APIX brings discovery; x402/Algorand brings payment; the Onramp fuses them and composes services into paid, discoverable chains — and this runs as a real product, not a mock.

## 12. Economics — how each layer earns

Layered value, each priced in its own natural unit:

```
Netz-Gas (ALGO, ~0.001/txn)  <  A: ledger price  <  B: humanity margin  <  APIX: platform fee
```

**No separate ALGO fee (decided — counterproductive).** Reasons: (1) it forces the agent to hold/spend *two* assets per call, breaking the single-stablecoin simplicity; (2) the amounts are so small that collecting a micro-ALGO fee costs about as much ALGO in gas as it yields; (3) in the simple (no-fee-abstraction) model each signer pays its own gas directly, so the wrapper is not out-of-pocket for gas and has nothing to recover. **Gas stays a network cost, not a business line. The wrapper earns in USDC, not ALGO.**

**How APIX (the wrapper) earns:**
- **Primary — per-call platform fee in USDC, taken inline at settlement.** The Onramp is the payment-enforcement point, so it takes its cut in the same settlement. Cleanest on Algorand as an **atomic transfer group** that splits the agent's payment into provider-share + APIX-share in *one* settlement (native atomic transfers — no extra hop, single currency). **Zero upfront cost to list → maximises adoption; APIX earns when providers earn.** Matches the BSF EBIT model (small transaction fee, ~0.1% precedent). Structure: a small **percentage with a floor** (e.g. 1%, min 0.001 USDC).
- **Optional/complementary — subscription or registration tiers.** A flat recurring "rental" for providers who prefer predictable cost, or premium features (higher rate limits, priority, fee-abstraction sponsorship). BSF registration-tier model. Offered as a *choice*, never a mandatory entry gate (a gate would fight the "wrap any API in one config step" thesis).

**Fee abstraction (premium value-add, roadmap).** GoPlausible supports a fee-payer txn; the wrapper can *sponsor the ALGO gas* so agents pay **only USDC** (no ALGO at all). This turns the gas complexity into a selling point, priced into the USDC platform fee. Not in the 48h demo (which uses the simple single-txn model where the signer pays its own gas).

**Demo scope (48h):** fees are **represented transparently** — the BSM advertises the price breakdown and the result surfaces it (A price + B margin + APIX fee). Actually routing the APIX fee on-chain (the atomic-split) is a **stretch**, not core.
