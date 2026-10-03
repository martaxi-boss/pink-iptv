# PINK IPTV — final development integration audit, 3 October 2026

## Canonical control

PL_MAIN_SHA=c8c9643bfa39e746079043cfd83c1aae349b0462
PL_PLUGIN_VERSION=0.5.0
NEW_TASK_SCHEMA_VERSION=2.0
WORKER_RESULT_SCHEMA_VERSION=2.0
CI_WAIT_STATE=WAITING_EXTERNAL_CI

This continuation uses the live canonical Project Leader documents, Skill, central policy and executable validators. The original immutable tasks retain their original a8eeff8f25ef9f632ed2542c167b2137a439f705 policy pin; the central policy bytes are unchanged. Their authorization records are not rewritten to pretend they began under a later control revision. Current canonical audit logic is c8c9643bfa39e746079043cfd83c1aae349b0462.

## Reconstructed live state and current authority

Main remains 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4.

| PR | Live state | Actual base | Certified integration/head | Required current CI |
| --- | --- | --- | --- | --- |
| 16 | MERGED / CLOSED | builder/wireguard-004b-identity-permission-adapter | merge 53a208a5fe5a87a960d073f51df1f65074de880e | Post-integration Android 37108519453 and Backend 37108519467 SUCCESS |
| 17 | MERGED / CLOSED | builder/android-stale-catalog-001 | merge 1e6ee4001c55cb04320773416a49b4e737ea9b14 | Post-integration Android 37109115657 and Backend 37109115774 SUCCESS |
| 13 | OPEN / mergeable | main | head 53a208a5fe5a87a960d073f51df1f65074de880e | Android/Backend SUCCESS at this exact head |
| 14 | OPEN / mergeable | main | head 9f84e6c5789f1c9f019f5eccce570076c78e1e25 | Backend 37080066858 SUCCESS at this exact head |
| 15 | OPEN / mergeable | main | head 1e6ee4001c55cb04320773416a49b4e737ea9b14 | Android/Backend SUCCESS at this exact head |
| 4 | OPEN / not mergeable / dirty | main | head 208760bd06901a3de326b3dfcb36c9a766a8ede0 | Historical conflicting infrastructure documentation; outside this active remediation workstream |

The current Owner instruction explicitly authorized the development integrations of PRs16/17. Their actual bases were freshly checked before merging with expected_head_sha enforcement and normal merge commits. No merge-to-main Human Gate was falsely applied to them. Main merges of PRs13/14/15 are still not authorized. PR4 was inspected separately: its older infrastructure proposal includes Remote Desktop Commander and backup assumptions superseded by current project policy; being open does not make it an active construction order. It is not merged or closed by this continuation.

## Independent Supervisor evidence audit

Both original Task Authorization/Worker Result pairs use v2. Their authorization-only commits change exactly their own task file. Exact authorization bytes/digests, authorization -> implementation -> current task-branch ancestry, central scope/protected paths, required validation/CI and referenced run repository/name/SHA/completed-success were verified through freshly fetched GitHub connector payloads and canonical Python functions. Historical v1 records on the parent branches remain historical; no retroactive v2 certification is claimed.

The actual merged PR16 tree equals source head 5ded25148edbc250fe937679dd39371ce8da2f2e. Its merge parents are 65d7491e3d69878a0a3b4b0256d844219f3a1fea and 5ded25148edbc250fe937679dd39371ce8da2f2e. The actual merged PR17 tree equals source head df66332caa26668c6e8049c4266a0de91240683e. Its merge parents are f30faa0080fef20f4e477d219630914d8656422d and df66332caa26668c6e8049c4266a0de91240683e. GitHub merged=true, parent branch heads and ancestry were independently read. Neither merge introduces content beyond the certified source tree.

Current post-integration Android/Backend runs above are completed/success at the exact merged head, with run_attempt=1. Successful push runs were also read. Relevant device evidence was inspected directly:

