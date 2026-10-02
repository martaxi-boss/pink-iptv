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

## Android Stage 004A / 004B boundary

Stage 004A is COMPLETE / MERGED and consumes the official released `com.wireguard.android:tunnel:1.0.20260102` dependency with non-root `GoBackend` only. PINK does not vendor WireGuard source or ship the unused `libwg.so` / `libwg-quick.so` native binaries.

Stage 004B is limited to local identity, Android permission and adapter preparation. Its architecture is:

PINK Settings/state -> PINK preparation controller -> Android `VpnService.prepare()` gateway -> official `GoBackend` adapter.

The device-install WireGuard identity is not tied to an Xtream account. The first identity is generated only after Android VPN preparation has been authorized. Later sessions reuse it, including across IPTV logout/login.

The private WireGuard key is encrypted before persistence with a dedicated Android Keystore AES-256-GCM wrapping key named `pink_iptv_wireguard_identity_v1`. The dedicated `pink_wireguard_identity` DataStore contains only format version, IV and ciphertext. The existing IPTV credential alias/storage is separate. Corrupt encrypted identity fails closed and is not silently regenerated.

The ordinary UI exposes no private key, PSK, endpoint or WireGuard configuration. 004B uses preparation states such as not prepared, awaiting system permission, ready for a later tunnel stage, denied and error. It does not claim VPN ON or CONNECTED.

The official `GoBackend$VpnService` remains the only VPN service. Its merged PINK manifest explicitly sets `android.net.VpnService.SUPPORTS_ALWAYS_ON=false` until a future stage certifies real tunnel lifecycle/service-survival behavior.

004B never calls `Backend.setState(... UP ...)`, creates no real `Config`, creates no peer and contacts no VPN server.

## Routing

Android should prefer app-scoped VPN where technically supported and stable. Windows policy remains a later phase. Split-tunnel behavior must be tested against provider host/IP changes before production use.

## Capacity

Streaming bandwidth is the primary scaling concern because provider video traverses the future VPN gateway. CPU, memory, network throughput, concurrency and isolation must be measured under representative load.

Production VPN capacity must be measured. No current development/staging host specification is itself evidence for a specific number of concurrent users or streams.
