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
- Room 2.8.5 / KSP 2.3.12 for non-secret local library metadata

SDK contract: compileSdk 36, targetSdk 36, minSdk 23, JDK 17.

## Completed baseline

Foundation / Mega Proof 001 is complete and merged.

Android Shell 002 is complete and merged at merge commit `f83542a31ff7ac0fbd08ceab3531d3d92ee03a10`. Phone device proof and Android TV device proof both passed.

The Login surface remains exactly USERNAME, PASSWORD and ENTRAR. The PINK Backend URL is build configuration and is never a customer field.

## Phase 3 / Stage 003A — COMPLETE / MERGED

Stage 003A approved head: `f6ad7a5f53afb7aded8db9e8841ae626f236f5d4`.

Stage 003A merge commit: `135df164de9bb30e66b3b4268fbfc38c8b204e1a`.

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

## Phase 3 / Stage 003B — COMPLETE / MERGED

Stage 003B approved head: `484b51007ee55ca9fbc5c921bb9bc1b5199a63c4`.

Stage 003B merge commit: `b98ce7a819d6c752c3ec8e5b45646c79fbe2a3d8`.

Stage 003B adds AndroidX Media3 1.11.1 foreground playback for Live TV and Movies/VOD. The player reuses the redirect-disabled provider OkHttp transport through Media3 OkHttpDataSource with `PINK-IPTV/0.1`.

Playback uses credential-free typed Live/VOD references outside the player layer. The credential-bearing media URI is constructed only inside the player layer from the active runtime provider session and is never placed in navigation, public player UI state, saved state, logs or documentation.

Live uses the canonical Xtream live path. Movies/VOD use the catalog-provided container extension after conservative validation. Series remains browse-only. ExoPlayer ownership is scoped to the active player surface and released when that surface is permanently disposed.

## TV / input

Catalog surfaces are touch-scrollable and D-pad focusable. Focused catalog categories, items and actions use the PINK focus treatment. The Stage 003B player adds touch play/pause/retry/Back, seek controls only when media is seekable, and focusable TV/D-pad player controls. Home/Login behavior from Order 002 remains intact.

## Phase 3 / Stage 003C — COMPLETE / MERGED

Stage 003C approved head: `50ca487ccd7f14fd2e5b7be71b3d3d4bed38230b`.

Stage 003C merge commit: `39d5b9e6d43b48c83a966b994d9ffbe66fec5ccb`.

Stage 003C adds Series detail through `player_api.php?action=get_series_info&series_id=...`, defensive seasons/episodes parsing, a phone/TV Series-detail screen, and Episode playback through the existing Media3 player.

Episode playback references contain only non-secret episode identity/display metadata. Credential-bearing episode URLs remain private to the player layer and use the exact authenticated origin plus the canonical `/series/{username}/{password}/{episode_id}.{container_extension}` path.

Real-provider Series info and episode playback are not yet certified unless a separate secure proof is executed.

## Phase 3 / Stage 003D — COMPLETE / MERGED

Stage 003D approved head: `ba3cbf66c85bf30381aef809cb0169b5ce03ad86`.

Stage 003D merge commit: `344166836661a7b340750fd921036e8b2ff0c671`.

Stage 003D approved head: `ba3cbf66c85bf30381aef809cb0169b5ce03ad86`.

Stage 003D merge commit: `344166836661a7b340750fd921036e8b2ff0c671`.

Stage 003D adds bounded channel-centric EPG for Live TV through `get_short_epg` and `get_simple_data_table`. Live archive metadata retains only credential-free `epg_channel_id`, `tv_archive` and `tv_archive_duration` semantics required for eligibility.

Programme titles/descriptions are decoded defensively when the provider supplies valid Base64, while plain text remains usable. Numeric provider timestamps drive ordering and local display; provider start text is retained separately for timeshift path creation.

Catch Up is fail-closed and appears only when both channel and programme explicitly advertise archive support, provider timing is valid, the programme is past, and a bounded positive duration can be derived. Playback reuses the existing Media3 1.11.1 player through the canonical `/timeshift/{username}/{password}/{duration}/{start}/{stream_id}.m3u8` runtime path. The credential-bearing URI never enters public models or navigation.

Real-provider EPG/Catch Up proof is NOT CERTIFIED unless separately performed securely.

## Phase 3 / Stage 003E — COMPLETE / MERGED

Stage 003E approved head: `0c5fcecc713a7936d748ff5cd4d73c2743edb98c`.

