# PINK IPTV

Private project for an original IPTV player for Android/Android TV and Windows.

Status: PRE-BUILD / architecture approved for supervisor review only.
Public launch: NO.
Repository must never contain production credentials, Mega OTT tokens, Xtream passwords, WireGuard private keys, or server private keys.

## Product direction

PINK IPTV follows the familiar functional structure of mainstream IPTV players: login, home, Live TV, Movies, Series, Catch Up/EPG, Favorites, Search, Player and Settings.

The implementation, brand, code, icons, assets and visual design must be original PINK IPTV work. Do not copy proprietary source code or third-party assets.

The customer-facing app is Xtream-only:
- login shows only Username + Password;
- no DNS/portal field;
- no user-facing M3U import;
- no MAC/MAG or Enigma login in v1.

Important terminology: Mega OTT calls username/password subscriptions type M3U in its reseller API. That is an internal panel type only; the PINK client still exposes only Xtream-style username/password access.

## Target platforms

- Android phone/tablet
- Android TV / TV Box / Fire TV class devices
- Windows desktop
- Future platforms are out of v1 scope

## High-level architecture

Client -> PINK Backend -> Mega OTT API for subscription metadata/dns_link.
Client -> WireGuard Gateway -> assigned Xtream host for playback/catalog.
Video must not proxy through the PINK Backend.

See:
- docs/PRODUCT_SPEC.md
- docs/ARCHITECTURE.md
- docs/MEGA_OTT_INTEGRATION.md
- docs/VPN_ARCHITECTURE.md
- docs/INFRASTRUCTURE.md
- docs/SECURITY.md
- docs/ROADMAP.md
- SUPERVISOR_HANDOFF.md

No implementation begins until the project supervisor reviews this baseline and issues a builder order.
