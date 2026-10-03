# Convergence preflight 007 — development composition

Status: VALIDATION_PENDING. This report supersedes the stopping decision in AUDIT_CANONICAL_CONTINUATION_2026-10-03.md and the older development integration snapshot. It does not certify main promotion.

## Control
Canonical Project Leader: [pinned contract](https://github.com/martaxi-boss/Project-leader/blob/c426edc709cfe6538489bdd84c2eeb4bb10bec05/PROJECT_LEADER.md). Plugin 0.5.0; Task Authorization and Worker Result 2.0. Apply DETECT -> AUDIT -> CORRECT -> VALIDATE -> CONTINUE, convergence preflight before a real Human Gate, and causal Recovery. Active CI is WAITING_EXTERNAL_CI.

## Exact composition
- PR13: WireGuard 004B identity, permission and settings preparation at 53a208a5fe5a87a960d073f51df1f65074de880e.
- PR14: backend validation/privacy at 9f84e6c5789f1c9f019f5eccce570076c78e1e25.
- PR15: stale catalog and session lifecycle at 1e6ee4001c55cb04320773416a49b4e737ea9b14.
- Resolve workflow overlap by retaining PR13 Android CI (API24 and all eight phone instrumentation classes) and Backend CI unchanged.
- Retain PR13 strict v2-aware scanner; the older PR14 scanner is superseded.
- Import backend main/validation tests/security documentation and catalog/series/session controllers and tests verbatim. No new product stage, real tunnel, server mutation or production action.
- Immutable authorization 007 preceded this implementation on a separate commit. Historical authorizations/results/checkpoints are retained as snapshots; their old PASS fields do not certify this combined HEAD.

## Historical recovery reconciliation
Task 006's source journal remains immutable at [source branch snapshot](https://github.com/martaxi-boss/pink-iptv/tree/550d22028fb637aaaa1c0833d6f16fccb9f6a10c/.project-leader/recovery-events/PINK-IPTV-ANDROID-SESSION-LIFECYCLE-006).
Android run 37089068739 attempt 2 started 2026-10-03T08:03:57Z. FAILURE_OBSERVED and RETRY_AUTHORIZED were recorded at 08:11:40Z and persisted afterwards. RETRY_AUTHORIZED is RETROACTIVE_INVALID: that journal cannot certify causal Recovery under the current contract. No backdating or rewriting is permitted. A fresh combined run under task 007 must independently certify this composition; it cannot rehabilitate the historical retry.

## Outstanding preflight
Exact tree/scope and independent Supervisor audit, combined Android/backend CI and API24/API36 device evidence, final evidence record, development integration and post-integration audit remain required. PR14/15 may be reconciled only after containment is proven. Main remains unchanged; PR4 infrastructure documentation is outside this workstream and retains its separate authority requirements.
