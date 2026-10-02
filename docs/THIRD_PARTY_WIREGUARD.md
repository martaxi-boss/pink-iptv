# Phase 4 WireGuard third-party attribution

Scope: PINK IPTV Phase 4 WireGuard dependency/compliance foundation only. This is not a complete inventory of every third-party dependency used by PINK IPTV.

## WireGuard Android tunnel library

- Component: WireGuard Android tunnel library
- Maven coordinate: `com.wireguard.android:tunnel:1.0.20260102`
- Consumption model: released Maven AAR; upstream source is not vendored into PINK IPTV
- Authoritative upstream: `https://git.zx2c4.com/wireguard-android`
- Release tag: `1.0.20260102`
- Release commit: `09b75c2bd37f749e2a8c85876394854113c74be7`
- Primary upstream license: Apache License 2.0
- AAR SHA-256: `PENDING_EXACT_CI_ARTIFACT_RECONCILIATION`
- AAR byte size: `PENDING_EXACT_CI_ARTIFACT_RECONCILIATION`

PINK uses the non-root userspace `GoBackend` only. The final APK must exclude unused `libwg.so` and `libwg-quick.so` while retaining `libwg-go.so`.

## wireguard-go provenance for libwg-go

The official WireGuard Android release `1.0.20260102` records `golang.zx2c4.com/wireguard v0.0.0-20250521234502-f333402bd9cb` in `tunnel/tools/libwg-go/go.mod`.

Resolved wireguard-go revision: `f333402bd9cbe0f3eeb02507bd14e23d7d639280`.

Authoritative wireguard-go license: MIT.

When PINK distributes `libwg-go.so`, the applicable WireGuard Android Apache-2.0 attribution and wireguard-go MIT copyright/permission notice must be retained in distribution compliance materials.

## Upstream source-build components versus PINK payload

Upstream source builds also reference WireGuard tools and build tooling. Presence in upstream source does not by itself mean PINK distributes those components.

Stage 004A compliance is based on the exact released AAR and final PINK APK/AAB payload. CI fails if the PINK APK contains `libwg.so` or `libwg-quick.so`.

No WireGuard private key, PSK, server credential, provider credential, or real VPN configuration is recorded here.
