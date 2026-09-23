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

LIVE PROOF: NOT PASSED / BLOCKED until an Owner-authorized test line and runtime secrets are available to the Builder in an approved secure execution context.

The project must not mark the Mega/Xtream proof complete based only on mocks/CI.

## Remaining technical gates

1. Run the Owner-authorized live Mega retrieve/import and compare redacted hash evidence.
2. Prove real Xtream `SUCCESS` through exactly the mapped `dns_link`.
3. Prove an incorrect customer credential => `INVALID_CREDENTIALS` with no leakage.
4. Supervisor audit/merge decision for Order 001.
5. Only after Order 001 gate: issue any later Android/VPN/Windows order explicitly.

Implementation status: ORDER 001 AUTOMATED IMPLEMENTATION PREPARED; LIVE_PROOF_BLOCKED.
