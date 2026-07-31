# Submission form — ready-to-paste answers

WeAreDevelopers x402 Hackathon (Algorand). Deadline **2026-07-31, 23:00 CEST**.
Fill the `<<…>>` placeholders (personal / links) and paste the rest.

---

**First / Last name:** `<<your name>>`
**Email:** `<<your email>>`
**X (Twitter) project profile:** `<<https://x.com/…>>`  *(create a project handle if you don't have one)*

**Project name:**
> APIX Paygate

**Project one-liner:**
> APIX Paygate makes any API discoverable *and* payable for AI agents in one config step —
> fusing APIX machine-native discovery with x402 pay-per-call on Algorand.

**Project description** *(problem · users · how it uses x402 on Algorand):*
> **Problem.** Autonomous agents can't use paid APIs the way humans do — there's no one to sign up,
> enter a card, solve a CAPTCHA, or run OAuth — and even once payment is solved, an agent still has
> to *find* the service and its price. Today a human must onboard every API. That doesn't scale to
> an agent economy.
>
> **Users.** API/service providers who want to sell to agents without building payment or billing,
> and the agents (and their developers) that need to discover and pay for services autonomously.
>
> **How it uses x402 on Algorand.** APIX Paygate is a reverse-proxy gateway. Wrap any existing API
> with one config entry and it becomes (1) **discoverable** via an auto-published APIX manifest
> (BSM) and (2) **payable** via **x402 on Algorand**, enforced at the gateway with **zero paywall
> code** for the provider. Payment settles in USDC through the GoPlausible facilitator; the payment
> is the core flow, not a bolt-on. Crucially it **composes**: a wrapped service can itself be an
> agent that discovers and pays another wrapped service.
>
> **Live demo — a cascade with two on-chain x402 hops.** An agent discovers and pays a "humanity"
> sanctions-screening service (0.03 USDC); that service in turn discovers and pays a neutral global
> sanctions *ledger* (0.01 USDC). The ledger screens against UN/EU/SECO/OFAC with a real matcher and
> returns every register match as **evidence + a score** — stateless, nothing stored. The humanity
> layer applies a pro-humanity filter: a subject listed *only* by OFAC and serving humanity (an
> international-court prosecutor not sanctioned by the UN/EU/Switzerland) is returned as
> `MATCH_EXEMPT`, with the OFAC record kept as proof — *a ledger, not a blind judge*. The value-add
> layer keeps a 0.02 USDC margin. Runs today on Algorand testnet with official USDC; mainnet is a
> config flip.

**Team members** *(name, email, role, background):*
> `<<Name, email — role, background>>`
> `<<add co-founders if any>>`

**Pitch deck link (5–6 slides):**
> `<<link to docs/deck/apix-x402-onramp-deck.md on GitHub, or an exported PDF/slides>>`

**Demo video link (3–5 min):**
> `<<link to the recorded run of ./demo/run-demo.sh>>`

**GitHub repository URL:**
> `<<https://github.com/…/apix-x402-onramp>>`

**Project URL (live demo):**
> `<<deployed gateway URL, or: "runs locally against Algorand testnet — see README">>`

---

*Note: the form's Program registration (July 8–20) had to be completed to be eligible to submit;
this fills the Project Submission (due July 31).*
