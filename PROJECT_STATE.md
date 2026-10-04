# PINK IPTV - PROJECT STATE

Date: 2026-10-04
Phase: Phase 4 — WireGuard Stage 004B — COMPLETE / MERGED; runtime preflight preparation
Public launch: NO

## Current runtime continuation 023

Current control source: Project Leader05225695684b54ce5becbb89cf0e5b6dc88dcafa (skill0.6.2). Starting PINK main:0bc2b751c77da5702d8cfb24996d046e5f1829fc. The older audit sections below preserve historical acceptance evidence.

Separately bounded VPS tasks021/022 recovered native Actions SSH execution and identified runtime Git HEAD ccccaf1eb25ca80936763f7d6a17e4dd00347e8a, the proof unit as a headless Android emulator, and zero WireGuard interfaces. Task024 corrected the observed staging validation-input echo using only the already merged backend privacy handler; protected source backup, generic422/no-store response, OpenAPI200, PostgreSQL readiness and existing active services were verified. Runtime Git HEAD remains historical with a documented single-file source overlay; this is not full-main deployment or real-tunnel proof.

See [runtime audit023](docs/OVH_RUNTIME_AUDIT_023.md) for exact runs/hashes/provenance and remaining network/rollback work. Direct session SSH absence no longer blocks continuation: use separate bounded VPS operations tasks through repository-return diagnostics. Stage004B is COMPLETE/MERGED. Current docs require their own exact CI/result and promotion evidence; this section does not predict acceptance. Physical-TV/provider proof, real tunnel and capacity remain unverified. Public launch:NO.

## Current canonical audit / continuation 014

Canonical Project Leader: `66db27d8118cfa527a21466b8a5034acd5b4395e`.
Live starting main: `84fe2dfffd4d381f45bd9b88a0983c122ba42ee4`.
Audited PR13 development composition: `196e50895d8239724352ef2d7e1fb8ac6fec754e`.

004B identity, Android VPN permission and read-only backend adapter are implemented. Backend validation privacy and stale Android session/catalog/series/preparation fixes are part of the same composition. No real tunnel has been implemented or certified. Task014 reconciles this state and supplies the independent audit in [canonical audit](docs/AUDIT_CANONICAL_014.md).

Task012 hygiene is complete and integrated through PR23. Its certified implementation `2885d6851d9ac79e67d6a9ed87d4682877514793` passed Android CI `37143358663`/`37143356834` and Backend CI `37143358661`/`37143356899`; all four exact-SHA contexts were re-read successfully. The development final head above differs only by Task012 result metadata. Task014 documentation changes require their own fresh implementation certificate; see its durable Worker Result and live PR receipt for actual acceptance. Preparation text is not future PASS evidence.

Task008 certification/recovery is closed, superseded for the later documentation composition by Task012. Task007's old-SHA recovery and Task006 retrospective authorization remain historical, non-certifying records. Preserve them; do not resume their old next steps or reinterpret them as current grants.

PR4 and rejected PR22/24 are closed unmerged. PR14/15 changes were absorbed through development PR18; PR19/20 are closed. PR13 is the sole application promotion candidate. Current consequential effects resolve through the canonical standing-authority contract and exact transition records, rather than an automatic merge Human Gate. No release, deployment, provider, OVH, peer, secret or paid effect follows from this audit.

PR13 was merged normally at `71a7b7ff88a0b66eab5857a6e9199c12fb9fb89a`; receipt PR26 was merged at `43829215cceeccb3f0f2b4b99c139ab63ce4c9cb`. Exact prior transition authorizations remain durable on `builder/canonical-transitions-014`; the Task014 Worker Result and main transition result are on main. Task016 now prepares the read-only runtime preflight and corrects public transition SHA scanner compatibility after rejected PR27/Task015. Its own CI/result controls must pass; this state text does not predict their success. Real-provider and physical-TV evidence remains outstanding. Next WireGuard server/tunnel work first requires current service audit, rollback evidence and an executable authorized access path.

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
- WireGuard Phase 4 is active under staged supervision. Stage 004A dependency/compliance foundation is COMPLETE / MERGED; Stage 004B is limited to device-install identity, Android VPN permission and a read-only GoBackend adapter. No real tunnel is authorized in 004B.
- Android Shell 002 and Stages 003A/003B/003C/003D/003E/003F are complete and merged. Phase 3 Android implementation is COMPLETE / MERGED. Phase 4 WireGuard is active in separately supervised stages; implementation completion does not imply production or public-launch approval.
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

