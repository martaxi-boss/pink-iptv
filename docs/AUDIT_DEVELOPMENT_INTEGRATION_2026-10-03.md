# PINK IPTV — development integration audit, 3 October 2026

## Live control verification

PL_MAIN_SHA=5c96a3c84444e42256e04629c631c364313f3582
PL_PLUGIN_VERSION=0.5.0
NEW_TASK_SCHEMA_VERSION=2.0
WORKER_RESULT_SCHEMA_VERSION=2.0
CI_WAIT_STATE=WAITING_EXTERNAL_CI

The live canonical Skill requires inspecting the PR base branch. Only an actual `main` base is the merge_to_main Human Gate. The current Owner instruction explicitly authorizes integrating certified PRs 16 and 17 into their existing development bases. This supersedes the earlier task-terminal wording forbidding any merge; immutable historical task/result records are preserved as the evidence of their original completed implementation cycles.

## Revalidation before integration

- Main re-read: 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4.
- PR16 source head: 5ded25148edbc250fe937679dd39371ce8da2f2e; base builder/wireguard-004b-identity-permission-adapter at 65d7491e3d69878a0a3b4b0256d844219f3a1fea.
- PR17 source head: df66332caa26668c6e8049c4266a0de91240683e; base builder/android-stale-catalog-001 at f30faa0080fef20f4e477d219630914d8656422d.
- Canonical v2 task/result validation, central-policy scope, authorization-only commits, exact authorization SHA-256, authorization -> implementation -> final-head ancestry and referenced run repository/name/SHA/completed-success were freshly verified for both PRs with live GitHub connector payloads and canonical trusted Python validators.
- No protected workflow or secret files appear in either nested PR diff. There is no project-local trusted v2 gate; the audit is external and does not claim a trusted target gate ran.
- PR16 final source head CI: Android 37089379075 and Backend 37089375812 completed success.
- PR17 final source head: Backend 37089064954 success; Android 37089068739 attempt 1 failed after all phone instrumentation suites and activity launch succeeded, at the process-presence probe. The log does not capture a root cause. Attempt 2 retries only the failed phone job after reading its completed failure and logs. No active run was retried and no product/workflow code was changed based solely on that unexplained failure.

## Integration in progress

PR16 is merged into its development base at 53a208a5fe5a87a960d073f51df1f65074de880e. GitHub merged=true, parent PR13 head, exact merge parents and original-head ancestry were independently re-read. The merge tree equals the certified source tree. Post-integration Backend CI 37108519467/37108515571 and push Android CI 37108515562 succeeded; Android PR run 37108519453 is still executing and includes the device-proof gates.

PR17 source-head Android CI 37089068739 attempt 2 completed success. Actual job 111161437836 logs show PHONE_LAUNCH_SMOKE=PASS, PHONE_CRASH_ANR=NONE and PHONE_DEVICE_PROOF=PASS. PR17 is now merged into builder/android-stale-catalog-001 at 1e6ee4001c55cb04320773416a49b4e737ea9b14. GitHub merged=true, parent PR15 head, exact merge parents, ancestry and equal certified/merge trees were re-read. Its post-integration CI is executing. Overall validation remains WAITING_EXTERNAL_CI.

Append-only recovery events 0001–0003 are persisted on the original session task branch under .project-leader/recovery-events/PINK-IPTV-ANDROID-SESSION-LIFECYCLE-006/. Canonical journal validation checks the sequence, hash chain and bounded counters. Events were committed after the source PR was closed, so recovery metadata does not replace the certified parent integration SHA or restart its device proof.

## Generic control-plane finding

The current control/trusted_gate.py accepts safely narrowed scope patterns through _scope_pattern_is_within. However, control/managed_project_contract.py still uses set(task.mutation_scope) - set(allowed_scope_patterns), so an exact file contained within android/** is rejected for a managed project. A read-only local probe reproduced this inconsistency with the current canonical code and the unchanged central policy. Both current PINK tasks use exact policy ceiling patterns, so this does not invalidate or block their acceptance.

A generic correction should share the scope-containment rule between local and managed verifiers and cover contained files/subpatterns, prefix collisions, parent traversal and widening. This finding applies to every managed project and does not call for a PINK-specific policy exception. The canonical control repository remains unchanged by this continuation.

## Retained boundaries

Main integration of parent PRs 13, 14 and 15 remains an Owner Human Gate. PR4 is older conflicting infrastructure documentation and is not integrated. No release, deploy, OVH/server/peer/tunnel effect or production secret change is authorized. Windows and subsequent WireGuard runtime stages retain their separate scope prerequisites. The application is not certified as a complete production product.
