# Implementation Plan for Builder Orders

Project Leader reconstructs live GitHub state, bounds one task and routes it through Supervisor/Builder/audit under the canonical contract. The order descriptions below preserve stage boundaries; completed stages are not instructions to rebuild them.

Current merged stages: Foundation 001, Android Shell 002, Android Catalog/Player 003A–003F and WireGuard dependency/compliance 004A. Open PR #13 holds the combined 004B and Android/backend remediation work. Windows has not started. Public launch remains NO.

## Order 001 - Foundation / Mega Proof
Allowed:
- backend skeleton
- database schema
- Mega API adapter
- subscription mapping
- minimal resolve endpoint
- tests
- no VPN
- no Android UI

Acceptance:
- one authorized test line maps to exact Mega subscription id + dns_link
- supplied username/password is validated against assigned Xtream host
- no Mega token in logs/repo/client
- CI green

## Order 002 - Android Shell
Allowed:
- Android project
- PINK theme
- Login and Home shell
- API client and secure local storage
- no full player yet

## Order 003 - Android Catalog + Player
- Live/VOD/Series/EPG
- Media3
- favorites/history
- Android TV focus

## Order 004 - staged WireGuard work
- 004A dependency/compliance: COMPLETE / MERGED
- 004B identity/permission/read-only adapter: open PR #13, not merged
- later real-tunnel work retains the prerequisites below
- snapshot/rollback first
- server WireGuard
- one test peer
- Android tunnel
- measured bandwidth
- no production onboarding

## Order 005 - Windows
- project shell
- API parity
- catalog/player
- secure storage
- VPN integration

Each order requires:
- exact live starting revision, with merged main distinguished from pending development work
- clean worktree
- explicit changed-file scope
- tests
- security check
- PR
- Supervisor review and exact transition authorization before a consequential merge.