## Phase 3 / Stage 003D — COMPLETE / MERGED

Stage 003D approved head: `ba3cbf66c85bf30381aef809cb0169b5ce03ad86`.

Stage 003D merge commit: `344166836661a7b340750fd921036e8b2ff0c671`.

Stage 003D adds channel-centric Live EPG using authenticated `get_short_epg` and `get_simple_data_table`, conservative Live archive metadata, defensive Base64/plain-text programme parsing, now/next summaries and fail-closed Catch Up eligibility.

Catch Up uses a credential-free typed playback reference and the canonical HLS timeshift path through the existing Media3 1.11.1 player. Provider-start validation is timezone-independent by using UTC only as a neutral strict calendar validator; provider textual time is not converted or shifted.

Real-provider EPG/Catch Up playback proof remains NOT CERTIFIED unless separately executed in an approved secure environment.

## Phase 3 / Stage 003E — COMPLETE / MERGED

Stage 003E approved head: `0c5fcecc713a7936d748ff5cd4d73c2743edb98c`.

Stage 003E merge commit: `da8164045280f05cb3467ac0da025128a65de9dd`.

Stage 003E implements a device-local, account-isolated media library using Room 2.8.5 with KSP 2.3.12 and schema version 1.

Favorites cover Live, Movies/VOD and Series. Recent history covers Live, Movies/VOD and Series Episodes. Continue Watching is limited to eligible seekable Movies/VOD and Series Episodes with controlled progress persistence and typed resume through the existing Media3 1.11.1 player.

The local profile partition is derived from the exact authenticated username as a SHA-256 digest under the fixed PINK namespace; plaintext usernames, passwords, provider origins and credential-bearing media URLs are not stored in Room. Logout clears visible/in-memory library state without deleting persisted profile rows.

Catch Up history persistence remains NOT IMPLEMENTED.

## Phase 3 / Stage 003F — COMPLETE / MERGED

Stage 003F approved head: `b2e1fa406c55514b43d19fb98ffe332da9563ed8`.

Stage 003F merge commit: `edf93ce866404c16ddc51659608643e2c3aa1e73`.

Certified automated/device evidence for the approved 003F head:

- Android CI `36925014754`: SUCCESS;
- backend CI `36925014938`: SUCCESS;
- phone-device-proof: SUCCESS;
- JVM tests, lint, instrumentation-source assembly, startup/login/navigation regression, Keystore proof, Room runtime smoke and secret scanning: PASS where applicable.

No post-merge CI is inferred.

Stage 003F adds Global Search for Live TV, Movies/VOD and Series by reusing the existing authenticated `get_live_streams`, `get_vod_streams` and `get_series` catalog operations.

Search does not introduce a provider search endpoint. The catalog index, query, filters and result set are runtime/in-memory only, are cleared when the authenticated provider session is invalidated, and are never persisted to Room, DataStore, the filesystem or the backend.

Matching is local, case-insensitive and accent/diacritic-insensitive with deterministic exact/prefix/contains ranking and bounded rendered results. Search reuses the existing Live/VOD Player, Series Detail and local Favorites architecture.

Room remains schema version 1. Media3 remains 1.11.1.

## Phase 3 Android implementation closure

Phase 3 — Android Xtream + Player — IMPLEMENTATION STATUS: COMPLETE / MERGED.

Implemented Phase 3 scope includes:

