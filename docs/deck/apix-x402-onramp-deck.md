# APIX x402 Onramp — Pitch Deck

*WeAreDevelopers x402 Hackathon · Algorand · 6 slides*

---

## 1 · APIX x402 Onramp

**Make any API discoverable *and* payable for AI agents — in one config step.**

A reusable gateway on Algorand. Real USDC. No human in the loop.

*Live demo: an agent screens a name against global sanctions lists, pays per call over x402, and
gets a values-aware, provenance-tagged result — settling twice on-chain in ~8 seconds.*

---

## 2 · The problem

Autonomous agents can't use paid APIs the way humans do:

- No human to **sign up**, enter a **card**, solve a **CAPTCHA**, or manage **OAuth**.
- And even once payment is solved — how does an agent **find** the service and its price in the
  first place?

Today an agent needs a human to onboard every single API. That doesn't scale to an agent economy.

---

## 3 · The insight

**Payment on Algorand is already solved.** x402 (Coinbase's HTTP-402 protocol) + the GoPlausible
facilitator let any endpoint charge in USDC — no accounts, no infrastructure.

**The missing half is discovery** — and that's what APIX brings: a machine-readable manifest (BSM)
so an agent *finds* a service and its terms without docs or a portal.

> The multiplier isn't "easy payments." It's **discovery + payment fused into one zero-code step** —
> the discovery half is uniquely APIX's, the part Algorand's ecosystem lacks.

---

## 4 · The product

The **Onramp** is a reverse-proxy gateway. Point it at any existing API and, with one config entry,
that API becomes:

- **discoverable** — auto-published BSM in the APIX agent index, *and*
- **payable** — x402 on Algorand enforced at the gateway, **zero paywall code** for the provider.

The provider keeps its origin private behind a forward secret; the gateway does the rest.

**It composes.** A wrapped service can itself be an agent that discovers and pays *another* wrapped
service — paid, discoverable **chains** of services.

---

## 5 · The demo — a live cascade

**Agent → Humanity layer (B) → Neutral ledger (A)**, two x402 hops, both settled on-chain in USDC:

1. Agent discovers **B** via its BSM, pays **0.03 USDC**, calls it.
2. **B** discovers and pays the neutral sanctions ledger **A** — **0.01 USDC** — a second on-chain hop.
3. **A** (using a real screening engine over UN/EU/SECO/OFAC) returns every register match as
   evidence + a similarity score. **Stateless — nothing stored.**
4. **B** applies the **BSF pro-humanity filter**: for an international-court prosecutor sanctioned
   only by OFAC, it returns **`MATCH_EXEMPT`** — OFAC record kept as proof, precedent cited
   (EU Blocking Regulation 2271/96). *A ledger, not a blind judge.*

---

## 6 · Values, economics & adoption

- **Ledger, not judge** — the neutral layer never blocks; the values layer is separate and
  transparent. Stateless + evidence-framed (record-keeping stays with the caller; GDPR-minimal).
- **Value-add pricing, on-chain** — B charges 0.03, pays A 0.01, keeps a **0.02 margin**. Three
  earning parties (ledger, value-add, platform). APIX earns a small USDC platform fee, not ALGO.
- **Adoption** — any provider joins with **one config entry**; zero upfront cost. This is not a
  mock: it runs today on Algorand testnet with official USDC, mainnet is a config flip.

**Zero-code discoverable *and* payable. APIX x402 Onramp.**
