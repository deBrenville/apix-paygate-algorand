# Handover → Registry room (api-index.org): register the two demo services (real discovery, no fake)

**From:** the Onramp session (owns `apix-x402-onramp` / `demo.api-index.org`).
**To:** the session that owns `api-index.org` (the real APIX registry).
**Ask:** register the **two demo services** in the real registry (stage `DEVELOPMENT`) pointing at the
onramp's `/gw` routes, and drop the `/apix` facade surface from the `demo.api-index.org` Caddy vhost.

## Why (the decision)
The final pitch is *against the product* — the jury must be able to run the discovery themselves, and
seeing the services in the **real** registry proves APIX + Paygate are *ready enough*. A fake facade at
the registry step would signal the opposite. So: `demo.api-index.org` becomes a **pure service host**;
discovery happens against the **real** `api-index.org`. (The onramp already discovers via HATEOAS —
root → `services-search` → endpoint — verified compatible against the live registry.)

## 1. Register these two services (stage `DEVELOPMENT`)

| Name | capability | endpoint | price (agent-facing) |
|------|-----------|----------|----------------------|
| Hello World B — outer demo service | `demo.hello` | `https://demo.api-index.org/gw/hello` | 0.03 USDC (x402) |
| Hello World A — inner demo service | `demo.hello.inner` | `https://demo.api-index.org/gw/hello-inner` | 0.01 USDC (x402) |

- Owner org: **Bot Standards Foundation** (NON_PROFIT, CH). Trust can stay `UNVERIFIED` / stage
  `DEVELOPMENT` — that's honest and fine for the demo.
- Descriptions (suggested): B = "The outer service of the x402 cascade demo: discovers and pays an
  inner service over x402, then returns its own greeting nesting the inner one." A = "The 0-hop gated
  leaf of the x402 cascade demo; paid per call via x402 on Algorand."
- If registration needs a **domain-ownership proof** (`/.well-known/…` on `demo.api-index.org`): Carsten
  controls that domain and can place the token — tell us the exact path/value.
- **Capability taxonomy:** does the registry allow free capability strings, or must `demo.hello` /
  `demo.hello.inner` be added to a fixed taxonomy first? This is the open question from Carsten.

## 2. Drop the facade surface from the demo vhost
In `apix-registry/infra/Caddyfile`, the `demo.api-index.org` block: **remove `/apix` and `/services`
from the `@allowed` path list** (leaving `/`, `/.well-known/bsm/*`, `/gw/*`). The onramp no longer
serves discovery — it's a service host only, so those paths should 404 at the edge (no fake registry).

## Acceptance criterion (what unblocks the onramp re-deploy)
```bash
curl -s 'https://api-index.org/services?capability=demo.hello&stage=DEVELOPMENT' \
  | grep -o '"endpoint":"[^"]*"'          # -> "endpoint":"https://demo.api-index.org/gw/hello"
curl -s 'https://api-index.org/services?capability=demo.hello.inner&stage=DEVELOPMENT' \
  | grep -o '"endpoint":"[^"]*"'          # -> "endpoint":"https://demo.api-index.org/gw/hello-inner"
```
When both return their `/gw` endpoint, the onramp side is: set `ONRAMP_REGISTRY_URL=https://api-index.org/`
(already the default) and it discovers for real. Then we verify the cascade end-to-end over the live registry.

## Order (dependency)
1. **Registry room** registers both services + adjusts the Caddy vhost → the acceptance curls pass.
2. **Onramp** re-deploys (registry-url already points at api-index.org) → the cascade's inner hop and
   the jury's discovery both resolve against the real registry.

*Related: `apix-x402-onramp/docs/deploy/demo-runbook.md`, `docs/handover/apix-registry-demo-caddy-vhost.md`.*
