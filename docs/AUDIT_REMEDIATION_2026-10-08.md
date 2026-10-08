# PINK IPTV — audit reconciliation and bounded backend remediation

Date: 2026-10-08. Source baseline: `e26749ee396585b7335730f9389406b7b5c52a42` (`main`).
Controller: Project Leader canonical 0.7.0 at
`7f3706f96a43071d797f19774b9eca25ab464e56`.
Public launch: NO.

## Corrected observations

- Backend CI on the exact baseline commit was successful on a development
  branch run 37827363771; this is **not** evidence of a workflow triggered
  from main or an enforced main branch protection rule.
- The historical known-ID-only Mega documentation does not describe current
  code: paged listing / exact username match and known-ID verified import
  are already implemented. This is bounded, not an invented username-query API.
- VPN account slot exhaustion was observed in separate VPS observations:
  five current leases and zero online peers, then natural expiration to four.
  PR47 (Task066) separately contains the HTTP 429 `Retry-After` and safe
  Android `VPN_LIMIT` recovery; it is not incorporated into this branch.
- Address exhaustion is a code-level risk: the VPN installer allocates from
  a fixed pool without reclaiming a historically used address. It has not
  been observed as a deployed failure. Automatic reuse would need gateway
  removal, isolation, durable ownership, and no concurrent address collision;
  this branch intentionally does not change lease/IP ownership.
- Android current product is the complete pinned Extreme 1.9.0 host with
  native PINK WireGuard, not the older `android/` client. Real staging
  certification is not Owner physical phone/TV sign-off.

## Scope of this PR

1. Add shared PostgreSQL-backed login budgets by keyed account digest and
   global attempt count, using transaction-scoped cross-worker locking.
   Deny before upstream traffic; generic 429 with `Retry-After` and no-store.
   No username/password/IP stored in the rate-window table.
2. Cap buffered Mega JSON by streaming at 2 MiB and reject a page exceeding
   the requested list page size; preserve no redirects and secret-safe errors.
3. Correct README, Mega, architecture and security contracts; allow Backend
   CI to run on future pushes to `main` as well as builder branches.
4. Extend migration, cross-account, global, rollover, privacy and oversized
   upstream response regression tests.

## Gates not closed by this change

- Never deploy changed backend before migration `20261008_0003` has been
  applied under a separately authorized rollback-safe operation.
- PR47 integration/deploy and physical owner device acceptance are separate.
  Do not reinstall the existing APK just to free a VPN slot.
- Current main is unprotected; CI execution is not an enforced merge gate.
  Branch protection and required status checks are governance changes.
- Additional VPN endpoint rate limiting, safe address lifecycle/reclamation,
  backup/restore, load testing, third-party license obligations, signed
  distribution, and Windows remain distinct workstreams.
- No VPS peer/database/secret changes, release, or public rollout were made
  by this repository-only remediation.
