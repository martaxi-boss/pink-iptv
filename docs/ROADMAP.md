# Roadmap

## Current client migration042

Owner selected the complete Extreme InfiniTV client as the new application
foundation. Current build covers PINK identity/launcher, PINK-only managed login,
protected account restore and reuse of all upstream catalogs/player/layout.
The earlier native-client implementation history remains valid for that client;
it does not certify the replacement Tauri host.

Next acceptance: phone/TV installation, restored account, real Live/VOD/Series
playback and navigation responsiveness. Then port the approved WireGuard client
contract into the new host. Public launch remains NO. Exact status and source:
[migration042](EXTREME_MIGRATION_042.md).


## Phase 0 — Architecture

Architecture baseline and Supervisor-controlled builder orders. Public launch: NO.

## Phase 1 — Backend / Mega Proof

- FastAPI backend foundation
- PostgreSQL subscription mapping + Alembic migration
- Mega retrieve-by-ID adapter
- internal known-ID import/bootstrap
- exact mapped `dns_link` Xtream authentication
- `/v1/session/resolve`
- automated security/migration tests
- Owner-authorized live Mega/Xtream proof

Gate: real authorized line proves mapping match, Xtream `SUCCESS`, and wrong-password `INVALID_CREDENTIALS` without secret leakage.

## Phase 2 — Android Shell

Android project shell, original PINK theme, login/home shell, backend API client and secure local credential storage. No full player gate is opened by Phase 1 alone.

## Phase 3 — Android Xtream + Player

Implementation status: COMPLETE / MERGED.

Implemented scope includes authenticated Xtream runtime, Live/VOD/Series catalogs and playback, Series detail/seasons/episodes, EPG/Catch Up, Favorites/History/Continue Watching/resume, Global Search, phone UX and Android TV navigation/focus. Residual real-provider and post-Shell physical-TV evidence remains separately certifiable and does not convert implementation closure into production readiness.

## Phase 4 — WireGuard

Stage 004A dependency/compliance foundation: COMPLETE / MERGED.

Stage 004A pins the official released Android tunnel dependency, intentionally raises the Android minimum to API 24, verifies the released native payload and proves GoBackend native loadability without creating a real tunnel. Approved head: `6d6611d8fd0cc784770fb9ec56d025bd87963b0a`; merge: `84fe2dfffd4d381f45bd9b88a0983c122ba42ee4`.

Stage 004B identity/permission/adapter: COMPLETE / MERGED.

004B creates/reuses only device-install local identity after explicit Android VPN authorization, protects private key material with a dedicated Keystore/DataStore boundary, adds preparation-state/UI/permission plumbing, keeps always-on disabled and closes the API24 lower-bound runtime smoke. No real WireGuard Config, tunnel activation, peer, VPN server, OVH, routing, firewall or NAT mutation is authorized.

Later Phase 4 gates remain rollback/snapshot prerequisites, existing-service audit, one test peer, first real Android tunnel integration, foreground-service/service-survival validation, networking safety, measured bandwidth/capacity and no production onboarding.

## Phase 5 — Windows

Windows shell, backend contract parity, catalog/player, secure storage and separately audited VPN integration.

## Phase 6 — Hardening / Distribution

Release CI/CD, signed builds, dependency/security hardening, observability/privacy review, performance/load testing, backup/restore, accessibility, legal/store review and controlled beta.

Public launch remains NO until explicit Owner/Supervisor approval.
