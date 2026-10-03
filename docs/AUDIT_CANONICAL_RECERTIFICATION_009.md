# Canonical recertification 009

This report supersedes the current-certification claims in Task007's Worker Result, its convergence snapshot and PR13 receipt. Those records remain immutable historical evidence.

## Contract and bounded scope

Canonical Project Leader revision: 638ed1047e7124257027ccc38ad7d12235164ba9. Plugin 0.5.0; Task Authorization and Worker Result schemas 2.0. Apply DETECT -> AUDIT -> CORRECT -> VALIDATE -> CONTINUE and convergence preflight.

Target baseline: ca728c6fc107cc786c3f18794f7c72150365f9b1. Main remains 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4. Task009 changes only its own control records and this report. Product and workflow blobs must remain byte-identical to the target baseline.

## Audit finding

Task007 certifies implementation fbcad5f3e47c6388935d2f87981922a9bfd43119 using Android run37114668507 attempt2. Its FAILURE_OBSERVED commit879f190 and RETRY_AUTHORIZED commitc053579 are descendants of that implementation SHA. Temporal ordering before the retry is insufficient: current canonical verify_recovery_structural_causality requires those commits to be ancestors of the CI-certified implementation. Therefore Task007 is STRUCTURALLY_INVALID as a current Recovery certificate. Its final metadata also changed docs/AUDIT_CONVERGENCE_PREFLIGHT_007.md and PROJECT_STATE.md after the certified implementation, which requires a new implementation SHA under current material-drift rules.

Existing fresh baseline CI37116785896 (Android) and CI37116785899 (Backend) succeeded on ca728c6. That proves current code tests succeeded, but does not repair the Task007 Worker Result binding. No historical authorization/result/journal is rewritten or retroactively approved.

## Correction

Task009 authorization-only commit 228120d83d61be1e392a0228942d0f104e657653 precedes this report. Fresh CI must certify the new report implementation SHA, which already contains all inherited Task007 history. Task009 itself has no retry and claims no Task007 Recovery rehabilitation. Its own Worker Result will bind immutable Task009 authorization, fresh run IDs and the exact new implementation SHA. Evidence-only result commits may follow only within Task009's permitted result path.

## Reconstructed product status

Android Xtream catalogs, Media3 player, series, EPG/Catch Up, favorites/history/resume and search are implemented. Backend mapping/session foundation is implemented. Stage004A dependency/compliance is merged. Stage004B install identity, explicit Android permission and read-only adapter are in PR13 development.

Real provider playback, later physical-TV proof, real WireGuard tunnel/server/peer, Windows and distribution remain staged prerequisites. This task introduces no new product stage.

PR14/15 are absorbed into PR13 via development PR18. PR4 is legacy infrastructure documentation with a conflicting base; it remains outside this app/evidence task. No infrastructure fact is re-certified from old documentation.

## Acceptance and convergence

Require Android CI and Backend CI on Task009 implementation SHA, including same-SHA push/PR consistency. Inspect actual job steps/logs, API24/API36 proof and backend tests. Verify schema/policy/scope, immutable authorization and implementation/final-head ancestry with canonical control code. Prove product/workflow subtrees unchanged. Integrate through a PR whose base is the existing PR13 development branch only, after acceptance. Re-audit final main candidate.

The repository has no local trusted gate at main. Use external canonical Supervisor validation and do not claim a local-gate PASS. Main protection/rulesets require separate governance authority.

Task009 completion does not authorize merge to main, release, production deploy, real tunnel/peer/provider operation, OVH mutation, production secrets, destructive changes or paid services.

## Superseded Task008 attempt

Task008 copied an obsolete central-policy digest. Exact raw-byte canonical validation rejected it before acceptance or integration. Task008 and PR19 are preserved as superseded, uncertified history. Task009 binds the exact current policy digest95ea2875 and was validated against canonical policy before persistence. Task009 has no CI retry; it creates fresh descendant evidence rather than treating any prior run as its certificate.
