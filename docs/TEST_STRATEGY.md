# Test Strategy

## Automated CI proof

CI uses Python 3.12 and a real PostgreSQL service. From a fresh database it runs:

1. dependency installation from pinned project requirements;
2. Ruff lint;
3. Ruff format check;
4. `alembic upgrade head`;
5. schema assertions against PostgreSQL;
6. automated pytest suite with no live Mega/Xtream calls;
7. secret scan over repository content.

SQLite is used only for isolated unit tests where PostgreSQL behavior is not the subject of the test.

## Mega adapter

Mocked tests verify Bearer authorization, retrieve-by-ID, id mismatch fail-closed behavior, M3U type validation, malformed responses, 4xx/5xx, timeouts, and password/token log redaction.

## Mapping

Tests cover valid import, idempotency, unique Mega id/username constraints, timezone-aware expiry contract, `dns_link` refresh, and the absence of any password column.

## Outbound URL safety

Tests cover public hostnames, malformed URLs, unsupported schemes, embedded credentials, localhost, loopback, private IP, link-local IP, DNS resolution to non-public space, and redirect refusal.

## Session resolve

Tests cover `SUCCESS`, unknown username, wrong password, authoritative expiry, explicit disabled state, DNS failure, timeout, invalid JSON and 5xx classification. Unknown username and wrong password responses are compared for equality.

## Secrets and session token

Tests assert that customer passwords, Mega tokens, Authorization values and Xtream auth URLs do not appear in logs. Signed session claims are inspected to prove the short lifetime and absence of customer/provider credentials and Mega subscription id.

## Live proof — separate from CI

Live proof is never executed in CI and requires an Owner-authorized test line plus secrets injected only into a secure execution environment.

Required live sequence:

1. retrieve the known Mega subscription by id;
2. import the mapping;
3. verify id plus SHA-256 evidence for username and `dns_link` and record only the `http`/`https` scheme;
4. prove the Mega response password was not persisted;
5. call `/v1/session/resolve` with the authorized username/password;
6. require Xtream `SUCCESS` using exactly the mapped `dns_link`;
7. retry with a wrong password and require `INVALID_CREDENTIALS`.

Evidence may include the Mega subscription id, masked/hash username evidence, SHA-256 `dns_link` evidence, scheme, mapping hash match, and the result `Xtream auth: SUCCESS`. Passwords, tokens and credential-bearing URLs are forbidden from evidence.

Order 001 live proof is complete and passed before merge. Automated CI remains separate from live-provider proof and must never use production credentials.

## Android Shell 002 automated proof

Android CI uses JDK 17 and the committed Gradle wrapper. It runs `lintDebug`, all debug JVM unit tests, `assembleDebug`, compilation/assembly of Android instrumentation-test sources, and repository secret scanning without production signing or credentials.

Order 002 JVM tests cover exact username/password request serialization, backend response-code mapping, login success/failure/temporary states, startup decisions with and without stored credentials, failed-login no-save behavior, invalid stored-credential clearing, logout clearing, HTTPS backend URL policy, and the encrypted credential-store contract. Stage 003A extends these tests with provider runtime-session lifecycle and catalog networking.

Compose instrumentation sources cover the Login control contract, Home navigation, and D-pad focus movement. They must compile in CI. Device execution is reported separately and is never invented when an emulator/device is unavailable.

### Order 002 device matrix

- phone/tablet: launch, Splash, Login, touch navigation and adaptive Home layout;
- Android TV / TV Box: launch, D-pad-only Home navigation, strong focus state, OK activation, Back behavior and Login with remote plus system keyboard.

No Order 002 device proof may use hidden production fake-login behavior. Test-only composition/fakes are allowed only in test sources.

Order 002 is complete and merged at `f83542a31ff7ac0fbd08ceab3531d3d92ee03a10`; phone proof PASS and TV proof PASS.

## Android Catalog + Player 003A automated proof

Stage 003A is complete and merged.

Approved head: `f6ad7a5f53afb7aded8db9e8841ae626f236f5d4`.

Merge commit: `135df164de9bb30e66b3b4268fbfc38c8b204e1a`.

Stage 003A adds JVM coverage for:

- runtime provider session creation only after backend SUCCESS;
- runtime session rebuild after startup reauthentication and clearing on logout/auth failure;
- absence of password fields from public `AppUiState`;
- exact Xtream origin preservation and HTTP/HTTPS origin policy;
- continued HTTPS-only policy for the PINK Backend;
- disabled provider redirects and stable `PINK-IPTV/0.1` User-Agent;
- Live, VOD and Series category/item parsing including numeric/string ids and nullable metadata;
- empty payloads, malformed top-level payloads, HTTP failures, timeout/network failures and missing runtime sessions;
- credential-free error representations;
- catalog controller content/category filtering, empty and recoverable-error states.

Compose instrumentation sources cover Live/Movies/Series loading/content/empty/error surfaces, category/item browsing, TV focus movement, retry and Back behavior using deterministic fake UI state. CI never calls a live provider.

