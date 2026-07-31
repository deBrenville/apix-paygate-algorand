# Deploy plan — `paygate.api-index.org`

**Status:** planned, execute **after the pitch is finalized** (post-hackathon-submission).
**Goal:** give APIX Paygate a permanent home in the APIX family, on the existing Hetzner VPS,
as a subdomain of the production domain `api-index.org`.

> Not `apix.org`. The production domain is **`api-index.org`** (A → `204.168.156.179`, Hetzner VPS,
> fronted by Caddy). `apix.org` exists but is not the prod domain. See
> `~/.claude/.../memory/reference_apix_www_vs_apex.md`.

---

## Facts we build on

| Thing | Value |
|---|---|
| VPS | Hetzner, `204.168.156.179`, user `deploy` |
| Front proxy | **Caddy** (`infra/Caddyfile` lives in the **apix-registry** repo, not this one) |
| Existing vhosts | `www.api-index.org` → portal `:8081`, `api-index.org` (apex) → registry `:8180` |
| **DNS — no change needed** | IONOS already has a **wildcard** `*.api-index.org` A → `204.168.156.179`. Verified: `paygate.api-index.org` and a random subdomain both resolve to the VPS. So a new subdomain needs **zero DNS work** — only the Caddy vhost. |
| Deploy of registry/portal | Codeberg Forgejo → self-hosted VPS runner (`host`) → blue-green (`scripts/deploy-bluegreen.sh`) |
| TLS | Caddy auto-provisions Let's Encrypt on first request (needs DNS live + :80/:443 reachable — both already true) |

## Standing rules that apply

- **WCAG 2.1 AA from the first line** — the landing page starts accessible, never retrofit
  (`feedback_bfsg_websites`).
- **OSS = English** — this repo is the public hackathon repo; landing-page copy in EN.
- **Surgical commits** — when editing the central `infra/Caddyfile` in apix-registry, stage only that
  file; never co-stage registry modules (`reference_apix_www_vs_apex`, and watch the backup-job
  gotcha that pulls foreign files onto a feature branch, `finding_backup_job_commits_feature_branch`).
- **Codeberg = SSH** (`reference_codeberg_ssh_not_https`).

---

## Decision 1 — what does the subdomain serve?

- **(A) Static marketing page** — "APIX Paygate": what it is, the mark, the one-config-step pitch,
  links to the deck + GitHub repo. No backend. **Baseline — always do this.**
- **(B) A + a live x402 pay-gate demo** — additionally run the self-contained **facade** (this repo's
  Quarkus app: captured APIX discovery responses + the *dynamic* `402 Payment Required` gate) as a
  container behind Caddy, so a visitor (and Algorand's jury) hits a **real, dynamic 402** on the live
  URL. No on-chain settlement is exposed publicly — the facade returns the 402 + payment terms; the
  actual on-chain cascade stays in the demo video / `-Dx402.live=true` run.

**Recommendation:** ship **(A)** with the subdomain; add **(B)** as phase 2 (the dynamic 402 is
exactly Algorand's "enforced payment" story, so it's worth the extra container once the page is up).

## Decision 2 — how it's served

- **If static (A only):** Caddy site block with `root * /srv/paygate` + `file_server`. Publish the
  built page to `/srv/paygate` on the VPS (scp/rsync from this repo). No container, no blue-green
  (a marketing page doesn't need zero-downtime).
- **If facade (B):** add a single `paygate` container (this repo → Dockerfile → image on the VPS),
  Caddy `reverse_proxy paygate:8080`. Set `restart: unless-stopped` + memory/CPU limits. Blue-green
  optional; a single container is fine for this traffic.

---

## Runbook (execute post-pitch)

1. **DNS — nothing to do.** The `*.api-index.org` wildcard at IONOS already resolves
   `paygate.api-index.org` → `204.168.156.179` (verified). Caddy gets its Let's Encrypt cert via
   HTTP-01 on reload because the name already resolves to the box — no wildcard cert, no DNS-01.
2. **Content:** build the landing page in this repo under `web/` (or `site/`), WCAG-2.1-AA,
   APIX design tokens + the Paygate mark (`docs/brand/apix-paygate-mark.svg`). Self-contained.
3. **Caddy vhost (apix-registry repo, `infra/Caddyfile`):** add the block below. Commit **only** the
   Caddyfile; push → auto-deploy reloads Caddy.
   ```
   paygate.api-index.org {
       encode zstd gzip
       # (A) static:
       root * /srv/paygate
       file_server
       # (B) facade instead of the two lines above:
       # reverse_proxy paygate-a:8080 paygate-b:8080
   }
   ```
4. **Publish content / start container:**
   - (A) `rsync -az web/ deploy@204.168.156.179:/srv/paygate/`
   - (B) build image → `docker load`/registry → `docker compose up -d paygate` on the VPS.
5. **Verify (live-check the subdomain directly, not apex):**
   `curl -I https://paygate.api-index.org/` → `200` + valid LE cert; for (B) also
   `curl -i https://paygate.api-index.org/<gated-route>` → `402` with payment terms.
6. **A11y gate:** run the BFSG/WCAG checklist before announcing.

## Open questions for Carsten

- Decision 1: **(A) static now, (B) facade phase 2** — confirm, or go straight to (B)?
- Landing-page scope: pure marketing, or also a short "how it works" + a "try the 402" widget?
- Who edits the central `infra/Caddyfile` — I prep the diff, you push (needs Codeberg SSH access)?
