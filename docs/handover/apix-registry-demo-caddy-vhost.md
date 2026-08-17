# Handover → Baustelle II (apix-registry): add the `demo.api-index.org` Caddy vhost

**From:** the Onramp session (owns `apix-x402-onramp`; read-only on `apix-registry`).
**To:** the session that owns `apix-registry` (Baustelle II).
**Ask:** add **one** Caddy site block into `apix-registry/infra/Caddyfile` — the *only* apix-registry
change the live cascade demo needs. Everything else (the container pair, its build/deploy) lives in the
`apix-x402-onramp` repo. Place it alongside the existing site blocks (`api-index.org`, `www`, `demo`,
`paygate`, `admin`, `git`); commit surgically.

## Why this is the only apix-registry touch
The onramp is deployed **blue-green** from its own repo/pipeline (not folded into the monorepo): an
`onramp-a`/`onramp-b` pair (`apix-x402-onramp/infra/docker-compose.bluegreen.yml` +
`infra/deploy-bluegreen.sh`). **It is stateless — no DB.** The pair joins the **existing**
`infra_default` network as *external*. So the central Caddy just needs a vhost pointing at the pair —
same shape as `registry-a/-b` and `paygate-a/-b`.

## Preconditions
- `onramp-a` / `onramp-b` deployed and healthy on `infra_default` (rolling a→b, zero-downtime); the
  Caddy container reaches them (`docker exec infra-caddy-1 wget -qO- http://onramp-a:8080/`
  → the onramp self-description JSON).
- No DNS change needed: `demo.api-index.org` resolves via the IONOS `*.api-index.org` wildcard; Caddy
  provisions the Let's Encrypt cert on first request.
- ⚠ If a `demo.api-index.org` block already exists for something else, coordinate — this claims that
  subdomain for the x402 cascade demo.

## The single task — add this block in `infra/Caddyfile`
Source of truth (kept in sync in the onramp repo): `apix-x402-onramp/infra/Caddyfile.snippet`.

```caddy
demo.api-index.org {
	encode zstd gzip

	# Public demo surface (default-deny allowlist):
	#   /                     the onramp self-description (JSON, agent-native)
	#   /apix, /apix/*        the APIX discovery facade (browse services by capability)
	#   /services, /services/*  the registry search facade (GET /services?capability=…)
	#   /.well-known/bsm/*    the per-route BSM payment terms
	#   /gw/*                 the x402 gate: 402 -> pay -> proxied call (the cascade entry point)
	# Everything else 404s — crucially /internal/* (the private upstreams, reachable ONLY via the gate)
	# and /q/* (Quarkus management).
	@allowed path / /apix /apix/* /services /services/* /.well-known/bsm/* /gw/*
	handle @allowed {
		reverse_proxy onramp-a:8080 onramp-b:8080 {
			lb_policy       first
			health_uri      /
			health_interval 5s
			fail_duration   30s
		}
	}

	handle {
		respond 404
	}

	header {
		-Server
		Strict-Transport-Security "max-age=31536000; includeSubDomains"
		X-Content-Type-Options "nosniff"
		Referrer-Policy "strict-origin-when-cross-origin"
		Content-Security-Policy "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
	}
}
```

## What the block does (so it can be judged, not just pasted)
- **Blue-green health-routing** to `onramp-a/b` (`lb_policy first` + active `/` checks) — same pattern as
  `registry-a/-b`. Active health checks dial the upstream directly, unaffected by the allowlist.
- **Default-deny allowlist:** only the discovery + gate + BSM surface is proxied; **everything else 404s**
  — including `/internal/*` (the private cascade upstreams) and `/q/*` — so they stay non-public
  independent of the app's own routing. Defense in depth: the upstreams also reject any request without
  the shared forward secret.
- **Strict CSP** — the onramp serves JSON only (no inline HTML/JS/wallet), so `default-src 'self'` is
  enough. (If a human-facing "Run the cascade" page is added later, widen it then.)

## Commit discipline
- **Stage only `infra/Caddyfile`.** Surgical — don't co-stage registry modules. Watch the backup-job
  gotcha that pulls foreign files onto a branch (`finding_backup_job_commits_feature_branch`).
- Suggested message: `feat(caddy): add demo.api-index.org vhost (x402 cascade demo, blue-green)`.
- Push → the apix-registry auto-deploy syncs the Caddyfile and reloads Caddy.

## Verify after your deploy reloads Caddy
```bash
curl -sI  https://demo.api-index.org/                                          # 200 + valid LE cert
curl -s   https://demo.api-index.org/ | head -c 400                            # onramp self-description JSON
# discovery facade returns the OUTER service, endpoint now on demo.api-index.org (NOT localhost):
curl -s 'https://demo.api-index.org/apix/services?capability=compliance.sanctions.screen.humanity&stage=DEVELOPMENT' \
  | grep -o '"endpoint":"[^"]*"'                                               # -> https://demo.api-index.org/gw/humanity
# closed surface still denied (each must be 404):
for p in /internal/humanity/screen /q /q/health/live /internal/ledger/screen; do
  printf '%s -> ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "https://demo.api-index.org$p"
done   # every line must print 404
```

## Rollback
Revert the one-file commit and redeploy — the subdomain reverts cleanly; the `onramp-a/b` containers are
untouched (they live in the onramp repo).

*Related: `apix-x402-onramp/docs/deploy/demo-runbook.md` (deploy + jury self-check recipe).*
