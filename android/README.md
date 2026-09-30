# Android Client

PINK IPTV uses one Android `:app` module for phone/tablet, Android TV and TV Box.

## Stack

- Kotlin 2.4.10
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose / Material 3
- Navigation Compose
- Android ViewModel / StateFlow
- DataStore Preferences
- Android Keystore AES/GCM
- OkHttp 5.4.0
- Kotlin serialization / coroutines

SDK contract: compileSdk 36, targetSdk 36, minSdk 23, JDK 17.

## Completed baseline

Foundation / Mega Proof 001 is complete and merged.

Android Shell 002 is complete and merged at merge commit `f83542a31ff7ac0fbd08ceab3531d3d92ee03a10`. Phone device proof and Android TV device proof both passed.

The Login surface remains exactly USERNAME, PASSWORD and ENTRAR. The PINK Backend URL is build configuration and is never a customer field.

## Phase 3 / Stage 003A

Stage 003A adds the authenticated provider runtime and catalog foundation:

- PINK Backend `POST /v1/session/resolve` remains the only login/discovery contract;
- a successful backend response creates an in-memory provider session containing the customer credentials plus the exact authoritative `xtream_base_url`;
- the runtime session is rebuilt only after successful startup reauthentication and is cleared on logout/invalid/expired/disabled authentication;
- direct provider requests use `player_api.php` only after backend success;
- exact backend-resolved HTTP or HTTPS Xtream origins are supported without scheme/host rewriting;
- redirects are disabled and provider requests use `User-Agent: PINK-IPTV/0.1`;
- Live TV, Movies/VOD and Series expose loading, content, empty and recoverable-error catalog states with category/item browsing.

Android platform cleartext policy permits provider HTTP because the backend-authoritative Xtream origin may legitimately use HTTP. This does not weaken `BackendSessionClient`: the PINK Backend remains HTTPS-only in production code.

The password remains absent from navigation arguments, Compose saved state and public `AppUiState`. Existing Android Keystore/DataStore encrypted credential persistence is unchanged.

## TV / input

Catalog surfaces are touch-scrollable and D-pad focusable. Focused catalog categories, items and actions use the PINK focus treatment. Home/Login behavior from Order 002 remains intact.

## Explicitly not implemented in 003A

No Media3/ExoPlayer playback, stream playback URL generation, Catch Up, full EPG browsing, favorites persistence, history/continue-watching, VPN/WireGuard or Windows implementation is included.

Public launch remains NO.