- authenticated provider runtime;
- Live TV, Movies/VOD and Series catalogs;
- Series detail, seasons and episodes;
- Live, VOD and Series episode playback through Media3;
- EPG and Catch Up;
- Favorites, Recent history, Continue Watching and resume position;
- Global Search;
- phone UX;
- Android TV/D-pad implementation with deterministic test coverage.

Evidence distinction is preserved:

- Foundation / Mega/Xtream authentication proof: CERTIFIED;
- later Phase 3 real-provider proof for Live playback, VOD playback, Series info/episode playback, EPG retrieval, Catch Up playback and Global Search catalog sources: NOT CERTIFIED / NOT EXECUTED WITH SECURE CREDENTIALS where applicable;
- Android Shell 002 Android TV emulator proof: CERTIFIED / PASS; physical-TV proof is not established by that emulator evidence;
- physical-TV proof for later Phase 3 feature surfaces (Live/VOD player, Series detail/episode player, EPG/Catch Up, Favorites/History/Continue Watching and Global Search): NOT SEPARATELY CERTIFIED.

Deterministic Android TV/D-pad source tests and CI compilation do not equal physical-TV certification. The outstanding provider/device items are residual validation gates, not known code blockers.

PUBLIC LAUNCH = NO.
DEPLOY = NO.
RELEASE = NO.

Phase 4 — WireGuard is active under staged supervision. Stage 004A is COMPLETE / MERGED and Stage 004B is COMPLETE / MERGED. Later gates remain snapshot/rollback, existing-service audit, one test peer, first real Android tunnel activation, networking safety, measured bandwidth and no production onboarding.

## Remaining technical gates

1. Secure, separately authorized real-provider validation for later Phase 3 surfaces: Live playback, VOD playback, Series info/episode playback, EPG retrieval, Catch Up playback and Global Search catalog-source proof.
2. Separate physical-TV validation for post-Shell Phase 3 feature surfaces where required.
3. Phase 4 — WireGuard, under a separate Supervisor order and its own rollback/network/capacity gates.
4. Phase 5 — Windows.
5. Phase 6 — hardening/distribution, final store/distribution assets, production/provider/device validation and explicit public-launch approval.

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

1. Phase 4 — WireGuard proof with rollback/snapshot, existing-service audit, one test peer, Android tunnel integration, networking safety and measured throughput;
2. production VPN capacity/isolation and bandwidth planning before production onboarding;
3. Phase 5 — Windows implementation;
4. Phase 6 — hardening/distribution, final store/distribution assets and explicit public-launch approval;
5. future production/provider/device validation for evidence that remains not separately certified.

Public launch remains: NO.


## Phase 4 / Stage 004A — COMPLETE / MERGED

Stage 004A approved head: `6d6611d8fd0cc784770fb9ec56d025bd87963b0a`.

Stage 004A merge commit: `84fe2dfffd4d381f45bd9b88a0983c122ba42ee4`.

Stage 004A pins `com.wireguard.android:tunnel:1.0.20260102`, raises Android minimum support to API 24, enables core-library desugaring, preserves only `libwg-go.so` in the PINK APK and proves official GoBackend native loadability without requesting VPN permission or establishing a tunnel.

## Phase 4 / Stage 004B — COMPLETE / MERGED

Stage 004B adds device-install-scoped WireGuard identity, a dedicated Android Keystore AES-256-GCM wrapping key (`pink_iptv_wireguard_identity_v1`), dedicated `pink_wireguard_identity` DataStore persistence, explicit Android `VpnService.prepare()` permission handling, a PINK-owned preparation state machine and a thin read-only GoBackend adapter using tunnel name `pink`.

Identity is independent from Xtream accounts and survives IPTV logout. Corrupt encrypted identity fails closed and is never silently rotated. Public UI state contains no private key, PSK, provider URL or IPTV credential.

The merged official GoBackend VpnService is explicitly marked `SUPPORTS_ALWAYS_ON=false` for this stage. 004B does not execute `Backend.setState(... UP ...)`, build a real WireGuard Config, create a peer, contact OVH, modify routing/firewall/NAT or establish a real VPN tunnel.

Public launch: NO.
