# PINK IPTV TESTE 3 — instalação separada e controlo do utilizador

This one-time **Android-only, opt-in test installer** uses debug package `com.pinkiptv.extreme.qa3` and launcher name **PINK IPTV TESTE 3**. Original `com.pinkiptv.extreme` and previous QA2 `com.pinkiptv.extreme.qa2` keep their own data, Keystore material, VPN identities and APK signatures. QA3 is not a production or an in-place update.

The same GitHub workflow first builds and certifies the combined source in its original package, then *after* the original integration tests re-packages already-built native libraries with only a different debug application ID/name. A fresh Android emulator verifies QA3's own Keystore/VPN consent and Media3 before producing `PINK-IPTV-TESTE3.apk` with `provenance.json`, `SHA256SUMS` and public certificate report. Original APK is not overwritten before testing.

**Important human gate:** Even though installation is parallel and does not replace original/QA2, logging into QA3 may consume a new backend device slot and temporarily take over Android's sole active VPN. Do not instruct the owner to install until device quota and consent have been confirmed. Never uninstall the original Pink IPTV. Debug signatures are not persistent and a future QA3 APK cannot safely update a previously installed QA3 under a new cert. Establish a securely managed persistent signing identity in a separate owner-authorized stage instead of repeatedly creating new QA package IDs.

When Android installation asks to **Update** or uninstall either original or QA2, cancel; the verified QA3 manifest should show **Install**. The tested code remains subject to physical provider compatibility and real phone frame-rate checks.
