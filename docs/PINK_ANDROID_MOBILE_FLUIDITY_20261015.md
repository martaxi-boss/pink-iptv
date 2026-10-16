# Pink IPTV Android phone fluidity — 2026-10-15

The owner has confirmed that Movies remains cached after the first long download, but the phone UI still feels continuously heavy and not fluid. This is a different issue from cold-download latency; screenshots show a >120,000-title movies inventory, and the original client eagerly builds up to 200 complex image cards at once and automatically accumulates 1,500 while scrolling.

## Source findings
- Upstream client already has native Media3, image lazy loading, image downscaling, IndexedDB, web workers and a **Performance mode** setting under Settings → Appearance. It is inaccurate to claim a missing "new plugin" is the cause.
- Existing performance mode removes expensive animation, blur/backdrop-filter and compositor hints. Its auto-on logic was limited to televisions.
- Both classic mobile VOD grids create 200 cards in a single synchronous JS pass. Each card has multiple DOM nodes, handlers, hover effects and a cached image. The poster infinite-scroll sentinel eagerly starts the next chunk at 600px, up to 1,500 auto-loaded cards.
- Movie/Series source already has getSharedGroupingIndex, but the pages called buildGroupingIndex directly for identical array references.

## Narrow mobile-only fix
- New Android phone configurations (native Pink bridge, coarse pointer, <=720px) **default Performance mode to On** before first paint; **do not override the stored explicit Off**. Keep TV's existing behavior unchanged.
- Classic Movies/Series phone grids render **24 instead of 200** cards per batch, with a 160px instead of 600px prefetch margin. After 240 cards, the existing explicit "Load more" action stays available; no rows are omitted, and search/categories still operate over the whole cached inventory.
- Disable only grid skeleton-to-cards View Transitions on small Android phone; desktop and TV retain existing transitions and batch sizes.
- Reuse the upstream shared grouping index memo for the same catalog array.
- No changes to Android signature, package name, native player, VPN, server API, wireguard lease, background catalog queue, user credentials or data schema.

## Acceptance gates
Tests cover the exact source injection, mobile/default/TV branch guards and retained page behavior. Require exact-head full Android CI including worker/browser tests, emulator Media3/VPN instrumentation, lint and secret scanning. **This source PR is not an APK for the owner.** Device responsiveness, frame rate, scroll velocity, first-open and cached-open timings remain to be measured physically; no numerical speedup is claimed. Do not uninstall the working debug-signed original app. Compare the behavior with built-in Performance mode toggled On/Off on the already-installed app first, then provision a safe side-by-side QA artifact after merging changes and signing review.
