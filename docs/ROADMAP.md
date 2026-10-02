# Roadmap

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

Stage 004A dependency/compliance foundation: ACTIVE.

Stage 004A pins the official released Android tunnel dependency, intentionally raises the Android minimum to API 24, verifies the released native payload and proves GoBackend native loadability without creating a real tunnel. No VPN server, peer, OVH, routing, firewall or NAT mutation is part of 004A.

Later Phase 4 gates remain rollback/snapshot prerequisites, existing-service audit, one test peer, real Android tunnel integration, foreground-service/service-survival validation, networking safety, measured bandwidth/capacity and no production onboarding.

## Phase 5 — Windows

Windows shell, backend contract parity, catalog/player, secure storage and separately audited VPN integration.

## Phase 6 — Hardening / Distribution

Release CI/CD, signed builds, dependency/security hardening, observability/privacy review, performance/load testing, backup/restore, accessibility, legal/store review and controlled beta.

Public launch remains NO until explicit Owner/Supervisor approval.
