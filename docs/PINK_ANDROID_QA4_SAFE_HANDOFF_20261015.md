# PINK IPTV TESTE 4 — Android physical regression (2026-10-15)

The owner reports Live TV working while Movies/Series in TESTE3 show static "No playlist selected" and no catalog. An exact built-route Chromium smoke reproduced the bug: the previous mobile optimization dropped `buildGroupingIndex` from imports while the Movies and Series modules still execute `buildGroupingIndex([])` at module initialization, causing a fatal `ReferenceError` before requesting the active account or provider. The new code keeps `buildGroupingIndex` for the initial empty state, and uses memoized `getSharedGroupingIndex` for loaded catalogs.

The CI browser smoke verifies *both built pages*, using a simulated encrypted native Keystore vault with either zero account or a fully synthetic selected Xtream account and category item. The test runs before costly Android compilation; no personal credentials, provider URL or tokens are used.

QA4 has a new disposable debug package `com.pinkiptv.extreme.qa4`, launcher **PINK IPTV TESTE 4**, and must not replace `com.pinkiptv.extreme` (original), `...qa2` or `...qa3`. Prior QA3 debug signing is not stable between runs; Android cannot safely install a replacement over QA3 signed with a different key. **Do not uninstall the original or any working app**. QA4 login may consume a separate WireGuard device lease and Android supports only one active VPN. Owner must confirm a free installation slot and consent before device activation. Do not deploy server/backend, change signing secrets, recycle keys or publish commercially.

No claim of full provider compatibility/speed until real phone tests; exact-head CI, Kotlin/Web Worker, lint, VPN/emulator signing and artifact provenance gates must all pass first.
