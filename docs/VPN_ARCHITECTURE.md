# VPN Architecture

## Goal

PINK IPTV should offer an integrated VPN experience without requiring Surfshark, AirGuard or a separate consumer VPN app.

WireGuard is the preferred tunnel protocol.

## Concept

Client -> encrypted WireGuard tunnel -> PINK VPN Gateway -> Internet/provider.

The VPN service is created by WireGuard between the client and a server we control.
There is no external VPN subscription required for the software itself.

## POC

The existing DigitalOcean Droplet may host one temporary WireGuard interface for proof-of-concept only.

Before any change:
- take a snapshot or establish a rollback/backup method;
- audit existing firewall/Nginx/PostgreSQL/app workloads;
- choose a non-conflicting UDP port;
- preserve existing services.

Current server location is London, so its public egress will normally geolocate as UK/London-region data-center traffic, subject to IP geolocation databases.

## Production

Use a dedicated VPN gateway once real users are onboarded.

Each installation gets:
- unique WireGuard keypair
- private key generated/stored on device
- public key registered with backend
- unique VPN address/peer
- revocable enrollment

Never ship one shared private key in the APK/EXE.

## App behavior

First run:
1. bootstrap PINK backend configuration
2. request OS VPN permission/setup
3. generate device WireGuard key
4. enroll public key
5. connect tunnel
6. verify tunnel health
7. resolve/login to Xtream

Normal run:
- auto-connect VPN when PINK IPTV needs network access
- reconnect on network changes
- if VPN policy is required and tunnel drops, pause/stop playback until tunnel is restored
- close or idle tunnel according to policy when app is not in use

## Routing

Android should prefer app-scoped VPN where technically supported and stable.
Windows implementation may initially use the WireGuard tunnel while the app is active; split-tunnel policy must be tested against provider host/IP changes.

## Capacity

WireGuard binaries are small; disk is not the scaling concern.
Streaming bandwidth is the scaling concern because provider video traverses the VPN gateway.
Do not promise unlimited users on the current 1 vCPU / 1 GB shared Droplet.
