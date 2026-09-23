# Implementation Plan for Builder Orders

The supervisor should issue small auditable orders. Do not ask the builder to implement the whole product in one PR.

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

## Order 004 - WireGuard POC
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
- latest main baseline
- clean worktree
- explicit changed-file scope
- tests
- security check
- PR
- supervisor review before merge.
