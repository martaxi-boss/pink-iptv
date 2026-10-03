> HISTORICAL SNAPSHOT — stopping decision and evidence certification superseded by [convergence preflight 007](AUDIT_CONVERGENCE_PREFLIGHT_007.md). Preserve this report as history; it does not certify the combined current HEAD.

# PINK IPTV — canonical audit and authorized continuation, 3 October 2026

This audit reconstructs live GitHub source, PR diffs, task records, Actions runs, jobs and selected instrumentation logs. It is not a live OVH inspection, real-provider playback certification, physical-TV certification, penetration test or release certification.

## Canonical control and authorization

The active Project Leader control plane is version 0.5.0, with Task Authorization v2 and Worker Result v2. New managed work uses independently pinned target and control revisions, central policy bytes, an immutable authorization-only commit before implementation, and append-only recovery when recovery is needed. External verification uses trusted canonical Python validation/evidence functions with GitHub payloads obtained through the installed read-only connector; no target-head verifier code is trusted.

Earlier task/result/checkpoint records in PRs 13–15 are retained as historical v1 evidence. The earlier audit's v1 bootstrap wording describes its older control reference and does not permit new v1 tasks under the current control plane. No retroactive authorization or v2 certification of those historical records is claimed.

Main contains the merged backend foundation, Android shell, complete Phase 3 implementation and WireGuard 004A dependency/compliance foundation. Main has not been changed during this continuation.

## Repository state and findings

| Surface | GitHub evidence | Outcome |
| --- | --- | --- |
| Backend auth/mapping | Known-ID Mega import; exact mapped host; password absent from database model; short signed token; public-IP-pinned Xtream transport; redirects disabled | Existing foundation retained |
| Android product | Live/VOD/Series, shared Media3 player, Series detail, EPG/Catch Up, local library and global search | Implementation present; real-provider and later physical-TV proof remain separate |
| WireGuard 004B, PR 13 | Separate installation identity encrypted with its own Keystore alias/DataStore; permission preparation; read-only GoBackend adapter; always-on opt-out | Android/Backend CI at historical final head succeed; API24 native/identity/manifest and API36 touch/simulated-TV Settings evidence inspected |
| Backend privacy, PR 14 | Generic HTTP422/no-store and seven invalid-input regressions; final-head Backend CI succeeds | Privacy remediation exists on its branch; not on main |
| Catalog/detail lifecycle, PR 15 | Generation guards and four delayed-response regressions; final-head Android and Backend CI succeed | Catalog remediation exists on its branch; not on main |
| New VPN preparation race, PR 16 | Delayed identity read could replace pending permission, denial or a newer readiness result | New v2 task adds generation-bound queued/suspended publication and four regressions |
| New session lifecycle race, PR 17 | Delayed login/bootstrap could restore logout, duplicate queued login could start twice, credential save/clear could interleave | New v2 task adds generations, synchronous in-flight state, serialized credential operations/cleanup and six regressions |
| Scanner metadata boundary | Existing helper recognizes only specific canonical SHA fields on control JSON, hex type, lowercase characters and exact 40/64 length | Extend only revision and authorization commit/digest fields for v2; nine positive/negative local probes pass |
| Older infrastructure PR 4 | Documentation branch predates current OVH narrative | Remains historical and must not overwrite newer documentation |
| Windows | README/planned stack only; explicit staged order prerequisite | Not implemented or authorized by this continuation |
| GitHub enforcement | Main unprotected; no project-local trusted v2 gate at audited main | Manual external v2 audit is required; no trusted-gate PASS is claimed |

## Two new task boundaries

PR 16 targets the existing WireGuard 004B development branch. PR 17 targets the existing catalog-remediation development branch. Their pinned bases are independently identified in their Task Authorization records. Both tasks are scoped within the central E1 policy, leave workflow files untouched, and use the existing scanner helper rather than weakening the scan.

Each authorization-only commit was independently inspected on GitHub and changes only its own task record. Later task bytes equal the authorization bytes. The scope audit compares the actual nested PR diff against the centrally bound policy. Implementation-head ancestry and CI run identity/conclusion are verified using the canonical evidence functions before a terminal result is recorded.

Historical parent branches have separate review/integration obligations. Nested PR CI proves the relevant branch combination, not all PRs combined with main. No integration, main merge or production action is implied by this report.

## Preserved contracts

- Username/password login; backend-authoritative subscription host; no user-facing portal/DNS or M3U import.
- Mega token stays backend-only; direct provider playback rather than backend video proxy.
- WireGuard private identity remains installation-scoped and separate from IPTV credential persistence.
- No real Config, UP call, peer, server enrollment or real tunnel is introduced.
- Original PINK branding and existing player/library/database/dependency architecture are retained.
- No OVH, VPN server, routing/NAT/firewall, production secrets, public-launch, deploy, release or paid-service action occurs.

## Remaining staged gates

1. Audited branch integration and exact-revision approval before any main merge.
2. Secure real-provider validation for post-auth Phase 3 catalog/playback/EPG/Catch Up surfaces and separate later physical-TV proof.
3. Separately authorized WireGuard runtime stage, including rollback, current-service/network audit, test peer, tunnel lifecycle, routing/DNS/IPv6/network-change safety and measured throughput.
4. A bounded Windows order after its prerequisites.
5. Production hardening/distribution: actual backend HTTPS configuration, abuse/resource limits, operational health/restore evidence, catalog performance, signing/artifacts and explicit launch approval.

The default Android API URL remains a placeholder. CI debug APK assembly is not a configured production distribution. VPS resource specifications do not certify tunnel or streaming capacity.

## Validation closure

The machine-readable v2 results on PRs 16 and 17 are the final evidence index. A result is recorded only after both required CI workflows and named validations pass. Active CI is WAITING_EXTERNAL_CI; unchanged active work is not redispatched.

The project is not complete as a production Android/Windows/VPN product. Completion of these local remediation tasks stops at the existing integration and staged-effect Human Gates.
