# PINK IPTV TESTE 5 — All Categories default (2026-10-15)

## Owner-verified finding
TESTE 4 is already fast for Live, Movies and Series. The Movies quick path automatically displayed 657 entries from a MULTI-LANG 2026 category, Series displayed 1,786 entries from MULTI-LANG Netflix. The owner requires both screens to open in **All Categories**, just as Live does; other providers may not have those named categories.

## New behavior, without pretending a partial result is complete
The native small-phone picker defaults to **All Categories** on first use per account and no longer automatically selects any named category. The old heuristic preferring TOP/Recent/2026 is removed. To preserve first-poster speed, the existing VPN-protected scoped category request is used only to paint an explicitly marked initial preview (count with `+`, `títulos iniciais` and loading message). The original provider-wide request and its full IndexedDB cache load next, *without clearing the preview*. When ready, the UI shows the true complete catalog and exact count. The first-ever full request may take minutes; no sub-second full-inventory claim is made.

One-time category preference migration happens only on the native phone, scoped by account, without overriding later manual preferences. TV, desktop, Live, signing, playback and WireGuard data paths remain unchanged. Account-switch/generation guards prevent stale full results painting over a new category or account. A provider without valid category-scoped data falls back to the original full route.

## Mandatory QA and signing gate
The compiled real-page Chromium smoke has a synthetic provider with two different categories and asserts All and both items are rendered in **Movies and Series** (not just a preview). Android emulator, native VPN/Keystore fail-closed, Media3, privacy scan, lint, package-signature/identity check and artifact checksum remain mandatory on the exact PR head.

Previously installed QA4 debug signing is ephemeral and cannot be replaced safely by the next run. The proposed disposable TESTE5 package is **com.pinkiptv.extreme.qa5**, distinct from original/QA2/QA3/QA4. No original uninstall, backend lease revocation, public deployment or production merge. Logging into QA5 may allocate an additional VPN installation slot, which requires owner approval before physical activation. Stabilize a persistent release signing identity before future in-place updates.
