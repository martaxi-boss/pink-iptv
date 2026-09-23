# Roadmap

## Phase 0 - Architecture
Current phase.
Deliver docs, repository structure and supervisor handoff.
No production changes.

## Phase 1 - Backend Foundation
- FastAPI service skeleton
- PostgreSQL schema
- Mega adapter
- subscription mapping/import path
- /session/resolve
- /app-config
- tests
- staging deployment

Gate: one real username/password resolves to the correct assigned dns_link and validates against Xtream.

## Phase 2 - Android Core
- Kotlin/Compose project
- login
- secure credential storage
- Xtream catalog adapter
- Home
- Live TV
- Movies
- Series
- Media3 player
- EPG
- favorites/history

Gate: stable playback on phone + Android TV device.

## Phase 3 - VPN
- WireGuard POC on existing Droplet
- Android embedded tunnel
- per-installation peer enrollment
- reconnect/kill policy
- bandwidth measurements
- dedicated-gateway decision

Gate: IPTV works through VPN with measured throughput and no impact to existing workloads.

## Phase 4 - Windows
- WinUI 3 application
- same backend contract and information architecture
- media player
- secure credentials
- WireGuard integration

## Phase 5 - Hardening / Distribution
- CI/CD
- signed release builds
- crash/error reporting
- accessibility/TV focus pass
- load tests
- backup/restore tests
- legal/store policy review
- controlled beta

Public launch remains NO until explicit owner/supervisor approval.
