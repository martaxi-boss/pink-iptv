# Pink IPTV integrated Android QA — 2026-10-15

This branch combines source-separated PRs **#68, #71, #72 and #73** for a single exact-SHA integration CI.

- Native Media3 playback actions for Live, Movies and Series already separately pass Android CI.
- PINK Cinema premium UI (incl. compact catalog artwork) passes individual Android CI.
- Home now warms only the fast Live catalog, not whole Movies and Series inventories. The other catalogs remain loaded on demand with existing native VPN-bound streaming, IndexedDB caching and Worker.
- The classic small native Android phone automatically defaults the upstream Performance mode only when previously unset (never overriding the user's Off selection), renders 24 posters in one batch instead of 200, automatically adds up to 240 before existing Load More, and disables unnecessary large transitions. Desktop/Android TV retain original grid batch sizes.
- The shared upstream grouping memo is reused for unchanged data arrays.
- The cinematic dark/pink CSS disables additional hover/filter compositor effects whenever Performance mode is enabled.

All existing credentials and traffic stay under the original Android Keystore and admitted WireGuard network. No new Floot web project, Android package identity, provider endpoint or backend service is introduced. No new account or server-side subscription.

**Limits:** A cold request for `All categories` still transfers and maps the entire provider VOD/Series inventory, which can take minutes. A provider-verified, safe category-scoped fast path and real-device performance measurements are needed for that case. A complete source build does not certify actual phone frame rate.

**Distribution gate:** Original Android debug APK signing certificates vary between CI runs (issue #54). This integration PR is not a replacement APK; **never uninstall or replace the existing working Android/VPN app**. Provide only a verified separate QA build before owner acceptance, then request physical test. Production release remains disabled.

## Category-first extension (from PR #75)
- Android phone makes a small VPN-bound Xtream request for the selected category before retrieving a full provider VOD/Series catalog. Numeric category IDs validated both in JS and Kotlin; provider URLs and credentials remain native.
- Category-map and per-category rows use account-scoped cache keys. An explicit All categories selection is persisted; global search still loads the full list. The same full-list semantics remain for Android TV and desktop.
- The first category is chosen visibly from available, non-adult categories only when no existing selection was saved. Virtual favorites, recents and genres require a full view; missing/ambiguous category mappings fall back safely.
- First uncached data is still provider-dependent: servers may ignore the category_id filter; no physical measured speedup yet. Only acceptance with the owner's active provider can prove it.

**CI gate:** the Android workflow must pass after this exact integration SHA; the previously successful PR #74 SHA is superseded. No distribution or merge without complete integration CI and signing/identity review.
