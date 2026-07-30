#!/usr/bin/env bash
# Narrated live cascade demo for the video.
# Requires: a funded testnet .env (ONRAMP_PAYER_MNEMONIC, ONRAMP_B_PAYER_MNEMONIC) with USDC + ALGO.
# Screen-record the terminal while this runs; see docs/demo-caption-script.md for the voiceover.
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -o test -Dtest=CascadeDemoRunner -Dx402.live=true
