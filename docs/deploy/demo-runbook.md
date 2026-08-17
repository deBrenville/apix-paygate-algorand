# demo.api-index.org — deploy + jury self-check runbook

The live x402 **cascade demo**: Agent A pays the outer service (Service B, "Hello World B"); Service B
discovers and pays the inner service (Service C, "Hello World A") in the background — **two on-chain
settlements per call.** Content is deliberately trivial; the demo showcases the 0-hop gated service +
the server-side chaining of gated services.

## Wallet map (flow: Agent A → Service B → Service C)

| Wallet     | Public address (IBAN-like)                                   | Role                                      |
|------------|--------------------------------------------------------------|-------------------------------------------|
| Agent A    | `Q2XTPIACCO27OZ7ROPPT5SU5HLAZCELVGEGGSGRIMQBP5TBXHOHZWN6GOY` | pays hop 1 (0.03 USDC → Service B)         |
| Service B  | `GVRXJLXF6OJLRIV46CRIXLG3DX3RJYBBAGRKNNYLHWOAHG57GKWHDH6ZAE` | receives hop 1, **sends** hop 2 to C       |
| Service C  | `EO63ZQECPU7IMHTSWOZKH7YBBS73PY72RJKCAMI3LHFT6LGMIR2W5ECIBE` | receives hop 2 (0.01 USDC), signs nothing  |

Env vars are named by hop + role (no letter puzzle). The only boot-critical secret is
`ONRAMP_SERVICE_B_SENDER_MNEMONIC` (Service B signs hop 2). Addresses ship as defaults in
`application.yaml`. Full detail: `.env.template`.

## 0. Prerequisite — the receiving wallets opted in to testnet USDC (ASA 10458941)
A payment to a wallet that hasn't opted in fails. **Service C (`EO63Z…`) is new — fund it (~0.2 ALGO)
and opt it in to ASA 10458941** before the first cascade. Service B must be opted in too (it receives
hop 1) and hold a little USDC + ALGO (it pays hop 2). Agent A needs a testnet USDC balance for hop 1.

```bash
# one-off, run locally with the target wallets' mnemonics set (never on the box). OptInHelper opts in
# whichever of the three are present: ONRAMP_AGENT_SENDER_MNEMONIC, ONRAMP_SERVICE_B_SENDER_MNEMONIC,
# and — opt-in only, since Service C signs nothing in the server — ONRAMP_SERVICE_C_OPTIN_MNEMONIC.
mvn -o test -Dtest=OptInHelperTest -Dx402.live=true
# prints each wallet's ALGO + USDC opt-in status, and submits the opt-in where ALGO is present.
```

## 1. Deploy the onramp pair (on the VPS)
```bash
# secret file on the box — ONRAMP_SERVICE_B_SENDER_MNEMONIC = Service B's 25 words (see .env.template).
# You set this; the assistant never sees a mnemonic. Only public addresses are shareable.
sudoedit /opt/onramp/.env        # or ~/onramp/.env + export ONRAMP_ENV_FILE
chmod 600 /opt/onramp/.env
cd /opt/apix-x402-onramp && git pull
bash infra/deploy-bluegreen.sh   # builds image, rolls onramp-a then onramp-b, both healthy
```
The deploy pre-flight refuses to start if `ONRAMP_SERVICE_B_SENDER_MNEMONIC` is absent/empty. On boot,
the server also verifies that this mnemonic derives `ONRAMP_SERVICE_B_SENDER_ADDRESS` — on a mismatch
it refuses to start (so Agent A can never pay one address while Service B signs with another).

## 2. Caddy vhost (cross-repo — Baustelle II / apix-registry)
Hand `docs/handover/apix-registry-demo-caddy-vhost.md` to the apix-registry session. It adds the one
`demo.api-index.org` site block to `apix-registry/infra/Caddyfile` and pushes → auto-deploy reloads
Caddy. (The vhost MUST live in that repo — the deploy overwrites the box Caddyfile from the repo.)

## 3. Jury self-check (live, interactive) — the dynamic gate
```bash
# self-description:
curl -s https://demo.api-index.org/

# discovery by capability — endpoint now points at demo.api-index.org (proof the facade re-bases):
curl -s 'https://demo.api-index.org/apix/services?capability=compliance.sanctions.screen.humanity&stage=DEVELOPMENT'

# the DYNAMIC gate: an UNPAID call is refused with a real 402 carrying the exact terms
curl -s -X POST 'https://demo.api-index.org/gw/humanity?lawfulBasisAttested=true' \
  -H 'Content-Type: application/json' -d '{"name":"hello"}' -i | sed -n '1,20p'
# -> HTTP/1.1 402 Payment Required
#    accepts[0]: amount=30000 (0.03 USDC), asset=10458941, payTo=GVRXJ… (Service B)
```
This proves, live and unfaked: a real registry-discoverable service, and a real dynamic 402 with
on-chain payment terms. No account, no email, no OAuth, no CAPTCHA.

## 4. Settlement proof (the two on-chain hops) — Explorer links from a real run
Because the jury holds no wallet, we show the settled cascade via a real run's transactions. Run once
(locally is fine — the settlements are real testnet transactions regardless of where the server runs),
with Agent A + Service B mnemonics set (`ONRAMP_AGENT_SENDER_MNEMONIC` + `ONRAMP_SERVICE_B_SENDER_MNEMONIC`):
```bash
mvn -o test -Dtest=CascadeDemoRunner#run -Dx402.live=true
# server log prints two "x402 settled" lines (hop 1: Agent A→Service B, hop 2: Service B→Service C);
# the run asserts HTTP 200, message "Hello World B", innerResult.message "Hello World A".
```
Take the two transaction IDs and present them as AlgoExplorer testnet links, alongside the wallets:
- Agent A → Service B (0.03 USDC): `https://testnet.explorer.perawallet.app/tx/<HOP1_TXID>/`
- Service B → Service C (0.01 USDC): `https://testnet.explorer.perawallet.app/tx/<HOP2_TXID>/`
- Wallet histories: `https://testnet.explorer.perawallet.app/address/<ADDRESS>/`

## Verify checklist
- [ ] `curl -sI https://demo.api-index.org/` → 200, valid LE cert
- [ ] discovery endpoint shows `https://demo.api-index.org/gw/humanity` (NOT localhost) → proxy X-Forwarded works
- [ ] unpaid `POST /gw/humanity` → 402 with payTo = Service B, amount 30000, asset 10458941
- [ ] `/internal/humanity/screen`, `/internal/ledger/screen`, `/q/*` → 404 at the edge
- [ ] one real `CascadeDemoRunner#run` → 200 + "Hello World B" nesting "Hello World A" + two settled TXs

## Rollback
`onramp-a/b` are stateless — redeploy a previous commit, or `docker compose -p onramp down`. The Caddy
vhost rollback is a one-file revert in apix-registry (see the handover).
