# PINK IPTV - PROJECT STATE

Date: 2026-10-01
Phase: Phase 3 — Android Catalog + Player 003D
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
- Android Shell 002 and Stages 003A/003B/003C are complete and merged. Stage 003D is active for bounded EPG browsing and fail-closed Catch Up on the existing Media3 core.
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

## Android Shell 002 — COMPLETE / MERGED

Order 002 approved head: `6d93c4e8359fc18d97f68b434f2b1b90bb054565`.

Order 002 merge commit: `f83542a31ff7ac0fbd08ceab3531d3d92ee03a10`.

Certified evidence:

- phone device proof: PASS;
- Android TV device proof: PASS;
- Login remains USERNAME + PASSWORD only;
- secure Android Keystore/DataStore credential persistence remains the only credential persistence path;
- public launch: NO.

## Phase 3 / Stage 003A — COMPLETE / MERGED

Stage 003A approved head: `f6ad7a5f53afb7aded8db9e8841ae626f236f5d4`.

Stage 003A merge commit: `135df164de9bb30e66b3b4268fbfc38c8b204e1a`.

Stage 003A implements the in-memory authenticated-provider session, exact backend-resolved Xtream origin handling, direct catalog networking through `player_api.php`, and real Live TV, Movies/VOD and Series browsing states.

## Phase 3 / Stage 003B — COMPLETE / MERGED

Stage 003B approved head: `484b51007ee55ca9fbc5c921bb9bc1b5199a63c4`.

Stage 003B merge commit: `b98ce7a819d6c752c3ec8e5b45646c79fbe2a3d8`.

Stage 003B adds AndroidX Media3 1.11.1 foreground playback for Live TV and Movies/VOD, with credential-contained Xtream source construction, lifecycle-safe ExoPlayer ownership, touch controls, Android TV/D-pad controls, safe player errors and retry.

## Phase 3 / Stage 003C — COMPLETE / MERGED

Stage 003C approved head: `50ca487ccd7f14fd2e5b7be71b3d3d4bed38230b`.

Stage 003C merge commit: `39d5b9e6d43b48c83a966b994d9ffbe66fec5ccb`.

Stage 003C extends the existing authenticated Xtream and Media3 architecture with Series detail, provider-defined seasons, episodes and credential-contained episode playback through the canonical `/series/` path.

Series detail uses `get_series_info` with the exact selected `series_id`, defensive response parsing and phone/TV navigation. Episode playback reuses the Stage 003B player lifecycle and transport; no second player or credential store is introduced.

Real-provider Series info and episode-playback certification remain separate evidence and are not inferred from the merge.

## Phase 3 / Stage 003D — ACTIVE

Stage 003D adds channel-centric Live EPG using authenticated `get_short_epg` and `get_simple_data_table`, conservative Live archive metadata, defensive Base64/plain-text programme parsing, now/next summaries and fail-closed Catch Up eligibility.

Catch Up uses a credential-free typed playback reference and the canonical HLS timeshift path through the existing Media3 1.11.1 player. Credential-bearing EPG requests and timeshift URIs remain internal runtime material.

Real-provider EPG/Catch Up playback proof is NOT CERTIFIED unless separately executed in an approved secure environment.

Favorites/history/continue-watching, VPN/WireGuard and Windows remain unimplemented and outside Stage 003D.

## Remaining technical gates

1. Supervisor audit of Android Catalog + Player 003D.
2. Separate real-provider EPG/Catch Up proof when authorized and securely available.
3. Separate favorites/history stage after 003D.
4. Separate VPN/WireGuard and Windows phases.
5. Production capacity/bandwidth, distribution and explicit public-launch approvals remain future gates.

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
2. Android Phase 3 continuation after Stage 003D, including favorites/history;
3. WireGuard proof with rollback/snapshot, service audit, reconnect and throughput evidence;
4. production VPN capacity/isolation and bandwidth planning;
5. Windows implementation;
6. hardening/distribution and explicit public-launch approval.

Public launch remains: NO.
