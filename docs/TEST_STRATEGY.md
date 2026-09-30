# Test Strategy

## Automated CI proof

Backend CI continues to use Python 3.12 and a real PostgreSQL service. Android CI uses JDK 17, the committed Gradle wrapper, Android SDK 36 and repository secret scanning.

## Live proof — separate from CI

Live provider proof is never required to place real provider credentials in CI. Any Owner-authorized line must remain confined to an approved secure execution environment and evidence must be sanitized.

Order 001 live proof is complete and passed before merge.

## Android Shell 002 automated proof

Android CI runs `lintDebug`, all debug JVM unit tests, `assembleDebug`, compilation/assembly of Android instrumentation-test sources, and repository secret scanning without production signing or credentials.

Order 002 is complete and merged at `f83542a31ff7ac0fbd08ceab3531d3d92ee03a10`; phone proof PASS and TV proof PASS.

## Android Catalog + Player 003A automated proof

Stage 003A is complete and merged.

Approved head: `f6ad7a5f53afb7aded8db9e8841ae626f236f5d4`.

Merge commit: `135df164de9bb30e66b3b4268fbfc38c8b204e1a`.

003A JVM and instrumentation coverage remains in force for provider runtime-session lifecycle, exact Xtream-origin handling, redirect refusal, stable User-Agent, Live/VOD/Series parsing, catalog state/error handling, browsing, retry, Back and TV catalog focus.

## Android Catalog + Player 003B automated proof

Stage 003B adds deterministic coverage for:

- typed credential-free Live and VOD playback references;
- exact authoritative HTTP/HTTPS playback origin and explicit port preservation;
- structured Live path generation;
- structured Movies/VOD path generation;
- stream id and VOD container-extension validation;
- missing runtime session fail-closed behavior;
- absence of credentials/URI fields from public player UI state;
- Series selection remaining non-playback;
- safe Media3 error classification;
- player state mapping for preparing/buffering/playing/paused/ended;
- player retry using the same selected playback identity;
- Live/Movie catalog navigation into the player with no credential route argument;
- Series remaining in the catalog with a future-stage indication;
- player loading/error/control rendering with deterministic fake player state;
- Back returning to the previous catalog;
- seek controls only when the player reports seekable media;
- TV focusability and D-pad activation of player controls.

No Android CI test requires live provider media.

The existing pull-request phone-device-proof remains unchanged. `NavigationShellTest` includes deterministic Live/Movie player navigation and Back coverage so the phone emulator proves the Stage 003B route/control shell without provider credentials.

## Stage 003B live-provider proof policy

If the Owner-authorized test line is available only through an approved secure runtime, a separate sanitized smoke may verify one Live start and one VOD start. No credential-bearing media URI, provider hostname, username or password may appear in GitHub Actions, PR text, repository files or evidence.

If secure credentials are unavailable to the executor, implementation audit proceeds on deterministic CI/device proof and the separate real-provider playback gate remains for Supervisor routing.

## Future client/platform matrices retained

### Android phone/tablet

- phone and tablet form factors;
- Wi-Fi/mobile network transitions;
- background/foreground and process restart;
- secure credential persistence;
- player/network recovery where implemented.

### Android TV / TV Box

- D-pad-only navigation;
- deterministic focus movement and visible focus state;
- resume/reconnect behavior after network changes;
- playback behavior on supported TV hardware.

### Windows

- Windows 11;
- mouse/keyboard navigation;
- install/uninstall and upgrade behavior;
- secure credential storage;
- network transitions and media protocol/codec behavior.

### VPN / WireGuard

When the separately supervised VPN phase opens, test throughput, packet loss, reconnect, DNS behavior, provider access through the gateway, peer revocation, network transitions, and multiple simultaneous test peers. Capacity must be measured before production estimates.
