# Complete Extreme client migration

## Startup refinement045

Physical feedback confirms PINK login renders, but the upstream first-launch
release popup still displayed the Infinity name, artwork and community links.
Task045 removes that automatic popup and upstream community/donation promotions;
optional release/support links now target the PINK repository. GPL notices and
upstream attribution remain in the corresponding source.

Login uses the same PINK service and existing account credentials as the earlier
client. `INVALID_CREDENTIALS` is a backend response, not an Infinity account
request. The UI now clarifies this; authentication and mapping are unchanged.
No real-account authentication or physical playback success is inferred from
this change. Source recipes exclude generated Python caches.

Owner-selected base: complete Extreme InfiniTV 1.9.0, pinned to
`1efcc1b4ab3468db04aedd1c466228c52ef001b7`. This is the new client foundation,
not a visual mockup or a recreation of its layout. Catalogs, artwork, search,
favorites, progress, EPG, player engines, native Android player and TV focus
navigation come from that source. `clients/extreme/prepare.py` applies a bounded
overlay to the complete checkout; the workflow emits the patched corresponding
source and rebuild recipe alongside the APK.

PINK adaptations:

- PINK IPTV display identity, P ribbon/play launcher and loading image;
- phone and TV login replaced with PINK username/password-only forms;
- demo/provider URL entry removed from the login and welcome paths;
- exact provider origin comes only from the established HTTPS backend;
- saved account protected with Android Keystore AES-GCM, no plaintext storage
  fallback, no account in backup export/import, Android backups disabled;
- backend revalidation when restoring the saved account; a failed check preserves
  the encrypted account and returns to login instead of granting stale access;
- backend-compatible User-Agent and MPEG-TS live default;
- upstream desktop updater endpoint removed from the branded build.

The native Android bridge follows upstream's local Tauri WebView interface
pattern. Its account methods must not be exposed to remote content. The source
already uses separate isolated WebViews for third-party stream sniffing; the PINK
vault is registered only on the hosted app WebView. Key loss fails closed.

The existing backend, PostgreSQL mappings, Mega adapter and authorized runtime
are retained. This task does not deploy or alter them. The earlier Kotlin client
remains recovery history while the new full Extreme client is validated. Existing
WireGuard implementation is not claimed to be present in the new Tauri host;
porting its approved client contract is the next roadmap integration.

CI checks the backend login contract and selected upstream playback regression
tests, builds the full frontend and Android binaries, and packages the exact
modified source under GPL-3.0-or-later with all original notices. This does not
claim that the entire upstream test suite passes after intentional login/storage
replacement. Debug signing is for device evaluation; public release remains NO.

Acceptance still requires installation on the Owner's phone/TV, account restore,
real Live/VOD/Series audio/video and responsive navigation. Upstream completeness
does not certify these against the PINK provider. Do not mark the earlier black
screen or freezes fixed until that evidence exists.

Control lineage: tasks042/043 were superseded before acceptance. Task042 changed
its authorization through an incorrect intermediate tree; task043 used raw-file
hashes where the canonical recovery journal requires normalized JSON hashes.
Neither is certifying and neither was merged/released/deployed. Task044 uses a
fresh independently validated immutable authorization and fresh exact-SHA CI.
The fixture/provenance scan corrections remain bounded; no detector was weakened.