- PR13/API24 job 111162812882: app startup, official native library, installation identity/Keystore and manifest policy PASS.
- PR13/phone job 111162812897: WG_SETTINGS_TOUCH_AND_SIMULATED_TV=PASS, native/identity/manifest PASS, PHONE_LAUNCH_SMOKE=PASS, PHONE_CRASH_ANR=NONE, PHONE_DEVICE_PROOF=PASS.
- PR15/phone job 111164488947: instrumentation/Keystore/native PASS, PHONE_LAUNCH_SMOKE=PASS, PHONE_CRASH_ANR=NONE, PHONE_DEVICE_PROOF=PASS.

There is no project-local trusted v2 gate at the audited base. This is an external canonical audit, not a claim that a target trusted PR gate ran. Simulated TV proof does not certify later physical-TV surfaces. No secure real-provider playback proof is inferred.

## Recovery audit and chronology limitation

Source-head Android run 37089068739 had a failed first attempt and a successful second attempt at df66332caa26668c6e8049c4266a0de91240683e. Actual successful phone logs were read; the original failure's root cause remains unproven. Only the failed job was retried, after the completed failure and logs were read; no active run was redispatched.

The append-only journal exists at source task head 550d22028fb637aaaa1c0833d6f16fccb9f6a10c under .project-leader/recovery-events/PINK-IPTV-ANDROID-SESSION-LIFECYCLE-006/0001.json through 0003.json. It contains FAILURE_OBSERVED, RETRY_AUTHORIZED and RECOVERED, with contiguous sequence, canonical hash chain and bounded counters. No strategy change occurred, so REPLAN is not applicable. Events were read back from GitHub, not accepted from chat memory. These recovery records are on the original task branch after the source PR closed; they are not claimed to be present in the earlier merged parent head.

The current canonical full GitHub evidence verifier was executed using real connector-fetched payloads on an ephemeral audit index bound to the retried source SHA and the journal-bearing current task head. It accepts the valid journal and rejects the same retried run when the journal payload is removed. This audit index does not rewrite the original Worker Result or fabricate earlier durable approval.

Timing is explicitly qualified: GitHub attempt2 started at 2026-10-03T08:03:57Z; the first journal commit was created at 2026-10-03T08:16:26Z. Thus this historical retry does not prove that FAILURE_OBSERVED/RETRY_AUTHORIZED were durably persisted before rerun. The events are retained truthfully; no timestamp is backdated and no unnecessary rerun is performed to manufacture prior approval. Under the current protocol, every future retry must persist those events before rerun. The fresh post-integration acceptance runs are first attempts and do not rely on this late journal to satisfy their CI gates.

## Generic control-plane findings

The earlier managed scope-containment inconsistency is RESOLVED at the current canonical revision. A contained exact-file managed scope now passes the canonical cross-repo verifier, which imports the same control.scope_policy helper as the trusted gate.

A remaining generic limitation is RECOVERY_CAUSALITY_NOT_ENFORCED: current control/verify_github_evidence.py requires event presence, identity and a valid journal chain, but does not prove that the RETRY_AUTHORIZED commit preceded the certified retry or bind each recovery episode mechanically to that exact run/attempt. The real late journal above passes the current verifier, demonstrating the gap between the documented pre-rerun rule and executable enforcement. A universal correction should verify episode/run/attempt binding and GitHub commit/attempt chronology, with negative regressions for a journal created after dispatch or for a different run. No PINK-specific exception is proposed, and the control repository is not modified in this managed-project task.

## Authorized scope closure and real gate

Development integrations16/17 and their post-integration validation are complete. The next active integration effects are PRs13/14/15 into main. These actual base=main transitions require separate exact-revision Owner authorization; this continuation stops at HUMAN_GATE_MAIN. No main merge, release, deploy, destructive action, production credential change, irreversible infrastructure mutation or paid-service action occurs.

Original product boundaries remain: Xtream username/password only; backend-authoritative origin; PINK-owned UI; no real WireGuard tunnel/peer/server/OVH mutation; existing player/library/database/dependency architecture. Later WireGuard runtime, Windows, secure provider/physical-device validation and distribution retain their separate scope prerequisites. The complete production Android/Windows/VPN product is not claimed finished.
