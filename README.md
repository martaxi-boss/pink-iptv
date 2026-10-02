# PINK IPTV

Private project for an original IPTV player for Android/Android TV and Windows.

Status: Phase 3 Android Xtream + Player implementation COMPLETE / MERGED. Phase 4 WireGuard has not started. Windows has not started.
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

Current Android flow: Client -> PINK Backend -> `POST /v1/session/resolve` for authenticated provider resolution, followed by direct authenticated Xtream catalog/playback networking from the Android client. Video does not proxy through the PINK Backend.

Phase 3 Android implementation is COMPLETE / MERGED. This includes Live/VOD/Series catalogs and playback, Series detail/episodes, EPG/Catch Up, Favorites/History/Continue Watching/resume, Global Search, phone UX and Android TV/D-pad implementation.

Foundation / Mega/Xtream authentication proof is CERTIFIED. Later Phase 3 real-provider playback/catalog proofs and post-Shell physical-TV feature proofs remain separate residual validation gates where not explicitly certified. Deterministic TV test coverage is not represented as physical-device certification.

Phase 4 — WireGuard is the next separately supervised phase and has not started. Phase 5 — Windows and Phase 6 — Hardening / Distribution remain future work. Public launch remains NO.

See:
- docs/PRODUCT_SPEC.md
- docs/ARCHITECTURE.md
- docs/MEGA_OTT_INTEGRATION.md
- docs/VPN_ARCHITECTURE.md
- docs/INFRASTRUCTURE.md
- docs/SECURITY.md
- docs/ROADMAP.md
- SUPERVISOR_HANDOFF.md

Implementation proceeds only under explicit Owner/Supervisor builder orders. Phase 3 implementation completion does not constitute production readiness, release approval or public-launch approval.