Order 002 phone-device proof remains enabled on pull requests. Its `NavigationShellTest` is reconciled to the real Live catalog route rather than the former placeholder.

## Android Catalog + Player 003B automated proof

Stage 003B is complete and merged.

Approved head: `484b51007ee55ca9fbc5c921bb9bc1b5199a63c4`.

Merge commit: `b98ce7a819d6c752c3ec8e5b45646c79fbe2a3d8`.

Stage 003B adds deterministic coverage for:

- credential-free typed Live and VOD playback references;
- exact authoritative HTTP/HTTPS playback origin and explicit-port preservation;
- structured Live and Movies/VOD path construction;
- stream-id and VOD container-extension validation;
- missing runtime-session fail-closed behavior;
- absence of credential/URI fields from public player UI state;
- Series selection remaining non-playback;
- safe Media3 state/error mapping;
- player loading/error/retry and seek-control rendering through deterministic fakes;
- Live and Movie catalog navigation into the player with no credential route argument;
- Back returning to the previous catalog;
- TV-focusable player controls.

No Android CI test requires live provider media or production/provider credentials. The existing pull-request phone-device-proof remains unchanged and continues to execute `NavigationShellTest`, which now covers deterministic Live/Movie player navigation and Back without real media.

Real-provider playback proof, when separately authorized and securely available, remains sanitized and outside CI.

## Android Catalog + Player 003C automated proof

Stage 003C is complete and merged.

Approved head: `50ca487ccd7f14fd2e5b7be71b3d3d4bed38230b`.

Merge commit: `39d5b9e6d43b48c83a966b994d9ffbe66fec5ccb`.

Stage 003C adds deterministic coverage for:

- exact `get_series_info` requests using the selected `series_id`;
- authoritative origin, redirect-off provider transport and internal credential query handling;
- grouped and array-style episode response parsing with numeric/string identity variants;
- missing optional metadata, empty seasons/episode groups and malformed-response failure;
- HTTP, timeout/network and missing-session safe outcomes;
- Series-detail loading/content/empty/error/retry states;
- deterministic provider-defined season selection and episode filtering;
- credential-free `EpisodePlaybackRef` and public Series models;
- canonical `/series/` playback URL generation with HTTP/HTTPS/explicit-port preservation;
- episode-id and container-extension rejection rules;
- `PlaybackKind.Series` through the existing Media3 player;
- Series catalog -> detail -> episode -> player -> Back navigation;
- phone touch actions and deterministic TV/D-pad Series-detail focus/navigation.

The existing pull-request phone-device-proof remains unchanged. `NavigationShellTest` now retains Live/Movie coverage and additionally proves Series -> detail -> Episode Player -> Back without live provider access.

Real-provider Series info/episode playback remains a separate sanitized proof and is not required in CI.

## Android Catalog + Player 003D automated proof

Status: COMPLETE / MERGED.

Approved head: `ba3cbf66c85bf30381aef809cb0169b5ce03ad86`.

Merge commit: `344166836661a7b340750fd921036e8b2ff0c671`.

Stage 003D adds deterministic coverage for:

- Live `epg_channel_id`, numeric/string/null `tv_archive` and `tv_archive_duration` normalization without exposing `direct_source`;
- exact bounded `get_short_epg` and `get_simple_data_table` requests using selected Live `stream_id`;
- authoritative HTTP/HTTPS origin and explicit-port preservation with redirects disabled;
- invalid stream IDs and unbounded short-EPG limits rejected before network;
- EPG HTTP, timeout/network and missing-session safe outcomes;
- `epg_listings` parsing with numeric/string ids, timestamps, flags, Base64 text, plain-text fallback, nullable metadata and deterministic timestamp ordering;
- channel-centric loading/content/empty/error/retry state with selected-channel now/next;
- fail-closed Catch Up eligibility, future/current rejection, strict provider-start formatting and bounded rounded duration;
- credential-free `CatchUpPlaybackRef`, `PlaybackKind.CatchUp` and canonical HLS `/timeshift/` source construction;
- HTTP/HTTPS/explicit-port preservation for Catch Up and redacted resolved-source output;
- Home -> EPG -> archived programme -> Catch Up Player -> Back using deterministic fake state;
- deterministic Android TV focus from channel to programme to Catch Up action.

The pull-request phone-device proof continues to execute the genuine Order 002 classes. `NavigationShellTest` retains Live/Movie/Series coverage and adds deterministic EPG -> Catch Up Player -> Back without provider credentials.

Real-provider EPG/Catch Up fetch/playback is a separate sanitized proof and is NOT CERTIFIED by CI.

## Android Catalog + Player 003E automated proof

Status: COMPLETE / MERGED.

Approved head: `0c5fcecc713a7936d748ff5cd4d73c2743edb98c`.

Merge commit: `da8164045280f05cb3467ac0da025128a65de9dd`.

Stage 003E uses Room 2.8.5 with KSP 2.3.12 and exported Room schema version 1.

Stage 003E adds deterministic coverage for:

