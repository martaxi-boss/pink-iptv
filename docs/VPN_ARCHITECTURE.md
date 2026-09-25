# VPN Architecture

## Goal

PINK IPTV should offer an integrated VPN experience without requiring a separate consumer VPN application. WireGuard remains the preferred tunnel protocol.

## Concept

Client -> encrypted WireGuard tunnel -> PINK VPN Gateway -> Internet/provider.

The VPN service is created by WireGuard between the client and infrastructure controlled for PINK IPTV. There is no external consumer VPN subscription requirement in the product architecture.

## POC policy

The current approved development/staging environment is the OVH host documented in `PROJECT_STATE.md` and `docs/INFRASTRUCTURE.md`.

A future Order 004 may authorize a bounded WireGuard POC in an approved environment. Android Shell 002 does not authorize WireGuard installation, VPN networking changes, firewall/routing mutation, IP forwarding or NAT.

Before any future WireGuard mutation:

- establish snapshot/rollback;
- audit existing backend/Nginx/PostgreSQL/system services;
- choose a non-conflicting UDP port;
- preserve existing services and backend reachability.

## Production

Use a dedicated VPN gateway once real production demand, isolation requirements and measured capacity justify it.

Each installation gets:

- unique WireGuard keypair;
- private key generated/stored on device;
- public key registered with backend;
- unique VPN address/peer;
- revocable enrollment.

Never ship one shared private key in the APK/EXE.

## Future app behavior

A separately authorized VPN phase may add OS VPN permission/setup, device key generation, enrollment, tunnel health and reconnect policy. None of that is implemented by Android Shell 002.

## Routing

Android should prefer app-scoped VPN where technically supported and stable. Windows policy remains a later phase. Split-tunnel behavior must be tested against provider host/IP changes before production use.

## Capacity

Streaming bandwidth is the primary scaling concern because provider video traverses the future VPN gateway. CPU, memory, network throughput, concurrency and isolation must be measured under representative load.

Production VPN capacity must be measured. No current development/staging host specification is itself evidence for a specific number of concurrent users or streams.
