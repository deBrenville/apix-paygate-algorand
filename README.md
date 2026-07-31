# APIX x402 Onramp

**Make any HTTP API discoverable *and* payable for AI agents — in one config step.**
A Quarkus reverse-proxy gateway that fuses machine-native discovery (APIX BSM) with pay-per-call
settlement (x402 on Algorand). Built for the WeAreDevelopers x402 Hackathon.

Payment on Algorand is already solved (x402 + the GoPlausible facilitator). The missing half is
**discovery** — a machine-readable manifest so an agent *finds* a service and its price. The Onramp
fuses both into one zero-code step, and it **composes**: a wrapped service can itself discover and
pay another wrapped service.

## What it demonstrates

A live **cascade** — two x402 hops, both settled on-chain in testnet USDC:

```
Agent ──x402 (0.03 USDC)──▶ Upstream B (BSF humanity layer)
                              └─x402 (0.01 USDC)──▶ Upstream A (neutral sanctions ledger)
```

- **A — neutral global sanctions ledger.** Screens a subject against UN/EU/SECO/OFAC with a real
  matcher (`apix-verification`), aggregates one match per register, and returns the match as
  **evidence + a similarity score** (a "match-proof"). **Stateless** — nothing is persisted.
- **B — BSF humanity layer.** Discovers and pays A over x402, then applies a **pro-humanity filter**:
  a subject listed *only* by OFAC and serving humanity (e.g. an international-court prosecutor) is
  returned as `MATCH_EXEMPT`, with the OFAC record kept as proof and the precedent cited. *Ledger,
  not blind judge.* B keeps a margin (0.03 in, 0.01 out).
- **Attestation.** Compliance routes require a loggable, non-PII `?lawfulBasisAttested=true` (audit
  trail in access logs); the screened subject travels only in the request body.
- **Discovery is real, HATEOAS-driven.** The agent knows only the registry **entry URL** and its
  **goal capability**. It follows links: enter the root → follow `services-search` → search by
  capability → follow the service's `endpoint`. No service URL or payment detail is hardcoded; the
  payment terms (payTo/amount/asset) come from the service's `402`.

## What's real vs. replayed

The point Algorand cares about — **enforced payment** — is fully real and dynamic. The only replayed
part is the *discovery lookup*:

| Part | Real/dynamic? | How |
|---|---|---|
| **x402 enforcement** (402 when unpaid → verify/settle when paid) | **real, dynamic, on-chain** | `X402GatewayFilter` + GoPlausible facilitator + Algorand testnet (official USDC) |
| **Two-hop cascade + margin** | **real, on-chain** | agent→B and B→A each settle on-chain |
| **Discovery** (which service exists, its endpoint) | replayed | `DemoRegistryResource` serves responses **captured from a real apix-registry** (services registered at DEVELOPMENT stage) — so the demo needs no live registry/DB and never exposes APIX |

Even without a wallet, `mvn test` runs `X402GatewayFilterTest`, which proves the 402 gate rejects
unpaid calls and accepts paid ones (facilitator stubbed). The live cascade shows the real on-chain
settlement. In production, discovery points at the live `api-index.org` — same shape, same links.

## Architecture

| Piece | Responsibility |
|---|---|
| `gateway/` | `POST /gw/{route}` reverse-proxies to a private origin with a forward secret |
| `x402/` | `@PreMatching` filter: 402 → facilitator verify/settle → proxy; `X402AvmClient` builds the signed Algorand payment |
| `bsm/` | `/.well-known/bsm/{route}` — the discovery manifest (capability, price, x402 terms) |
| `ledger/` | Upstream A — neutral match-proof over curated fixtures |
| `humanity/` | Upstream B — x402 client of A + the pro-humanity filter |
| `client/` | The demo agent driving the cascade |

Upstreams are config entries (`onramp.upstreams` in `application.yaml`) — that's the "one config
step" to wrap an API.

## Run the tests

Offline unit/integration tests (no network, no chain):

```bash
mvn -o test
```

## Run the live demo (records the video)

Needs a funded testnet `.env` in the repo root (git-ignored):

```
ONRAMP_PAYER_MNEMONIC="… 25 words …"     # the agent (pays B)
ONRAMP_B_PAYER_MNEMONIC="… 25 words …"   # humanity B (pays A)
```

Both accounts must hold testnet **USDC** (ASA `10458941`) and a little **ALGO**. Then:

```bash
./demo/run-demo.sh        # or: mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true
```

The narrated output + the two `x402 settled` server-log lines are the demo. See
`docs/demo-caption-script.md`.

## Environment / config

- `onramp.price-asset-id` — payment asset. Testnet USDC `10458941` (mainnet USDC `31566704`).
- `onramp.facilitator-url` — GoPlausible facilitator (`https://facilitator.goplausible.xyz`).
- `onramp.upstreams[]` — the wrapped routes (route, upstream-url, forward-secret, pay-to, price, …).
- `.env` is git-ignored; **mnemonics are never printed or committed**.

## Notes

- **Sanctions data** in this demo is small curated fixtures (production would swap in the full list
  import — the parsers already exist). The `SanctionsMatcher` engine is the real one.
- **`apix-verification`** is installed to the local Maven repo:
  `mvn -pl apix-verification -am install -DskipTests` from the `apix-registry` project.
- On-chain helpers/tests are gated behind `-Dx402.live=true` so a normal `mvn test` stays offline.

*BSF · APIX (API Index) — neutral infrastructure for autonomous agents.*
