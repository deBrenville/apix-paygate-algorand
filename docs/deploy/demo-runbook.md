# demo.api-index.org — deploy + jury self-check runbook

The live x402 **cascade demo**: an agent pays the outer service (B, "Hello World B"); B discovers and
pays the inner service (A, "Hello World A") in the background — **two on-chain settlements per call.**
Content is deliberately trivial; the demo showcases the 0-hop gated service + server-side chaining.

## Wallet map (the source of truth — the onramp's internal A/B names are INVERTED vs. these)

| Wallet     | Public address (IBAN-like)                                   | Role                                   |
|------------|--------------------------------------------------------------|----------------------------------------|
| AGENT      | `Q2XTPIACCO27OZ7ROPPT5SU5HLAZCELVGEGGSGRIMQBP5TBXHOHZWN6GOY` | pays hop 1 (0.03 USDC → SERVICE A)     |
| SERVICE A  | `GVRXJLXF6OJLRIV46CRIXLG3DX3RJYBBAGRKNNYLHWOAHG57GKWHDH6ZAE` | OUTER svc: receives hop 1, pays hop 2  |
| SERVICE B  | `EO63ZQECPU7IMHTSWOZKH7YBBS73PY72RJKCAMI3LHFT6LGMIR2W5ECIBE` | INNER svc: receives hop 2 (0.01 USDC)  |

Env-var mapping (match by ROLE): `ONRAMP_B_*` = the OUTER service = **SERVICE A**;
`ONRAMP_A_*` = the INNER service = **SERVICE B**. Full detail: `.env.template`.

## 0. Prerequisite — all three wallets opted in to testnet USDC (ASA 10458941)
A payment to a wallet that hasn't opted in fails. AGENT + SERVICE A were already used (8e dogfood);
**SERVICE B (`EO63Z…`) is new — it must be funded (~0.2 ALGO) and opt in to ASA 10458941** before the
first cascade. AGENT also needs a testnet USDC balance to pay hop 1.
```bash
# one-off, run locally with each wallet's mnemonic set (see OptInHelper):
mvn -o test -Dtest=OptInHelper -Dx402.live=true    # opts the configured wallet into ASA 10458941
```

## 1. Deploy the onramp pair (on the VPS)
```bash
# secret file on the box — ONRAMP_B_PAYER_MNEMONIC = SERVICE A's 25 words (see .env.template).
# You set this; the assistant never sees a mnemonic. Only public addresses are shareable.
sudoedit /opt/onramp/.env        # or ~/onramp/.env + export ONRAMP_ENV_FILE
chmod 600 /opt/onramp/.env
cd ~/apix-x402-onramp && git pull
bash infra/deploy-bluegreen.sh   # builds image, rolls onramp-a then onramp-b, both healthy
```
The deploy pre-flight refuses to start if `ONRAMP_B_PAYER_MNEMONIC` is absent/empty (without it the
second hop throws at call time).

## 2. Caddy vhost (cross-repo — Baustelle II / apix-registry)
Hand `docs/handover/apix-registry-demo-caddy-vhost.md` to the apix-registry session. It adds the one
`demo.api-index.org` site block to `apix-registry/infra/Caddyfile` and pushes → auto-deploy reloads
Caddy. (The vhost MUST live in that repo — the deploy overwrites the box Caddyfile from the repo.)

## 3. Jury self-check (live, interactive) — the dynamic gate
The jury can verify the mechanism themselves against the live box:
```bash
# self-description:
curl -s https://demo.api-index.org/

# discovery by capability — endpoint now points at demo.api-index.org (proof the facade re-bases):
curl -s 'https://demo.api-index.org/apix/services?capability=compliance.sanctions.screen.humanity&stage=DEVELOPMENT'

# the DYNAMIC gate: an UNPAID call is refused with a real 402 carrying the exact terms
curl -s -X POST 'https://demo.api-index.org/gw/humanity?lawfulBasisAttested=true' \
  -H 'Content-Type: application/json' -d '{"name":"hello"}' -i | sed -n '1,20p'
# -> HTTP/1.1 402 Payment Required
#    accepts[0]: amount=30000 (0.03 USDC), asset=10458941, payTo=GVRXJ… (SERVICE A)
```
This proves, live and unfaked: a real registry-discoverable service, a real dynamic 402 with on-chain
payment terms. No account, no email, no OAuth, no CAPTCHA.

## 4. Settlement proof (the two on-chain hops) — Explorer links from a real run
Because the jury holds no wallet, we show the settled cascade via a real run's transactions. Run once
(locally is fine — the settlements are real testnet transactions regardless of where the server runs),
with AGENT + SERVICE A mnemonics set (`ONRAMP_PAYER_MNEMONIC` + `ONRAMP_B_PAYER_MNEMONIC`):
```bash
mvn -o test -Dtest=CascadeDemoRunner#run -Dx402.live=true
# server log prints two "x402 settled" lines (hop 1: AGENT→SERVICE A, hop 2: SERVICE A→SERVICE B);
# the run asserts HTTP 200, message "Hello World B", innerResult.message "Hello World A".
```
Take the two transaction IDs and present them as AlgoExplorer testnet links, alongside the wallets:
- AGENT → SERVICE A (0.03 USDC): `https://testnet.explorer.perawallet.app/tx/<HOP1_TXID>/`
- SERVICE A → SERVICE B (0.01 USDC): `https://testnet.explorer.perawallet.app/tx/<HOP2_TXID>/`
- Wallet histories: `https://testnet.explorer.perawallet.app/address/<ADDRESS>/`

## Verify checklist
- [ ] `curl -sI https://demo.api-index.org/` → 200, valid LE cert
- [ ] discovery endpoint shows `https://demo.api-index.org/gw/humanity` (NOT localhost) → proxy X-Forwarded works
- [ ] unpaid `POST /gw/humanity` → 402 with payTo = SERVICE A, amount 30000, asset 10458941
- [ ] `/internal/humanity/screen`, `/internal/ledger/screen`, `/q/*` → 404 at the edge
- [ ] one real `CascadeDemoRunner#run` → 200 + "Hello World B" nesting "Hello World A" + two settled TXs

## Rollback
`onramp-a/b` are stateless — redeploy a previous commit, or `docker compose -p onramp down`. The Caddy
vhost rollback is a one-file revert in apix-registry (see the handover).