- SHA-256 local profile-key derivation, same-account stability, account isolation and absence of plaintext username in persisted entities;
- Room schema version 1 creation, Favorite composite identity, profile filtering, Flow invalidation, History newest-first ordering, 100-row retention/pruning and current-profile clearing;
- Live, Movie/VOD and Series Favorites, duplicate-safe toggling, per-kind identity, removal and typed reopen behavior;
- Live, Movie/VOD and Series Episode recent-history metadata with Catch Up excluded;
- history creation only after usable player state, approximately 10-second progress-write throttling, pause/exit/ended flush behavior and session-clear protection;
- Continue Watching eligibility for seekable Movies/VOD and Series Episodes only, including 30-second minimum progress, 60-second minimum duration, 90% completion exclusion and completed-media exclusion;
- overflow-safe resume/progress calculations and typed credential-free resume reconstruction;
- Favorites screen FAVORITOS / CONTINUAR / RECENTES sections, Portuguese empty states, remove Favorite and clear-history actions;
- deterministic phone navigation for Favorite Live/Movie/Series, Continue Watching and Recent playback through existing Player/Series Detail surfaces;
- deterministic Android TV/D-pad section focus and action controls without live provider credentials.

Room remains device-local and no Favorites/history data is uploaded to the PINK Backend. Catch Up history persistence remains outside Stage 003E. Public launch remains NO.

## Android Catalog + Player 003F automated proof

Status: COMPLETE / MERGED.

Approved head: `b2e1fa406c55514b43d19fb98ffe332da9563ed8`.

Merge commit: `edf93ce866404c16ddc51659608643e2c3aa1e73`.

Certified Stage 003F runs:

- Android CI `36925014754`: SUCCESS;
- backend CI `36925014938`: SUCCESS;
- phone-device-proof: SUCCESS.

No post-merge CI is inferred.

Stage 003F adds deterministic coverage for:

- runtime-only concurrent loading of existing Live, VOD and Series catalog list operations with no invented provider Search endpoint;
- no provider request on query or content-filter changes and selective retry of failed sources;
- query trim, empty/one-character behavior, Unicode-safe case-insensitive and accent/diacritic-insensitive matching;
- deterministic exact-title, prefix, contains, alphabetical and stable-provider-identity ordering with bounded results per type;
- soft partial failure, full recoverable failure and successful-source searchability during a partial failure;
- provider-session invalidation clearing query, filters, source cache and results;
- generation/session identity preventing late old-account or old-retry results from repopulating Search;
- exact typed `LivePlaybackRef` and `VodPlaybackRef`, VOD extension preservation and exact Series `seriesId` without eager `seriesInfo` calls;
- Home -> Pesquisa, query/filter/result/favorite interaction, existing Player/Series Detail reuse and Back context preservation;
- deterministic phone navigation plus Android TV/D-pad focus from query to filters, results and favorite actions;
- continued Room schema version 1, Room runtime smoke, Media3 1.11.1 and existing Login/EPG/Catch Up behavior.

The phone-device proof executes the genuine startup, Login, NavigationShell and Keystore/Room smoke classes. `NavigationShellTest` includes deterministic Search -> Live/Movie Player -> Back and Search -> Series Detail -> Back flows without provider credentials.

Real-provider Search proof remains NOT CERTIFIED / NOT EXECUTED WITH SECURE CREDENTIALS. It would only show that existing `get_live_streams`, `get_vod_streams` and `get_series` sources populate Search and produce a local query match; no separate provider Search endpoint exists.

## Phase 3 closure evidence note

Phase 3 — Android Xtream + Player — IMPLEMENTATION STATUS: COMPLETE / MERGED.

Across the merged Phase 3 stages, automated/device evidence includes exact-head Android CI coverage, JVM tests, Compose/instrumentation compilation, phone emulator device proof, startup/login regression, navigation regression, Keystore proof, Room runtime smoke and secret scanning.

Foundation / Mega/Xtream authentication proof is CERTIFIED.

Later Phase 3 real-provider evidence remains separate and is NOT CERTIFIED / NOT EXECUTED WITH SECURE CREDENTIALS where applicable for Live playback start, VOD playback start, Series info/episode playback, EPG retrieval, Catch Up playback and Global Search catalog-source proof. These are residual validation gates, not known code blockers.

Android Shell 002 physical TV proof is CERTIFIED / PASS. Physical-TV proof for later Phase 3 feature surfaces — Live/VOD player, Series detail/episode player, EPG/Catch Up, Favorites/History/Continue Watching and Global Search — is NOT SEPARATELY CERTIFIED. Deterministic TV/D-pad source tests and CI compilation are not physical-device certification.

DEPLOY = NO. RELEASE = NO. PUBLIC LAUNCH = NO.

## Future client/platform matrices retained

Order 001 backend proof does not remove the test plan for later phases.

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

## R1 outbound binding proof

Automated tests additionally prove that a validated public DNS result is pinned into the real transport connection, a subsequent/private peer swap is rejected before request bytes are written, provider paths are rejected, HTTP `Host` is preserved, and HTTPS SNI plus certificate verification remain enabled.
