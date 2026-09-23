# Android Client

Order 002 implements the PINK IPTV application shell for phone/tablet, Android TV and TV Box from one `:app` module.

## Stack

- Kotlin 2.4.10
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose 1.11.4 / Material 3 1.4.0 with Compose BOM 2026.06.01
- Navigation Compose 2.9.8
- Android ViewModel / StateFlow
- DataStore Preferences
- Android Keystore AES/GCM
- OkHttp 5.5.0 for the PINK Backend session endpoint

SDK contract: compileSdk 36, targetSdk 36, minSdk 23, JDK 17. API 36 is used as the stable non-preview SDK available in the CI environment; Order 002 does not opt into Android 17 preview SDK tooling.

## Order 002 scope

Implemented screens: Splash, Login, Home, TV ao Vivo shell, Filmes shell, Séries shell, EPG shell, Favoritos shell and Definições.

The Login surface exposes only USERNAME, PASSWORD and ENTRAR. The backend URL is build configuration, not a user field.

The application calls only PINK Backend `POST /v1/session/resolve`. Direct Xtream provider networking is not part of Order 002.

## Security

The versioned fallback `PINK_API_BASE_URL` is `https://pink-api.invalid/`. Runtime builds must supply an HTTPS PINK Backend base URL using the Gradle property. Cleartext traffic is disabled by manifest/network security configuration.

Valid credentials may be retained for reauthentication with the password encrypted using a per-installation Android Keystore AES/GCM key; only ciphertext/IV metadata and username are stored in DataStore. Logout clears the local record and destroys the key alias.

No HTTP body logger is included.

## TV / input

The same APK declares normal and Leanback launcher entry points. Touchscreen is not required. Home defines deterministic D-pad focus neighbors and a strong PINK focus border; Login is usable with D-pad plus the system keyboard.

## Explicitly future

No Media3/player, real catalog, stream URL handling, VPN/WireGuard, Room, Windows, Samsung/Tizen or LG/webOS implementation is included.
