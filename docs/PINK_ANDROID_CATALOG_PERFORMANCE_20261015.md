# PINK IPTV Android catalog performance audit — 2026-10-15

The owner reports fast Live TV but roughly five minutes to load Movies and still longer for Series. The physical screenshots show approximately 28,590 channels and 120,886 movies (657 in the selected film category). These screenshots alone do not measure which operation consumes the time.

## Confirmed code
The Android Home background sequence previously started Live, then downloaded the entire Movies catalog, then the entire Series catalog. The VPN-bound native queue does not preempt a running transfer. Native full-catalog transfer caps are 256 MiB and 180 seconds **per request**. Even if a user opens only Series, an automatic Movies job can occupy the queue. The application already has a Web Worker, persistent IndexedDB cache and page-sized painting; absence of modern "plugins" is not an established cause.

## First low-risk fix
Only Live is warmed at Home. Movies/Series load on visit using the existing cache, Worker, full-data semantics and deduplicated protected transport. Indexed-loop base64 chunk decoding avoids per-byte callback overhead. Regression tests enforce no automatic VOD/Series transfer, correct Series priority and graceful Live failure. Existing browser and Android native tests remain mandatory.

## Unresolved: cold catalog latency
A first Movies/Series visit still requests **all** provider titles. No improvement to first-load time is promised without physical measurements. Follow-up: collect *privacy-safe timings only* for provider fetch, native staging, bridge decode, worker mapping, cache read/write and first paint. Test optional provider `category_id` filtered requests with strict validation, dedicated per-category cache keys, compatibility fallback and correct All categories, global search, favorites, language grouping and Android TV UI. Never expose credentials, provider URLs or raw media catalogs in diagnostics.

This PR does not update, uninstall or release the owner's app, change the VPN/account identity, or launch commercially. Require exact-SHA CI, security and physical acceptance; safe QA signing remains a separate gate.
