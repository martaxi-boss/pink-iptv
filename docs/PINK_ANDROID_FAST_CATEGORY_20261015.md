# PINK Android quick category load (candidate, 2026-10-15)

Owner reports that Live TV loads fast while first Movie/Series loads can take several minutes; screenshot shows 120,886 Movies globally, but 657 titles in selected category.

This patch implements an Android **small-phone-only**, on-demand cold-category path where the provider's Xtream `category_id` filter is supported. It queries category metadata first, then retrieves only the selected category through the same **VPN-owned, native, selected-account-bound transport**. The category ID is strictly 1–12 ASCII decimal digits at both JS/native boundary and fixed `player_api.php` builder; untrusted URL/credentials/params are not accepted. For the first cold install without saved preference, a non-adult category is selected visibly, preferring recent/top listings. No auto-choice is made for explicitly selected Favorites/Recents/genres.

The full catalogue remains available by selecting **Todas as categorias**, and global search explicitly triggers the original full-list route. Full and partial IndexedDB cache keys are distinct; a partial response never overwrites the full cache. Return to a previously selected cached category should not re-download its items. Non-Android/TV retain unchanged existing semantics. A provider that ignores `category_id` may still be slow; unsupported scoped calls fall back to full lists.

**Limitations requiring testing:** exact category filter support is unverified against the owner's actual provider, category name collisions trigger safe full fallback, native credential/VPN routing must remain private, physical Android scrolling/frame-time and provider request durations are not verified by emulator-only CI. A UI category mode does not imply every title/genre/favorite is loaded until the user selects All. Do not ship a debug-signed APK over a working phone; issue #54 still applies.

Relates to integrated PR #74 and prior PRs #72/#73. Not a public release.
