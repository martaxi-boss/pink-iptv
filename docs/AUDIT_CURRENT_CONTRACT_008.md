# Current-contract certification 008

Canonical control: martaxi-boss/Project-leader at 638ed1047e7124257027ccc38ad7d12235164ba9; plugin 0.5.0; Task/Worker 2.0. Current central policy digest: 95ea2875af179ae32962dbf337a985cd513a8cd6af4d006167c4bb61cdf07c34.

## Finding and correction
Task007's immutable result selects implementation fbcad5f3e47c6388935d2f87981922a9bfd43119 and Android run37114668507 attempt2. Its RETRY_AUTHORIZED event was committed at c053579e3b01bba7481645a9af3405e37c478565 AFTER that implementation SHA. The live comparison c053579...fbcad5f is behind by two commits. Its RECOVERED event0bffa388 also precedes final candidateca728c6f. Current structural Recovery rules therefore reject that historical certificate. Timestamp ordering alone does not repair Git ancestry. Preserve all historical task/result/journal bytes. This audit supersedes the earlier task007 current-certification claim; it does not rewrite history or retroactively authorize that retry.

The exact candidateca728c6fc107cc786c3f18794f7c72150365f9b1 independently passed six push/PR CI runs at attempt1, including Android37116785896 and Backend37116785899. Those product checks remain observed successful; the old Worker Result is not their current v2 certificate.

Task008 binds an immutable authorization-only commit on the existing dedicated PR13 development branch before this correction. It refreshes the current composition certificate using fresh required CI on the correction implementation SHA and canonical external verification. No task008 retry has been performed at preparation time. If recovery is needed, persist its own append-only journal before executing a retry.

## Scope and product
Only task008 evidence, this audit and PROJECT_STATE change. Android, backend, workflows, scanner and all historical records must remain byte-identical to ca728c6f. The application remains Stage004B identity/permission/read-only adapter, with no real tunnel, peer or server operation. Phase3 implementation is merged; later real-provider and physical-TV evidence is not certified. Windows and later WireGuard stages still require their separate scope.

PR14/15 are absorbed into PR13 through development composition; PR4 remains a separate legacy infrastructure documentation workstream and is not promoted. Main remains84fe2dfffd4d381f45bd9b88a0983c122ba42ee4. No local trusted PR gate is installed; canonical external audit is required and no local-gate success is claimed.

## Acceptance
This preparation document does not assert future CI success. The task008 Worker Result and live PR13 receipt must bind the exact authorization bytes/commit, implementation SHA, actual fresh run IDs, same-SHA context consistency, authorized diff and evidence-only final-head ancestry. All current-contract checks and device job logs must be audited before reporting acceptance.

When those checks pass and no covered correction remains, the next action is the separately authorized exact-head PR13 merge to main. No merge/release/deploy/infrastructure/provider/secret/destructive/spending permission is inferred.
