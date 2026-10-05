# PINK IPTV / Extreme InfiniTV

PINK IPTV uses the complete Extreme InfiniTV client by InfiniteL8p / Ludovico
Ferrara, pinned to `1efcc1b4ab3468db04aedd1c466228c52ef001b7` (1.9.0).
Upstream: https://github.com/infinitel8p/Extreme-InfiniTV

The application source is GPL-3.0-or-later. Original copyright, LICENSE and
third-party notices remain in the full source distribution. PINK changes cover
branding, managed backend login and Android credential protection. The generated
PINK identity is supplied with these adaptations. There is no subscription or
content bundled with the application.

Every CI APK artifact includes the complete corresponding patched source,
upstream revision and overlay recipe. To rebuild: install Node22, pnpm10.31.0,
JDK17, Rust and Android SDK36/NDK29.0.13846066, then follow the repository workflow
`PINK Extreme Android 042`. Debug signing is for testing; public distribution and
production signing are separate release steps.
