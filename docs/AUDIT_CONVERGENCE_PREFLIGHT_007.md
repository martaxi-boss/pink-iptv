# Convergence preflight 007 — development composition

Status: DEVELOPMENT_COMPOSITION_CERTIFIED; final metadata HEAD CI and independent acceptance audit remain required before promotion. This report supersedes the stopping decision in AUDIT_CANONICAL_CONTINUATION_2026-10-03.md and the older development integration snapshot. It does not certify main promotion.

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

## Post-integration audit and durable state

PR18 merged into the PR13 development branch at fbcad5f3e47c6388935d2f87981922a9bfd43119. Its tree4501de0 is exactly the initial combined tested tree. Both source heads are ancestors of this integration. PR14/15 were closed as absorbed, not merged to main; their branches and records remain intact. Main remains 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4.

Post-integration Android [37114668507](https://github.com/martaxi-boss/pink-iptv/actions/runs/37114668507) succeeded on attempt2; Backend [37114668515](https://github.com/martaxi-boss/pink-iptv/actions/runs/37114668515) succeeded on attempt1. Actual API24 and API36 logs were audited. API24 startup/native/Keystore/manifest/crash-ANR passed. API36 touch/simulated-TV settings, native/identity/manifest, launch and crash-ANR passed; physical TV and real-provider/tunnel certification are not inferred.

### Causal Recovery 007

Attempt1 phone job111179913592 passed all eight instrumentation classes and launch Status:ok, then exited255 at the process-presence probe. Root cause remains unproven; transient emulator/launch behavior was inferred from the exact same tree's initial successful device proof. No product code or protected workflow was changed.

| Durable event | Commit time UTC | Relation to attempt2 |
| --- | --- | --- |
| FAILURE_OBSERVED | 10:14:00 | Before retry |
| RETRY_AUTHORIZED | 10:15:06 | Before retry |
| GitHub retry start | 10:15:25 | Only failed jobs rerun |
| RECOVERED | 10:27:21 | After successful run and log audit |

Journal: .project-leader/recovery-events/PINK-IPTV-CONVERGENCE-PREFLIGHT-007/. Every event was created once and never rewritten. Current canonical validation of hash chain, GitHub commit persistence times and causal ordering passed. Retry phone job111182289406 emitted PHONE_DEVICE_PROOF=PASS, PHONE_LAUNCH_SMOKE=PASS, PHONE_CRASH_ANR=NONE and WG_SETTINGS_TOUCH_AND_SIMULATED_TV=PASS.

### Historical evidence index

| Record lineage | Current classification |
| --- | --- |
| 002 scanner-control task/checkpoint; 003 scanner-script task/checkpoint | Preserved v1 legacy snapshots; not current v2 certification |
| WireGuard CI recovery001 and settings proof004 task/checkpoint | Preserved v1 legacy snapshots |
| Backend validation/privacy001 and Android stale-catalog001 task/results | Preserved v1 snapshots; code incorporated into new task007 composition |
| WireGuard stale-preparation005 task/result | Immutable v2 historical implementation certification; not a certificate for the combined HEAD |
| Android session-lifecycle006 task/result | Immutable v2 historical implementation snapshot; later source-head retry journal is RETROACTIVE_INVALID |
| Convergence preflight007 | New immutable v2 authorization, fresh combined CI, causal append-only recovery and externally verified Worker Result |

Canonical c426 rejected the historical006 late authorization on real GitHub payloads. That event remains immutable at its original source snapshot. Fresh task007 proof does not rehabilitate historical Recovery.

### Live workstream reconstruction

- PR2/3/5–12: closed historical main integrations for foundation, Android and WireGuard004A.
- PR16/17: closed development integrations; no main merge was inferred from their non-main bases.
- PR18: merged development composition.
- PR14/15: closed as absorbed into the composition, with heads and branches retained.
- PR13: single combined promotion candidate against main, replacing an unsafe sequence of independent overlapping promotions.
- PR4: open legacy infrastructure documentation; conflicts/drift and separate infrastructure authority keep it outside this composition. It was preserved.
- Issue1: historical foundation/architecture context; it grants no new product stage.
- All relevant branches and PR HEAD/base states were read from GitHub; bootstrap snapshots were treated as historical.

### Scope and enforcement

Independent reconstruction from source trees found zero unexpected blobs. Task007 changes are contained by the central policy and its narrower scope at the actual authorized development base53a208a. All existing task/result/checkpoint bytes remain unchanged. The full main candidate additionally carries inherited Stage004B workflow changes already present in PR13; task007 did not edit those protected blobs or create a workflow governance grant.

Trusted canonical c426 Python checks were executed with live connector-fetched base64 file bytes, GitHub comparisons, runs and journal commit histories. Authorization-only commit eac641f precedes implementation; authorization digest and current bytes match. The target has no project-local trusted gate; external canonical Supervisor audit is used and no local-gate PASS is fabricated.

## Final acceptance preflight

Covered code, overlap, evidence and development reconciliation work is complete. The Worker Result binds the exact certified integration implementation SHA and runs above. This report/result/state commit changes evidence only; its exact final HEAD must independently pass current CI, retain the authorization and journal chain, preserve every app/backend/workflow/scanner blob, and be re-audited in the live PR before any terminal acceptance or Human Gate.

Final exact-head audit and CI receipts are published in PR13's evidence section after completion, without another product mutation. No main promotion, release, production deploy, real provider/tunnel, OVH/server/peer, destructive or paid-service action is authorized or performed. No Project Leader control-plane mutation or PINK-specific control-plane adaptation was made.