Stage 003E merge commit: `da8164045280f05cb3467ac0da025128a65de9dd`.

Stage 003E adds the app-private `pink_library.db` using Room 2.8.5, KSP 2.3.12 and exported schema version 1. The database stores only non-secret Favorites/history metadata and does not replace Android Keystore/DataStore credential persistence.

The library is partitioned by a deterministic SHA-256 profile key derived from the exact authenticated username under a fixed PINK namespace. Plaintext username, password, provider origin/hostname, session tokens and credential-bearing playback URLs are not Room fields.

Favorites support Live, Movies/VOD and Series. History supports Live, Movies/VOD and Series Episodes, is newest-first and bounded to 100 rows per profile. Catch Up is deliberately excluded from long-term history.

Continue Watching is limited to seekable Movies/VOD and Series Episodes with known duration, at least 30 seconds watched, media duration of at least 60 seconds, and progress below the 90% completion threshold. Resume uses the existing typed playback references and Media3 1.11.1 player.

The Favoritos Home route exposes FAVORITOS, CONTINUAR and RECENTES sections for phone and TV/D-pad. Logout clears the active in-memory view while persisted rows remain available when the same account authenticates again.

## Phase 3 / Stage 003F — COMPLETE / MERGED

Stage 003F approved head: `b2e1fa406c55514b43d19fb98ffe332da9563ed8`.

Stage 003F merge commit: `edf93ce866404c16ddc51659608643e2c3aa1e73`.

Certified Stage 003F evidence:

- Android CI `36925014754`: SUCCESS;
- backend CI `36925014938`: SUCCESS;
- phone-device-proof: SUCCESS;
- no post-merge CI is inferred.

Stage 003F adds the Home `Pesquisa` route and client-side Global Search across Live TV, Movies/VOD and Series.

Search loads the three existing catalog list operations concurrently where practical: `get_live_streams`, `get_vod_streams` and `get_series`. Query and content-filter changes perform local filtering only; no undocumented provider search endpoint and no request-per-keystroke behavior is introduced.

Search source data, normalized matching index/state, query and filter remain runtime/in-memory only for the authenticated provider session. Logout/session invalidation clears Search state so catalog data cannot leak between accounts. No Room migration is introduced and `pink_library.db` remains schema version 1.

Matching is case-insensitive and accent/diacritic-insensitive with deterministic exact-title, title-prefix, title-contains, alphabetical and provider-identity ordering. Rendered results are bounded per content type.

Live and Movie results reuse the existing Media3 1.11.1 player through typed credential-free playback references. Series results retain the exact `seriesId` and load Series Detail only when opened. Search favorites reuse the existing Room-backed library controller.

## Phase 3 implementation closure

Phase 3 — Android Xtream + Player — IMPLEMENTATION STATUS: COMPLETE / MERGED.

The merged implementation includes authenticated provider runtime, Live TV, Movies/VOD, Series, Series detail/seasons/episodes, Live/VOD/Series episode playback, Media3, EPG, Catch Up, Favorites, Recent history, Continue Watching, resume position, Global Search, phone UX and Android TV/D-pad implementation.

Phase 3 evidence includes exact-head Android CI coverage, JVM tests, Compose/instrumentation compilation, phone emulator device proof, startup/login regression, navigation regression, Keystore proof, Room runtime smoke and secret scanning.

Evidence not to overstate:

- Foundation / Mega/Xtream authentication proof: CERTIFIED;
- later real-provider Phase 3 proof for Live playback, VOD playback, Series info/episode playback, EPG retrieval, Catch Up playback and Global Search catalog sources: NOT CERTIFIED / NOT EXECUTED WITH SECURE CREDENTIALS where applicable;
- Android Shell 002 physical TV proof: CERTIFIED / PASS;
- later Phase 3 feature-surface physical TV proof: NOT SEPARATELY CERTIFIED.

Deterministic Android TV/D-pad tests and successful compilation/CI do not equal a physical-TV PASS.

## Residual and future work after Phase 3 closure

Catch Up history persistence and bulk XMLTV ingestion/synchronization remain outside the implemented client library scope.

Phase 4 — WireGuard is next and has not started. It remains separately supervised with rollback/snapshot, service-audit, one-test-peer, Android tunnel integration, networking-safety and measured-bandwidth gates. Phase 5 — Windows and Phase 6 — Hardening / Distribution remain future work.

DEPLOY = NO. RELEASE = NO. PUBLIC LAUNCH = NO. Phase 3 implementation completion does not constitute production-launch approval.
