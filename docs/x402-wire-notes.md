# x402 AVM wire notes (pinned for the spike)

Source: GoPlausible Algorand x402 docs + facilitator. Verified 2026-07-29.

## Facilitator
- Base URL: `https://facilitator.goplausible.xyz`
- `POST /verify` — validate a signed payment, no settlement.
- `POST /settle` — broadcast the transfer on-chain.

### Request body (both /verify and /settle) — VERIFIED EMPIRICALLY 2026-07-30
The README's flattened shape is WRONG. The facilitator wants top-level `paymentPayload`
(the full payment payload object) + `paymentRequirements`:
```json
{
  "x402Version": 2,
  "paymentPayload": {
    "x402Version": 2,
    "scheme": "exact",
    "network": "algorand:SGO1GKSzyE7IEPItTxCByw9x8FmnrCDexi9/cOUJOiI=",
    "payload": { "paymentGroup": ["<b64 msgpack signed axfer>"], "paymentIndex": 0 }
  },
  "paymentRequirements": {
    "scheme": "exact",
    "network": "algorand:SGO1GKSzyE7IEPItTxCByw9x8FmnrCDexi9/cOUJOiI=",
    "asset": "10458941",
    "amount": "10000",
    "payTo": "<algo address>",
    "maxTimeoutSeconds": 60,
    "extra": { "decimals": 6 }
  }
}
```
- Missing top-level `paymentPayload`/`paymentRequirements` → `{"error":"Missing paymentPayload or paymentRequirements"}`.
- **/verify runs a full on-chain transaction SIMULATION** (not just a structural check): it verifies the payer is opted into the asset and holds the balance. A not-opted-in payer yields `invalidReason: "... asset <id> missing from <addr>"`.

### Responses
- `/verify` → `{ "isValid": true, "invalidReason": null }`
- `/settle` → `{ "success": true, "transaction": "<txId>", "network": "algorand:..." }`

## Network
- Algorand **testnet** CAIP-2: `algorand:SGO1GKSzyE7IEPItTxCByw9x8FmnrCDexi9/cOUJOiI=`
- Testnet USDC ASA id (if we ever price in USDC): `10458941`.

## X-PAYMENT header
- **Raw JSON** (NOT base64-of-JSON):
  `{ x402Version:2, scheme:"exact", network:"algorand:...", payload:{ paymentGroup:[...], paymentIndex:N } }`
- `paymentGroup` = array of **base64(msgpack(signedTxn))**.

## Payment transaction — RESOLVED EMPIRICALLY
- The facilitator **requires an asset transfer (`axfer`)**. A native-ALGO `pay` txn is rejected:
  `invalidReason: "Payment transaction is not an asset transfer"`. So native ALGO (`asset:"0"`)
  is NOT usable with this facilitator — we must pay in an **ASA**.
- **Demo asset = testnet USDC, ASA `10458941`** (canonical). Single signed `axfer`, `paymentIndex: 0`,
  payer covers its own fee (no fee abstraction).
- **Spike gate result:** the facilitator accepted and *simulated* our pure-Java `axfer`; the only
  remaining failure is account state (opt-in + USDC balance). The Java client is proven.
- Consequence: the payer account must be **opted into ASA 10458941** (automatable in code) and hold
  a small **test-USDC balance** (from a faucet). The demo prices in USDC; mainnet flips via config.
