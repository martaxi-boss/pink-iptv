# Roadmap

## Current certified Android integration (2026-10-06)

The complete Extreme InfiniTV-based PINK Android client and native WireGuard
integration were integrated into main through Task055 (commit
`8ac154b8578d55cde248afd21dc3afea43c7a7ee`), followed by its
certification-record preservation commit
`a90138b650d6d3415e2fc713fba534bc8d22df49`.

The Task055 Worker Result records exact-source Android and Backend CI success,
plus a separate real staging proof of username/password login, catalogs,
decoded native Live audio/video through WireGuard, normal Android VPN consent,
cold restoration and reconnect/roaming. See
`.project-leader/results/PINK-IPTV-EXTREME-LIVE-CERTIFICATION-055.json`.
These are scoped staging/certification observations, not evidence of Owner
physical-phone/TV acceptance, public release, production deployment, broad
provider compatibility, or Windows completion.

Remaining work must be selected from fresh live GitHub/runtime evidence and
independent acceptance gaps, not resumed from superseded migration042 text.
The earlier Android client and migration042 history remain retained as
recovery/provenance evidence. Public launch remains NO.

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
