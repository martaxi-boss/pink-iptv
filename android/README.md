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
- AndroidX Media3 1.11.1

SDK contract: compileSdk 36, targetSdk 36, minSdk 23, JDK 17.

## Completed baseline

Foundation / Mega Proof 001 and Android Shell 002 are complete and merged.

Stage 003A is complete and merged. Approved head: `f6ad7a5f53afb7aded8db9e8841ae626f236f5d4`. Merge commit: `135df164de9bb30e66b3b4268fbfc38c8b204e1a`.

The Login surface remains exactly USERNAME, PASSWORD and ENTRAR. The PINK Backend URL is build configuration and is never a customer field.

## Stage 003A catalog foundation

The authenticated provider runtime and catalog foundation preserve:

- backend-only login resolution through `POST /v1/session/resolve`;
- in-memory provider session rebuilt only after successful authentication;
- exact backend-resolved HTTP or HTTPS Xtream origin;
- direct provider catalog access through `player_api.php`;
- redirects disabled and stable `PINK-IPTV/0.1` identity;
- Live TV, Movies/VOD and Series catalog loading/content/empty/error states.

## Stage 003B player

Stage 003B is active and introduces a foreground-only Media3 player for Live TV and Movies/VOD.

Media3 modules are pinned to 1.11.1. Playback reuses the redirect-disabled provider OkHttp client through Media3's OkHttp data source and uses `PINK-IPTV/0.1`.

Playback URLs are generated only inside the player layer from the current runtime provider session plus a credential-free typed playback reference. The generated media URI is never placed in navigation, public UI state, saved state, documentation or logs.

Live uses the canonical Xtream live path. Movies/VOD use the catalog-provided container extension after conservative validation. Series remains browse-only.

The player is foreground-only: no MediaSession service, background playback, PiP, downloads or notification controls. The active ExoPlayer instance is created for the player surface and released when that surface leaves composition.

Phone/touch and Android TV/D-pad controls include play/pause, retry and Back. Seek controls are exposed only when Media3 reports the current item as seekable.

## Explicitly not implemented in 003B

Series episodes/playback, full EPG, Catch Up, favorites persistence, history/continue-watching, VPN/WireGuard and Windows remain future stages.

Public launch remains NO.
