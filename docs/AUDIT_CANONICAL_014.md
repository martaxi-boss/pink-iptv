# PINK IPTV canonical audit and continuation 014

Audit date: 2026-10-03. Target: martaxi-boss/pink-iptv only.
Control: martaxi-boss/Project-leader main 66db27d8118cfa527a21466b8a5034acd5b4395e.
Starting main: 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4.
Starting development / PR13 head: 196e50895d8239724352ef2d7e1fb8ac6fec754e.

## Method and evidence boundary

Reconstructed live default/development Git refs, open/closed PRs, task/results, recovery history, exact workflow IDs, same-SHA push/PR contexts, branch governance and repository source. Read canonical operating/runbook/recovery/role contracts and standing authority. Scope includes Android, backend, workflows, source security boundaries, test strategy, docs, infrastructure prerequisites and Windows state. This is a repository and CI audit; no fresh provider, physical device, infrastructure, penetration, capacity or production audit is inferred.

The installed plugin0.2.0 skill has stale registry and automatic action-name Human Gate wording. The live canonical contract no longer requires a registry and uses objective-bound standing authority. No Project Leader control-plane file is modified here. New task014 uses current Task/Worker v2, generic central policy, immutable authorization and append-only recovery; historical records are preserved under their exact bound historical policies.

## Reconstructed implementation

| Area | Verifiable state | Remaining evidence/work |
| --- | --- | --- |
| Foundation/Mega | Merged backend mapping, known-ID import, exact dns_link, prior sanitized authentication proof | Fresh provider/runtime evidence when needed |
| Android login | Username/password only; backend resolver; Keystore AES/GCM credential storage | Production backend configuration and distribution |
| Catalog/player | Live/VOD/Series, Media3 playback, EPG/Catch Up, favorites/history/resume, global search merged | Later real-provider playback/catalog/EPG proof and physical TV proof |
| WireGuard004A | Official dependency merged; minSdk24, native payload checks | No production capacity conclusion |
| WireGuard004B | Device identity, explicit permission, read-only GoBackend, no always-on or real tunnel | Main promotion, server/rollback preflight, peer, actual tunnel/lifecycle/routing/capacity |
| Backend remediation | Generic no-store422, no validation credential echo | Public abuse/rate controls before production |
| Android remediation | Generation guards for stale session/catalog/series/preparation results; serialized credential cleanup | Broader real-device concurrency and lifecycle validation |
| Windows | Planning README; no implementation | Later bounded Windows order |
| Distribution | No signed release/public launch | Signing, legal/store/accessibility/performance/security and controlled beta |

## Source and security review

- Login retains the exact provider origin returned by the backend; there is no customer DNS/M3U field or invented Mega username-search endpoint.
- Backend mapping schema has no customer password/token column. Mega token stays backend-side. The resolver authenticates the supplied credentials against the mapped origin.
- Backend outbound origin validation rejects credential-bearing URLs, local/non-public addresses and invalid origins. Its transport binds validated public IPs while preserving HTTPS hostname verification. Redirects and sensitive HTTP request logging are disabled.
- The422 handler returns a constant generic body and no-store header without serializing validation inputs or field names. Regression tests cover malformed JSON and sensitive extra keys.
- IPTV credentials and WireGuard identity use separate Keystore/DataStore paths. Room stores typed credential-free media refs under account partitions; current search index stays in memory.
- WireGuard corrupt identity fails closed; ensure operations are serialized. The preparation controller rejects obsolete asynchronous outcomes. The backend adapter has no UP/setState/config/peer operation. UI does not claim a connected VPN.
- Android session generation checks and credential-cleanup mutex prevent earlier authentication or logout work from publishing into a later session. Catalog and series controllers invalidate asynchronous results after clear or selection change.
- Review found no additional demonstrated product defect requiring a code patch in this bounded pass. That is not a proof of absence of all defects.

## CI and validation

Task012 implementation2885d685 passed all four live contexts: Android pull_request37143358663, Android push37143356834, Backend pull_request37143358661, Backend push37143356899. Required Android PR jobs android, phone-device-proof and wireguard-api24-smoke all completed success. Push emulator jobs are intentionally not applicable; PR success supplies the runtime evidence. Backend CI includes PostgreSQL migration/tests and credential scanning.

Git diff from implementation2885d685 to development196e508 contains only the task012 result file. Incidental Android run37151847142 on that evidence-only head was active at observation and is non-certifying; it does not invalidate the certified material head or justify a duplicate dispatch. Main is unprotected and live rulesets list is empty; no final-head branch-rule enforcement is claimed. Task014 material documentation correction requires fresh Android/Backend CI and same-SHA consistency before acceptance.

A fresh local backend regression run completed69 non-PostgreSQL tests. PostgreSQL is certified by the live backend CI, not by that local run. The local run used existing pinned dependencies, without reading runtime credentials. Full PR diff whitespace check passed.

The repository has no base-installed trusted Project Leader gate. Supervisor uses external canonical v2 policy/evidence checks; do not claim a local trusted-gate PASS. Existing CI performs Android lint/JVM/APK/instrumentation assembly, device smoke, backend lint/migrations/tests and secret scanning. Workflow source changes in the pending composition remain historical effects; the current task does not modify workflows or retroactively authorize them.

## Rejected audit candidate013

PR24 was closed unmerged because its recovery hash links used file-byte hashing instead of canonical event JSON hashing. Its authorization/journal/CI remain immutable non-certifying evidence on the rejected branch. Task014 starts from unchanged development196e508, carries no Task013 files, and validates its authorization before connector publication. The earlier shell credential failure is resolved through the available connector; no shell push is retried.

## Findings and disposition

| Finding | Disposition |
| --- | --- |
| PROJECT_STATE still said task008 certification was underway after closure | Corrected by task014; durable current results and live refs determine status |
| Shell TV emulator evidence described as physical-TV proof | Corrected: Android TV emulator success does not certify a physical TV |
| Historical task007 retry ancestry invalid; task006 retrospective authorization invalid | Preserved and explicitly non-certifying; fresh task008/012 certificates supply later state |
| Installed skill differs from live canonical runtime | Current task follows live canonical contract; no cross-project plugin edit |
| No trusted local gate or protected main/rulesets | Enforcement limitation recorded honestly; no governance expansion |
| Provider/device evidence absent for later Phase3 surfaces | Remains residual validation, not a fabricated PASS |
| Production cleartext/provider policy, rate limiting, signing and capacity unclosed | Retained future production requirements; no launch readiness claim |
| Windows and real VPN incomplete | Next implementation phases, not hygiene residue |

## Automatic continuation and convergence

Task014 authorization exists before the state/report changes. Required checks are architecture/scope, exact GitHub evidence, current-state consistency, product/history preservation and promotion convergence. Fresh required CI must bind the material task014 implementation. Persist the Worker Result only after independent verification, integrate only this correction into PR13 development, re-read exact main/head, and resolve promotion under standing authority. Exact merge authorization must exist durably before the main merge, and an independently verified result afterwards. Historical NO MERGE packets are not silently rewritten.

No main merge is predicted by this preparation report. The durable transition result and live GitHub refs prove whether it occurred. No production release/deploy, destructive cleanup, credentials, VPN/server/OVH or paid effect is authorized by ordinary Builder implementation.

After promotion, continue with Phase4 read-only service/rollback/access preflight. Existing VPS access workflows must be inspected before use; do not invent SSH access, expose secrets, mutate another repository, or activate a tunnel without prerequisites. If essential execution evidence/access is unavailable, record the exact missing capability and stop there. Physical-TV testing necessarily requires the corresponding device environment.
