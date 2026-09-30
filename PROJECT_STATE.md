# PINK IPTV - PROJECT STATE

Date: 2026-09-30
Phase: Phase 3 — Android Catalog + Player 003B
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
- Per-line `dns_link` is authoritative and is never guessed or rewritten to another host/scheme.
- WireGuard remains a later phase.
- Windows remains a later phase.
- GitHub remains source of truth.

## Foundation / Mega Proof 001 — COMPLETE / MERGED

Order 001 merge: `284b28999cd0e02d07ca94d26a75fce70368a854`.

LIVE PROOF: PASSED.

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

Stage 003A established the in-memory authenticated-provider session, exact backend-resolved Xtream origin handling, direct `player_api.php` catalog networking, and real Live TV, Movies/VOD and Series browsing states.

## Phase 3 / Stage 003B — ACTIVE

Stage 003B adds the first foreground Android playback layer:

- AndroidX Media3 1.11.1;
- Media3 ExoPlayer with the existing redirect-disabled Xtream OkHttp transport;
- credential-contained playback source construction from the runtime provider session;
- canonical Live playback path and validated Movies/VOD playback path;
- Live and Movies item navigation into the player;
- foreground player lifecycle, buffering/error/retry state and resource release;
- touch controls and TV/D-pad focusable controls.

Series episodes/playback, EPG/Catch Up, favorites/history/continue-watching, VPN/WireGuard and Windows are NOT IMPLEMENTED in Stage 003B.

Public launch remains: NO.

## Remaining technical gates

1. Supervisor audit of Android Catalog + Player 003B.
2. Separate Phase 3 real-provider/player device proof gate as required by supervision.
3. Separate Series episode/playback, EPG/Catch Up and favorites/history stages.
4. Separate VPN/WireGuard and Windows phases.
5. Production capacity/bandwidth, distribution and explicit public-launch approvals remain future gates.

## Current development/staging infrastructure

Current approved development/staging host remains OVHcloud. Infrastructure changes are isolated from Stage 003B and the separate infrastructure workstream is not modified by this stage.

Public launch remains: NO.
