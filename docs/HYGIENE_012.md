# PINK IPTV hygiene — 2026-10-03

Canonical control revision: ca2916d3c4ff46fef7783a4c081c49697a25dfbf.
Task: PINK-IPTV-PROJECT-HYGIENE-012. Task010/011 candidate work is superseded; PR22 is closed unmerged after protected-workflow policy audit.
Main remains 84fe2dfffd4d381f45bd9b88a0983c122ba42ee4. Hygiene starts from the existing PR13 development revision.
Pending PR13: c6142d76e84731bb675ff9e75be982e0e6cfb540, open/unmerged.

## Removed active contradictions

- Architecture/UX/integration instructions no longer describe merged Phase3 Android catalogs, player, search and library as future work or empty shells.
- The implementation plan separates completed 001/002/003/004A stages from pending 004B and later real-tunnel/Windows work.
- Infrastructure instructions follow GitHub-hosted CI, GitHub code/reproducible backups and runtime-only VPS secrets. No self-hosted runner, Remote Desktop Commander or paid provider-backup activation is introduced.
- PR4 is closed unmerged: its useful OVH baseline is already superseded by merged infrastructure documentation. Branch/history remain available.
- Backend credential scanning already uses the exact field-aware filter in the PR13 baseline; this task changes no scanner or workflow. Only 40/64-character hex SHA fields in recognised control JSON records are exempt; credential fields and product files remain scanned. No directory-wide exclusion is used.

## Retained working dependencies

The single Alembic root revision 20260923_0001 creates subscription_mappings used by the mapping model and session resolution. Its PostgreSQL test verifies columns, required constraints and absence of password storage. Deleting this migration or regression tests would break fresh installs or remove useful coverage. No competing migration chain or obsolete database schema was found in the inspected main tree.

Phase3 tests still cover active catalog, EPG/Catch Up, playback, search, library, credentials and session behavior. Earlier stage dates do not make these tests obsolete. No known unused test is removed by this task.

Historical handoff and audit records are evidence, not new execution orders. SUPERVISOR_HANDOFF.md already labels its DigitalOcean baseline historical. Immutable authorizations/results/recovery journals are retained; pending development work is not resumed from legacy checkpoint status alone.

## Verification boundary

Changed files are documentation and task-local control evidence. Android/backend product source, SQL migrations, tests, dependency versions, runtime configuration and all CI workflows remain unchanged. Fresh Android and Backend CI must validate lint/format, clean PostgreSQL migration, backend tests and credential scanning on the implementation revision. No new local trusted Project Leader gate is claimed.

No promotion of PR13, release, deployment, production launch, real VPN tunnel, peer/server/provider/OVH mutation, secret rotation, paid backup or history deletion is part of this cleanup. Outstanding real-provider and physical-TV proof remains recorded in PROJECT_STATE.md.
