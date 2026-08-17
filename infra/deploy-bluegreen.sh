#!/bin/bash
# Blue-green, zero-downtime deploy for the APIX x402 Onramp (the live demo at demo.api-index.org).
#
# Own pipeline, separate repo — mirrors apix-paygate's deploy for a single stateless service as an
# a/b pair. Strategy: build the image, force-recreate onramp-a, wait healthy, then onramp-b. One
# instance is always healthy, so the central Caddy (lb_policy=first + active health checks) never
# routes to a down box.
#
# Run on the VPS from a checkout of this repo:  bash infra/deploy-bluegreen.sh
# Requires: Docker; the apix-registry stack up (so the external caddy network exists); a secret file
# at /opt/onramp/.env (or ~/onramp/.env + export ONRAMP_ENV_FILE) containing ONRAMP_SERVICE_B_SENDER_MNEMONIC
# (the server-side payer for the inner cascade hop). See .env.template for the full wallet map.

set -euo pipefail
umask 077
exec > >(tee "${HOME:-/home/deploy}/onramp-deploy-last.log") 2>&1

INFRA_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
COMPOSE_FILE="$INFRA_DIR/docker-compose.bluegreen.yml"
ENV_FILE="${ONRAMP_ENV_FILE:-/opt/onramp/.env}"
export ONRAMP_ENV_FILE="$ENV_FILE"
PROJECT="onramp"
COMPOSE="docker compose -p $PROJECT -f $COMPOSE_FILE"

log() { echo "[onramp-deploy $(date -u +%H:%M:%S)] $*"; }

wait_healthy() {
    local container=$1
    local max=90 elapsed=0 status
    log "Waiting for $container to become healthy..."
    while [ "$elapsed" -lt "$max" ]; do
        status=$(docker inspect "$container" --format '{{.State.Health.Status}}' 2>/dev/null || echo "missing")
        if [ "$status" = "healthy" ]; then
            log "$container is healthy."
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
    log "ERROR: $container did not become healthy after ${max}s (last status: $status)"
    docker logs "$container" --tail 30 2>&1 || true
    return 1
}

log "run started $(date -u +%FT%TZ) — commit $(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo '?')"

# ── 0. Pre-flight: the boot-required payer mnemonic must be present AND non-empty ─
# The cascade's inner hop (outer service -> inner service) is settled server-side by
# ONRAMP_SERVICE_B_SENDER_MNEMONIC. Without it the second hop throws at call time, so the demo is dead.
if [ ! -f "$ENV_FILE" ] || ! grep -qE '^ONRAMP_SERVICE_B_SENDER_MNEMONIC=.+' "$ENV_FILE"; then
    log "ERROR: $ENV_FILE missing or ONRAMP_SERVICE_B_SENDER_MNEMONIC is absent/empty."
    log "It is the server-side payer that settles the inner cascade hop (the outer service paying the inner)."
    log "See .env.template for the wallet map (ONRAMP_B_* = the OUTER service = your SERVICE A wallet)."
    exit 1
fi

# ── 1. Build the image ───────────────────────────────────────────────────────
SHA="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo 'nogit')"
log "Building apix-x402-onramp:latest (also tagged :$SHA) ..."
docker build -f "$INFRA_DIR/Dockerfile" -t apix-x402-onramp:latest -t "apix-x402-onramp:$SHA" "$REPO_ROOT" -q

# ── 2. Rolling restart: a first, wait healthy, then b ────────────────────────
log "Recreating onramp-a ..."
$COMPOSE --env-file "$ENV_FILE" up -d --no-deps --force-recreate onramp-a
wait_healthy onramp-a

log "Recreating onramp-b ..."
$COMPOSE --env-file "$ENV_FILE" up -d --no-deps --force-recreate onramp-b
wait_healthy onramp-b

log "Deploy complete — one instance stayed healthy throughout."
log "The central Caddy (apix-registry) health-routes demo.api-index.org between onramp-a/b."
