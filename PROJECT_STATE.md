# PINK IPTV - PROJECT STATE

Date: 2026-09-24
Phase: Phase 2 — Android Shell
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
- WireGuard remains a later phase and is not implemented by Order 001 or Order 002.
- Android Shell 002 is the active implementation phase; direct Xtream catalog/provider networking remains future work.
- Windows remains a later phase.
- GitHub remains source of truth.

## Foundation / Mega Proof 001 — COMPLETE / MERGED

Order 001 merge: `284b28999cd0e02d07ca94d26a75fce70368a854`.

Backend implementation state

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
- PR #2: MERGED.

No username, customer password, Mega token, provider hostname, session token, or credential-bearing URL is recorded in this state document.

## Android Shell 002 implementation state

Order 002 is authorized and is being implemented on `builder/android-shell-002`.

Prepared scope includes the Android project shell for phone/tablet, Android TV and TV Box; Splash, Login, Home and the Live TV, Movies, Series, EPG, Favorites and Settings shell routes; PINK Backend session client; secure Android Keystore/DataStore credential storage; D-pad/focus behavior; automated Android tests and Android CI.

Order 002 status: IMPLEMENTATION PREPARED / IN REVIEW. It is not merged and must not be described as complete until Supervisor audit and merge.

## Remaining technical gates

1. Android Shell 002 build/test/CI evidence and Supervisor audit/merge decision.
2. Separate future orders for direct Xtream catalog/networking, player/Media3, VPN/WireGuard and Windows.
3. Production capacity/bandwidth, distribution and explicit public-launch approvals remain future gates.

## Current development/staging infrastructure

Current approved development/staging host:

- provider: OVHcloud;
- host: `vps-32bea5b6`;
- OS: Ubuntu 24.04 LTS;
- 4 vCores;
- approximately 8 GB RAM;
- approximately 72 GB root filesystem;
- KVM virtualization available for Android emulator validation.

The previous DigitalOcean 1 vCPU / 1 GB RAM / 25 GB environment is no longer an operational dependency or design constraint. Its historical resource limits must not drive application, dependency, test, or tooling decisions.

The current OVH host remains development/staging. Public launch is not approved. Production VPN use remains a future gate, and production VPN capacity must be measured before any user/stream capacity claim. The production architecture may still use a dedicated VPN gateway when capacity/isolation evidence justifies it.

## Future project gates retained

Beyond Android Shell 002, separate orders are still required for:

1. final distribution/store artwork and design assets;
2. Android direct Xtream catalog/provider networking and player work;
3. WireGuard proof with rollback/snapshot, service audit, reconnect and throughput evidence;
4. production VPN capacity/isolation and bandwidth planning;
5. Windows implementation;
6. hardening/distribution and explicit public-launch approval.

Public launch remains: NO.
