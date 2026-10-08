# PINK IPTV — owner-device login recovery 062

## Observed symptom, 2026-10-08

A physical-owner Android screenshot of the debug candidate `PINK-IPTV-VOD-882df9a4.apk`
shows the branded login screen and a generic `Serviço PINK temporariamente indisponível.`
message after credentials were entered. This proves the UI opened and that login failed
on that device; it does **not** identify the failing native stage, establish an
account/credential error, or disprove previously certified staging proof.

The screenshot showed a VPN indicator, but **does not establish whether it belongs to
PINK or another application**. No owner credentials, username, or screenshot
are committed to this repository.

## Confirmed source defects addressed

1. `PinkWebBridge` previously discarded every native exception/reason for `resolve`.
   The web transport therefore showed the same generic status for a refused VPN
   grant, an unreachable HTTPS control path, unavailable Android secure storage,
   and a tunnel activation fault. Task062 carries **only fixed, allowlisted**
   native status codes from the trusted main-frame bridge to the login UI.
2. Once a user declined the normal Android VPN consent, the process-wide
   `consentRequested` / completed latch prevented any repeat prompt on a
   subsequent login tap. Task062 adds an **explicit user-initiated** retry,
   rearming only the pending consent latch. Normal Activity resume never
   loops VPN dialogs. The system's `VpnService.prepare` and its official
   permission Activity remain authoritative.
3. The login UI now displays fixed Portuguese, user-actionable failure
   messages with a sanitized PINK code. Native exception text, provider URLs,
   private network details, subscription contents, account data, tokens, and
   credentials are not rendered or logged by this change.

No plaintext credential fallback, provider URL guessing, direct browser login,
production deployment, WireGuard bypass, or changes to existing protected
media/provider routing are permitted.

## Validation

- Source-bound recovery scope: `clients/extreme/audit_062.py` on the new branch.
- Regression: `pink-bridge.test.ts` and `pink-session.test.ts`.
- Full `PINK Extreme Android 042`: all existing frontend, Chromium browser,
  Android build/native media/Keystore/VPN instrumentations, and privacy scan
  remain mandatory. `Backend CI` required.
- Exact APK provenance: bind to successful final-source GitHub run.
- The previous 061 staging proof and the installed 882df9a4 candidate remain
  historical, not evidence of actual device login success after this correction.

## Owner physical acceptance still required

After a new tested APK is available, install it on the same Android phone and
attempt login. Record **only** the fixed `PINK:` failure code if it still
fails; never share a password, access token, or service URL. If the Android
VPN consent dialog appears, explicitly approve PINK; if a different VPN is
currently active, Android may require switching. A real successful login
and Live/Movies/Series proof must be observed separately before device
acceptance. No public release is authorized.
