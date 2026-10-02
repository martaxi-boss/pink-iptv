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

NEXT separately supervised phase; not started by the Phase 3 closure reconciliation.

Required gates remain rollback/snapshot prerequisites, existing-service audit, one test peer, Android tunnel integration, networking safety, measured bandwidth/capacity and no production onboarding.

## Phase 5 — Windows

Windows shell, backend contract parity, catalog/player, secure storage and separately audited VPN integration.

## Phase 6 — Hardening / Distribution

Release CI/CD, signed builds, dependency/security hardening, observability/privacy review, performance/load testing, backup/restore, accessibility, legal/store review and controlled beta.

Public launch remains NO until explicit Owner/Supervisor approval.
