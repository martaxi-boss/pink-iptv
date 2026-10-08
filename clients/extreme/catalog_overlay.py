"""Demand-first Android catalogs; pinned upstream remains the cache/UI owner."""
from pathlib import Path
import shutil
import subprocess


def apply(root: Path, destination: Path):
    def edit(path, before, after, count=1):
        target = destination / path
        text = target.read_text()
        if text.count(before) != count:
            raise SystemExit(f"Catalog upstream drift: {path}")
        target.write_text(text.replace(before, after))

    for name in ("pink-catalog-pipeline.js", "pink-catalog-worker.js"):
        shutil.copyfile(root / "overlay" / name, destination / "src/scripts/lib" / name)
    revision = subprocess.check_output(["git", "-C", str(root), "rev-parse", "--short=8", "HEAD"], text=True).strip()
    diagnostic = (root / "overlay/pink-catalog-diagnostic.js").read_text().replace("__PINK_CATALOG_REVISION__", revision)
    (destination / "src/scripts/lib/pink-catalog-diagnostic.js").write_text(diagnostic)
    shutil.copyfile(root / "overlay/pink-catalog-diagnostic.test.ts", destination / "tests/pink-catalog-diagnostic.test.ts")
    shutil.copyfile(root / "overlay/pink-catalog-pipeline.test.ts", destination / "tests/pink-catalog-pipeline.test.ts")
    shutil.copyfile(root / "overlay/pink-catalog-browser.mjs", destination / "tests/pink-catalog-browser.mjs")
    android_tests = destination / "src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme"
    shutil.copyfile(root / "overlay/vpn-tests/PinkCatalogWorkerChecks.kt", android_tests / "PinkCatalogWorkerChecks.kt")
    shutil.copyfile(root / "overlay/vpn-tests/PinkCatalogTransportChecks.kt", android_tests / "PinkCatalogTransportChecks.kt")
    assets = android_tests.parents[3] / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    # Exercise the exact production JS transport in a test-only WebView with
    # real Android HTTP/disk/chunk replies. No fixture is shipped in the app.
    scripts = [diagnostic] + [(root / "overlay" / name).read_text() for name in ("pink-bridge.js", "pink-catalog.js")]
    (assets / "pink-catalog-transport.js").write_text("\n".join(
        "\n".join(line for line in source.splitlines() if not line.startswith("import ")).replace("export ", "")
        for source in scripts))
    edit("src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme/PinkWebBridgeTest.kt",
         '        val instrumentation = InstrumentationRegistry.getInstrumentation()\n        val context = instrumentation.targetContext',
         '        PinkCatalogTransportChecks().verify()\n        val instrumentation = InstrumentationRegistry.getInstrumentation()\n        val context = instrumentation.targetContext')
    edit("src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme/PinkVpnStartupTest.kt",
         '            PinkVodTracksChecks().allRealAudioAndTextTracksIncludingForcedCanBeSelectedWithoutBreakingVideo()',
         '            PinkCatalogWorkerChecks().verify(activity)\n            PinkVodTracksChecks().allRealAudioAndTextTracksIncludingForcedCanBeSelectedWithoutBreakingVideo()')
    edit("src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme/PinkVpnStartupTest.kt",
         '            report("STRICT_OFFLINE_CAPTURE_AND_RECREATION=PASS")',
         '            PinkCatalogWorkerChecks().verify(activity)\n            report("STRICT_OFFLINE_CAPTURE_AND_RECREATION=PASS")')

    edit("src/scripts/lib/catalog.js", '// Shared catalog fetch + parse + cache',
         '// Shared catalog fetch + parse + cache\nimport { pinkCatalogRuntime, processPinkCatalog, backgroundPinkCatalog } from "./pink-catalog-pipeline.js"')
    # The Rust staging job must never race the page/cache-owned Android request.
    edit("src/scripts/lib/catalog.js", 'if (isTauri) {', 'if (isTauri && !pinkCatalogRuntime()) {', 5)
    edit("src/scripts/lib/catalog.js", 'export async function warmupActive(playlistId, opts = {}) {',
         '''export async function warmupActive(playlistId, opts = {}) {
  if (pinkCatalogRuntime() && opts.background && !opts.force) {
    // Resolving the selected account and the sequence never blocks Home.
    void backgroundPinkCatalog(playlistId || "selected", async (kind) => {
      const creds = await loadCreds()
      const { getActiveEntry } = await import("@/scripts/lib/creds.js")
      const active = await getActiveEntry()
      const pid = playlistId || active?._id
      if (!pid || active?._id !== pid) throw new Error("CATALOG_CANCELLED")
      const load = { live: ensureLive, vod: ensureVod, series: ensureSeries }[kind]
      await load(creds, pid)
    })
    return { live: [], vod: [], series: [], errors: {} }
  }''')
    # Check before JSON.parse: all heavy VOD work belongs to the worker.
    edit("src/scripts/lib/catalog.js", '    const parsed = JSON.parse(body)\n    const arr = Array.isArray(parsed)\n      ? parsed\n      : parsed?.movies || parsed?.results || []',
         '    if (pinkCatalogRuntime()) return processPinkCatalog(body, "vod", catMap, playlistId, VOD_TTL_MS)\n    const parsed = JSON.parse(body)\n    const arr = Array.isArray(parsed)\n      ? parsed\n      : parsed?.movies || parsed?.results || []')
    edit("src/scripts/lib/catalog.js", '    const parsed = JSON.parse(body)\n    const arr = Array.isArray(parsed) ? parsed : parsed?.series || parsed?.results || []',
         '    if (pinkCatalogRuntime()) return processPinkCatalog(body, "series", catMap, playlistId, SERIES_TTL_MS)\n    const parsed = JSON.parse(body)\n    const arr = Array.isArray(parsed) ? parsed : parsed?.series || parsed?.results || []')

    for path, before, after in (
        ("src/components/Sidebar.astro", 'm.warmupActive()', 'm.warmupActive(undefined, { background: true })'),
        ("src/scripts/tv/shell.ts", 'mod.warmupActive(entry._id)', 'mod.warmupActive(entry._id, { background: true })'),
        ("src/scripts/tv/views/home.ts", 'mod.warmupActive(activePlaylistId)', 'mod.warmupActive(activePlaylistId, { background: true })'),
        ("src/scripts/lib/connectivity.ts", 'warmupActive().catch', 'warmupActive(undefined, { background: true }).catch'),
    ):
        edit(path, before, after)

    # Detail, search, warmup and page now share identical VOD fetchers/mappers.
    for kind, path, function, shared in (
        ("vod", "src/scripts/movies/movies.ts", "fetchMovieRows", "ensureVod"),
        ("series", "src/scripts/series/series.ts", "fetchSeriesRows", "ensureSeries"),
    ):
        target = destination / path
        text = target.read_text()
        begin = text.index(f'async function {function}() {{')
        end = text.index('\nasync function load', begin)
        text = text[:begin] + f'''async function {function}() {{
  // This function is itself passed to cachedFetch: use the shared raw fetcher
  // rather than recursively joining the cache promise that owns this load.
  const {{ fetchPink{shared[6:]}Rows }} = await import("@/scripts/lib/catalog.js")
  return fetchPink{shared[6:]}Rows(activePlaylistId)
}}
''' + text[end:]
        # Obsolete category loader has no consumers after unifying the fetcher.
        begin = text.index('async function ensure' + ('Vod' if kind == 'vod' else 'Series') + 'CategoryMap() {')
        end = text.index('\n}', begin) + 2
        text = text[:begin] + text[end:]
        text = text.replace('  fetchCategoryMap,\n', '').replace('let categoryMap = null\n', '')
        text = text.replace('import { xtreamApiFetch } from "@/scripts/lib/xtream-api.js"\n', '')
        text = 'import { catalogFailureDetail } from "@/scripts/lib/pink-catalog-diagnostic.js"\n' + text
        text = text.replace('      onRetry: load' + ('Movies' if kind == 'vod' else 'Series') + ',',
                            '      onRetry: load' + ('Movies' if kind == 'vod' else 'Series') + ',\n      detail: catalogFailureDetail(e),')
        target.write_text(text)

    # A fresh full Android response already contains all provider metadata.
    # Re-downloading it cannot fill fields the provider omitted, and would race
    # the next background stage. Explicit refresh remains available.
    edit("src/scripts/lib/tmdb-backfill.ts", 'import { cachedFetch, CACHE_REVALIDATED_EVENT }',
         'import { pinkCatalogRuntime } from "./pink-catalog-pipeline.js"\nimport { cachedFetch, CACHE_REVALIDATED_EVENT }')
    edit("src/scripts/lib/tmdb-backfill.ts", '  const key = `${playlistId}:${kind}`',
         '  if (pinkCatalogRuntime()) return\n  const key = `${playlistId}:${kind}`')

    # Export reusable uncached fetchers; the cache/queue owns deduplication.
    target = destination / "src/scripts/lib/catalog.js"
    text = target.read_text()
    text = 'import { catalogActionFailure } from "./pink-catalog-diagnostic.js"\n' + text
    for kind, title, start_marker, end_marker in (
        ("vod", "Vod", '  const fetcher = () => retryWithBackoff(async () => {', '  const { data } = await cachedFetch(playlistId, "vod"'),
        ("series", "Series", '  const fetcher = () => retryWithBackoff(async () => {', '  const { data } = await cachedFetch(playlistId, "series"'),
    ):
        section = text.index(f'export async function ensure{title}')
        begin = text.index(start_marker, section)
        end = text.index(end_marker, begin)
        body = text[begin:end].replace('  const fetcher = () =>', '  return', 1)
        body = body.replace('  })', '  }, { tries: pinkCatalogRuntime() ? 1 : 3 })')
        body = body.replace(f'fetch{title}CategoryMap()', f'fetch{title}CategoryMap(playlistId)')
        helper = f'export async function fetchPink{title}Rows(playlistId) {{\n  const onBytes = makeBytesEmitter(playlistId, "{kind}")\n' + body + '}\n\n'
        text = text[:begin] + f'  const fetcher = () => fetchPink{title}Rows(playlistId)\n' + text[end:]
        # Remove the now-unused emitter from ensure*.
        section = text.index(f'export async function ensure{title}')
        position = text.index(f'  const onBytes = makeBytesEmitter(playlistId, "{kind}")\n', section)
        text = text[:position] + text[position:].replace(f'  const onBytes = makeBytesEmitter(playlistId, "{kind}")\n', '', 1)
        text = text[:section] + helper + text[section:]
        text = text.replace(f'async function fetch{title}CategoryMap() {{', f'async function fetch{title}CategoryMap(playlistId) {{')
        action = 'get_vod_categories' if kind == 'vod' else 'get_series_categories'
        text = text.replace(f'xtreamApiFetch("{action}")', f'xtreamApiFetch("{action}", {{}}, {{entryId: playlistId}})')
        action = 'get_vod_streams' if kind == 'vod' else 'get_series'
        text = text.replace(f'xtreamApiFetch("{action}")', f'xtreamApiFetch("{action}", {{}}, {{entryId: playlistId}})')
        text = text.replace(f'return processPinkCatalog(body, "{kind}", catMap, playlistId, {kind.upper()}_TTL_MS)',
                            f'return processPinkCatalog(body, "{kind}", catMap, playlistId, {kind.upper()}_TTL_MS).catch(error => {{ throw catalogActionFailure(error, "{action}", "WORKER_LOAD") }})')
        category_action = 'get_vod_categories' if kind == 'vod' else 'get_series_categories'
        # Chromium's Response.json() can replace a ReadableStream rejection
        # with a generic TypeError. Read with the existing stream reader so
        # the native fixed failure code survives, then parse the complete body.
        begin = text.index(f'async function fetch{title}CategoryMap(playlistId) {{')
        end = text.index('\n}', begin)
        category = text[begin:end]
        assert category.count('r.json()') == 1
        category = category.replace('r.json()', '(pinkCatalogRuntime() ? streamingText(r).then(JSON.parse) : r.json())')
        text = text[:begin] + category + text[end:]
        # A bridge/chunk failure is not an empty category map. Retain its exact
        # safe phase instead of silently proceeding with a second full request.
        marker = f'    log.warn("[xt:catalog] {kind} categories parse failed:", err?.message || err)'
        assert text.count(marker) == 1
        text = text.replace(marker, f'    if (pinkCatalogRuntime()) throw catalogActionFailure(err, "{category_action}", "CATEGORY_PARSE")\n' + marker)
    target.write_text(text)

    edit("src/scripts/lib/cache.js", '// IndexedDB-backed catalog cache with in-memory hydration layer',
         '// IndexedDB-backed catalog cache with in-memory hydration layer\nimport { pinkCatalogRuntime, schedulePinkCatalog, takePinkCatalogPersistence } from "./pink-catalog-pipeline.js"')
    # Share the existing schema/opener/write code with the worker. No second
    # cache format, and no worker -> cache -> worker bundle dependency cycle.
    target = destination / "src/scripts/lib/cache.js"
    text = target.read_text()
    start = text.index('const idbOpener = createTimedIdbOpener({')
    end = text.index('async function idbGet', start)
    opener = text[start:end].replace('function openDB()', 'export function openDB()')
    text = text[:start] + text[end:]
    start = text.index('async function idbPut(')
    end = text.index('async function idbDelete(', start)
    writer = text[start:end].replace('async function idbPut(', 'export async function idbPut(')
    text = text[:start] + text[end:]
    text = text.replace('import { createTimedIdbOpener } from "@/scripts/lib/idb-open.ts"',
                        'import { openDB, idbPut } from "./pink-catalog-store.js"')
    text = text.replace('const DB_NAME = "xt_cache"\n', '').replace('const DB_VERSION = 4\n', '')
    target.write_text(text)
    store = '''import { log } from "@/scripts/lib/log.js"
import { createTimedIdbOpener } from "@/scripts/lib/idb-open.ts"
const DB_NAME = "xt_cache"
const DB_VERSION = 4
const STORE = "entries"
const FETCHED_AT_INDEX = "fetchedAt"
''' + opener + writer + '''export async function persistPinkCatalog(entryId, kind, payload) {
  return idbPut(`xt_cache:${entryId}:${kind}`, payload)
}
'''
    (destination / "src/scripts/lib/pink-catalog-store.js").write_text(store)
    edit("src/scripts/lib/cache.js", '  const payload = { data, fetchedAt: Date.now(), ttl: ttlMs }\n  _mem.set(key, payload)\n  idbPut(key, payload)',
         '''  const persisted = takePinkCatalogPersistence(data, entryId, kind)
  const payload = { data, fetchedAt: persisted?.fetchedAt || Date.now(), ttl: ttlMs }
  _mem.set(key, payload)
  if (persisted && persisted.ttl === ttlMs) return
  idbPut(key, payload)''')
    edit("src/scripts/lib/cache.js", 'makeKey(entryId, kind) + (opts.force ? ":force" : "")',
         'makeKey(entryId, kind) + (opts.force && !(pinkCatalogRuntime() && ["live", "vod", "series"].includes(kind)) ? ":force" : "")')
    edit("src/scripts/lib/cache.js", '      const data = await fetcher()',
         '      const data = await schedulePinkCatalog(makeKey(entryId, kind), kind, fetcher)', 2)
