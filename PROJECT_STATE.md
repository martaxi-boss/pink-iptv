# PINK IPTV - PROJECT STATE

Date: 2026-09-23
Phase: Phase 1 — Backend / Mega Proof
Public launch: NO

## Governance

OWNER: project owner.
SUPERVISOR: reviews architecture, defines implementation orders, audits results.
BUILDER: implements only approved orders and reports evidence.

## Current decisions

- Product name: PINK IPTV.
- Customer login remains Username + Password only.
- Client scope remains Xtream-style only; no user DNS/M3U/MAG/Enigma input.
- Existing-line Mega bootstrap for Order 001 is by known `mega_subscription_id` only.
- No Mega username-search endpoint is assumed.
- Mega API token remains backend-only.
- Mega response password and customer Xtream password are never persisted.
- Per-line `dns_link` is authoritative and is never guessed or rewritten to another host/scheme.
- Local mapping has no invented provider status field.
- WireGuard remains a later phase and is not implemented by Order 001.
- Android and Windows remain later phases.
- GitHub remains source of truth.

## Order 001 implementation state

Backend implementation includes:

- FastAPI session-resolve endpoint;
- PostgreSQL/SQLAlchemy mapping model;
- Alembic initial migration;
- Mega GET-subscription-by-id adapter;
- internal idempotent known-ID import CLI;
- outbound `dns_link` safety validation;
- Xtream `player_api.php` authentication/classification;
- maximum five-minute signed PINK session;
- logging redaction controls/tests;
- PostgreSQL migration CI and secret scanning.

## Mega / Xtream proof status

LIVE PROOF: PASSED.

Sanitized live-proof record:

- live Mega retrieve-by-ID: PASS;
- `mega_subscription_id`: `10291720`;
- local mapping persistence and mapping match: PASS;
- Mega password persistence: NO;
- Xtream correct credentials: `SUCCESS`;
- Xtream deliberately incorrect test credential: `INVALID_CREDENTIALS`;
- final remediation: the observed upstream required a stable Xtream User-Agent; the approved implementation uses `PINK-IPTV/0.1`;
- final implementation CI: PASS;
- public launch: NO;
- PR #2: NOT MERGED.

No username, customer password, Mega token, provider hostname, session token, or credential-bearing URL is recorded in this state document.

## Remaining technical gates

1. Supervisor audit/merge decision for Order 001.
2. Only after the Order 001 merge decision: issue any later Android/VPN/Windows order explicitly.

Implementation status: FOUNDATION / MEGA PROOF 001 TECHNICALLY COMPLETE; awaiting only Supervisor decision/merge for PR #2.

## Infrastructure observed and retained

Existing DigitalOcean development/POC environment observed in the architecture baseline:

- Ubuntu 24.04 LTS;
- London region;
- 1 vCPU / 1 GB RAM;
- 25 GB plan disk with approximately 11 GB free at the baseline audit;
- existing Nginx, PostgreSQL, Gunicorn, Python, and Node workloads.

This environment remains POC/staging only. Production VPN use has not been approved, and the production design still expects a dedicated VPN gateway once capacity/isolation are proven. No DigitalOcean service, firewall, routing, deployment, or VPN mutation is part of Order 001/R1.

## Future project gates retained

After the Supervisor merge decision, separate orders are still required for:

1. original PINK design-system/screen-map approval where not already closed;
2. Android shell and later Android Xtream/player work;
3. WireGuard proof with rollback/snapshot, service audit, reconnect and throughput evidence;
4. production VPN capacity/isolation and bandwidth planning;
5. Windows implementation;
6. hardening/distribution and explicit public-launch approval.

Public launch remains: NO.
