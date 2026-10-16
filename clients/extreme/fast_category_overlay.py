"""Bounded on-demand Xtream category fast path for PINK Android classic views.

Runs only after the existing catalog_overlay.py and native Android phone patches.
Native transport stays inside the admitted WireGuard Network; full-cache keys are
never overwritten by a category-only response.
"""
from pathlib import Path
import shutil


def replace_once(dest: Path, relative: str, before: str, after: str) -> None:
    target = dest / relative
    current = target.read_text()
    if current.count(before) != 1:
        raise ValueError(f"PINK category fast path drift: {relative}, marker={before[:70]!r}, count={current.count(before)}")
    target.write_text(current.replace(before, after, 1))


def apply(root: Path, dest: Path) -> None:
    root, dest = Path(root), Path(dest)
    shutil.copyfile(root / "overlay/pink-fast-category.js", dest / "src/scripts/lib/pink-fast-category.js")
    shutil.copyfile(root / "overlay/pink-fast-category.test.ts", dest / "tests/pink-fast-category.test.ts")

    # The immutable native vault still owns credentials and selected-account
    # binding; only validated numeric category IDs are appended to a fixed action.
    native = "src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkVodCatalog.kt"
    replace_once(dest, native, "    fun open(action: String, entryId: String): JSONObject {",
                 "    fun open(action: String, entryId: String, categoryId: String? = null): JSONObject {")
    replace_once(dest, native,
                 'entry.getString("password"), action)',
                 'entry.getString("password"), action, categoryId)')
    replace_once(dest, native,
                 "internal fun requestUrl(origin: String, username: String, password: String, action: String): URL {",
                 "internal fun requestUrl(origin: String, username: String, password: String, action: String,\n"
                 "                                categoryId: String? = null): URL {")
    replace_once(dest, native,
                 '            return URL(origin.trimEnd(\'/\') + "/player_api.php?username=" + encode(username) + "&password=" + encode(password) + "&action=" + action)',
                 '            val scoped = if (categoryId == null) "" else {\n'
                 '                require(action == "get_vod_streams" || action == "get_series")\n'
                 '                require(Regex("[0-9]{1,12}").matches(categoryId))\n'
                 '                "&category_id=" + categoryId\n'
                 '            }\n'
                 '            return URL(origin.trimEnd(\'/\') + "/player_api.php?username=" + encode(username) + "&password=" + encode(password) + "&action=" + action + scoped)')
    # Native JVM source-level regression is packaged with the original tests.
    native_test = "src-tauri/gen/android/app/src/test/java/com/pinkiptv/extreme/PinkVodCatalogTest.kt"
    test_marker = "class PinkVodCatalogTest {"
    scoped_test = """class PinkVodCatalogTest {
    @Test fun numericCategoryScopeIsBoundToFixedProtectedCatalogActions() {
        assertEquals(
            "https://provider.example/player_api.php?username=fixture%20user&password=fixture%2Fpass&action=get_vod_streams&category_id=71",
            PinkVodCatalog.requestUrl("https://provider.example", "fixture user", "fixture/pass", "get_vod_streams", "71").toString()
        )
        assertTrue(PinkVodCatalog.requestUrl("https://provider.example", "u", "p", "get_series", "2").toString().endsWith("action=get_series&category_id=2"))
        for (bad in listOf("", "-1", "0&evil=1", "1%0A", "abcdefghijklmn", "1234567890123")) {
            try {
                PinkVodCatalog.requestUrl("https://provider.example", "u", "p", "get_series", bad)
                fail("Untrusted category admitted")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            PinkVodCatalog.requestUrl("https://provider.example", "u", "p", "get_vod_categories", "71")
            fail("Category filter on metadata action")
        } catch (_: IllegalArgumentException) { }
    }
"""
    replace_once(dest, native_test, test_marker, scoped_test)
    bridge = "src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkWebBridge.kt"
    replace_once(dest, bridge,
                 'vodCatalog.open(payload.getString("action"), payload.getString("entryId"))',
                 'vodCatalog.open(payload.getString("action"), payload.getString("entryId"),\n'
                 '                            payload.optString("categoryId").takeIf { it.isNotBlank() })')

    jsbridge = "src/scripts/lib/pink-bridge.js"
    replace_once(dest, jsbridge,
                 'openVod: (action, entryId) => call("vodCatalog", {action, entryId}, 220000),',
                 'openVod: (action, entryId, categoryId) => call("vodCatalog",\n'
                 '      categoryId ? {action, entryId, categoryId} : {action, entryId}, 220000),')
    catalog = "src/scripts/lib/pink-catalog.js"
    replace_once(dest, catalog,
                 'export async function fetchPinkVodCatalog(action, entryId, signal) {',
                 'export async function fetchPinkVodCatalog(action, entryId, signal, categoryId = null) {')
    replace_once(dest, catalog,
                 "const {token, size} = await native.openVod(action, entryId).catch",
                 "const {token, size} = await native.openVod(action, entryId, categoryId).catch")
    xtream = "src/scripts/lib/xtream-api.js"
    replace_once(dest, xtream,
                 '    if (Object.keys(params).length) throw new Error("Catálogo PINK inválido.")\n'
                 '    return fetchPinkVodCatalog(action, entry._id, fetchOpts.signal)',
                 '    const keys = Object.keys(params)\n'
                 '    const categoryId = keys.length === 1 && keys[0] === "category_id" &&\n'
                 '      ["get_vod_streams", "get_series"].includes(action) &&\n'
                 '      /^[0-9]{1,12}$/.test(String(params.category_id)) ? String(params.category_id) : null\n'
                 '    if (keys.length && !categoryId) throw new Error("Catálogo PINK inválido.")\n'
                 '    return fetchPinkVodCatalog(action, entry._id, fetchOpts.signal, categoryId)')

    shared = "src/scripts/lib/catalog.js"
    for kind, title, full_action, category_action in (
        ("vod", "Vod", "get_vod_streams", "get_vod_categories"),
        ("series", "Series", "get_series", "get_series_categories"),
    ):
        replace_once(dest, shared, f"async function fetch{title}CategoryMap(playlistId) {{",
                     f"export async function fetch{title}CategoryMap(playlistId) {{")
        replace_once(dest, shared, f"export async function fetchPink{title}Rows(playlistId) {{",
                     f"export async function fetchPink{title}Rows(playlistId, categoryId = null, suppliedCategoryMap = null) {{")
        replace_once(dest, shared,
                     f"    const catMap = await fetch{title}CategoryMap(playlistId)",
                     f"    const catMap = suppliedCategoryMap || await fetch{title}CategoryMap(playlistId)")
        replace_once(dest, shared,
                     f'    const r = await xtreamApiFetch("{full_action}", {{}}, {{entryId: playlistId}})',
                     f'    const r = await xtreamApiFetch("{full_action}", categoryId ? {{category_id: categoryId}} : {{}}, {{entryId: playlistId}})')
        replace_once(dest, shared,
                     f'processPinkCatalog(body, "{kind}", catMap, playlistId, {kind.upper()}_TTL_MS)',
                     f'processPinkCatalog(body, "{kind}", catMap, categoryId ? null : playlistId, {kind.upper()}_TTL_MS, categoryId)')

    # Filter on raw provider category IDs, not the first display label:
    # an entry may belong to multiple categories. The normal full worker
    # signature and global cache remain byte-identical by default.
    pipeline = "src/scripts/lib/pink-catalog-pipeline.js"
    replace_once(dest, pipeline,
        "export function processPinkCatalog(body, kind, categories, entryId, ttl) {",
        "export function processPinkCatalog(body, kind, categories, entryId, ttl, categoryId = null) {")
    replace_once(dest, pipeline,
        "worker.postMessage({body, kind, categories, entryId, ttl})",
        "worker.postMessage({body, kind, categories, entryId, ttl, categoryId})")
    worker = "src/scripts/lib/pink-catalog-worker.js"
    replace_once(dest, worker,
        "export function parsePinkCatalog(body, kind, categories) {",
        "export function parsePinkCatalog(body, kind, categories, scopedCategoryId = null) {")
    replace_once(dest, worker,
        '''  try {
    if (kind === "vod") return mapXtreamVodRows(raw, categories)
    if (kind === "series") return mapXtreamSeriesRows(raw, categories)
  } catch { throw catalogFailure("WORKER_MAP") }''',
        '''  try {
    let source = raw
    if (scopedCategoryId != null) {
      const chosenId = String(scopedCategoryId)
      if (!/^[0-9]{1,12}$/.test(chosenId)) throw Error("Invalid category")
      const chosenName = categories.get(chosenId)
      // Providers sometimes ignore category_id. Filter those full bodies in
      // the Worker, before mapping or painting any wrong-category posters.
      source = raw.filter(row => row && (
        String(row.category_id ?? "") === chosenId ||
        (Array.isArray(row.category_ids) &&
          row.category_ids.some(id => String(id) === chosenId)) ||
        (chosenName && String(row.category_name || "").trim() === chosenName)
      ))
      if (raw.length && source.length === 0) throw Error("Unverified category scope")
    }
    const mapped = kind === "vod"
      ? mapXtreamVodRows(source, categories)
      : kind === "series" ? mapXtreamSeriesRows(source, categories) : null
    if (!mapped) throw Error("Unknown catalog")
    if (scopedCategoryId != null) {
      const label = categories.get(String(scopedCategoryId))
      if (label) for (const row of mapped) row.category = label
    }
    return mapped
  } catch { throw catalogFailure("WORKER_MAP") }''')
    replace_once(dest, worker,
        "rows = parsePinkCatalog(data.body, data.kind, data.categories); offset = 0",
        "rows = parsePinkCatalog(data.body, data.kind, data.categories, data.categoryId); offset = 0")

    # The short category response gets its OWN IndexedDB entry. Full-list
    # search/favourites remain possible by explicitly choosing All categories.
    # Never use an unscoped cache key to persist partial items.
    for kind, title, plural, handler, storageKey in (
        ("vod", "Vod", "movies", "xt:movie-cat-changed", "xt_vod_active_cat"),
        ("series", "Series", "series", "xt:series-cat-changed", "xt_series_active_cat"),
    ):
        file = f"src/scripts/{plural}/{plural}.ts"
        page = "Movies" if kind == "vod" else "Series"
        lookup = f"fetch{title}CategoryMap"
        fetcher = f"fetchPink{title}Rows"
        helper = f"""
// Cold-category fast lane runs only on the real small Android app, not TV/web.
const PINK_FAST_PHONE = typeof window !== "undefined" && !!window.PinkNative &&
  window.matchMedia("(max-width: 720px) and (pointer: coarse)").matches
let pinkFastCategoryOnly = false
let pinkFastCategories: string[] = []
let pinkFastCurrentName = ""
let pinkFastAllRequested = false
let pinkFastGeneration = 0
"""
        replace_once(dest, file, "let all = []\n", "let all = []\n" + helper)
        replace_once(dest, file, "  getItems: () => all,",
                     "  getItems: () => pinkFastCategoryOnly ? pinkFastCategories.map(category => ({category})) : all,")
        marker = f'document.addEventListener("{handler}", (ev) => {{'
        # The category-changed event's original genre feature is retained.
        source = (dest / file).read_text()
        start = source.index(marker)
        end = source.index("\n})", start)
        before = source[start:end + 3]
        assert before.count("  applyFilter()") == 1
        # Build the listener atomically; replacing a generated fragment a
        # second time is brittle and breaks against upstream indentation.
        full_choice_key = f'"pink:full-catalog:{kind}:" + activePlaylistId'
        new_filter = f'''  if (PINK_FAST_PHONE && activePlaylistId) {{
    const key = {full_choice_key}
    if (!activeCat) {{
      pinkFastAllRequested = true
      try {{ localStorage.setItem(key, "1") }} catch {{}}
    }} else {{
      pinkFastAllRequested = false
      try {{ localStorage.removeItem(key) }} catch {{}}
    }}
  }}
  if (pinkFastCategoryOnly && activeCat !== pinkFastCurrentName) {{
    void load{page}()
    return
  }}
  applyFilter()'''
        after = before.replace("  applyFilter()", new_filter, 1)
        replace_once(dest, file, before, after)

        # Showing a 500-row category should not request a full 120k-title
        # genre index. Genre selections still fall back to the full view.
        replace_once(dest, file,
                     "  picker.rerender()\n  populateLanguageFilterOptions()\n  refreshGenreSets(activePlaylistId)\n",
                     "  picker.rerender()\n  populateLanguageFilterOptions()\n  if (!pinkFastCategoryOnly) refreshGenreSets(activePlaylistId)\n")

        fastfunction = f'''
// The visible selection is All Categories; keep the first small, fast
// category as an explicitly labelled preview while the full global list is
// fetched and persisted using its original global cache key.
async function pinkFastCompleteAll(entryId: string, token: number): Promise<void> {{
  if (token !== pinkFastGeneration || entryId !== activePlaylistId || picker.getActiveCat()) return
  try {{
    await hydrateCache(entryId, "{kind}")
    let result = getCached(entryId, "{kind}")
    if (!result) {{
      const {{ {fetcher} }} = await import("@/scripts/lib/catalog.js")
      result = await cachedFetch(entryId, "{kind}", {kind.upper()}_TTL_MS, () => {fetcher}(entryId))
    }}
    // Never overwrite the owner's later selection or another account's view.
    if (token !== pinkFastGeneration || entryId !== activePlaylistId ||
        picker.getActiveCat() !== "" || !result || !Array.isArray(result.data)) return
    pinkFastCategoryOnly = false
    document.documentElement.removeAttribute("data-pink-fast-category")
    const fromCache = result.fromCache === undefined ? true : result.fromCache
    {"await " if kind == "series" else ""}paint{page}(result.data, fromCache, result.age || 0)
  }} catch {{
    if (token === pinkFastGeneration && entryId === activePlaylistId &&
        picker.getActiveCat() === "" && pinkFastCategoryOnly && listStatus) {{
      listStatus.textContent = String(all.length) +
        " títulos de pré-visualização · Catálogo completo indisponível neste momento"
    }}
  }}
}}

async function pinkFastLoadCategory(entryId: string, token: number): Promise<boolean> {{
  if (!PINK_FAST_PHONE || pinkFastAllRequested || searchEl?.value?.trim()) return false
  const {{ {lookup}, {fetcher} }} = await import("@/scripts/lib/catalog.js")
  const {{ pickPinkFastCategory }} = await import("@/scripts/lib/pink-fast-category.js")
  // Keep the small category index cached independently of large title lists.
  const categoryResult = await cachedFetch(entryId, "{kind}-pink-category-map",
    {kind.upper()}_TTL_MS, () => {lookup}(entryId))
  const categories = categoryResult.data
  if (token !== pinkFastGeneration || entryId !== activePlaylistId) return true
  const selected = pickPinkFastCategory(categories, picker.getActiveCat())
  if (!selected) return false
  pinkFastCategories = [...new Set([...categories.values()].filter(Boolean))]
  pinkFastCurrentName = selected.name
  pinkFastCategoryOnly = true
  document.documentElement.setAttribute("data-pink-fast-category", "1")
  // Never replace the owner's All-categories preference with the quick preview.
  // Only the *native request* is scoped; the All picker remains unselected.

  const key = "{kind}-pink-category:" + selected.id
  await hydrateCache(entryId, key)
  const existing = getCached(entryId, key)
  const result = existing ? {{data: existing.data, fromCache: true, age: existing.age}} :
    await cachedFetch(entryId, key, {kind.upper()}_TTL_MS,
      () => {fetcher}(entryId, selected.id, categories))
  if (token !== pinkFastGeneration || entryId !== activePlaylistId) return true
  const scoped = result.data
  {"await " if kind == "series" else ""}paint{page}(scoped, result.fromCache, result.age)
  if (listStatus) listStatus.textContent = picker.getActiveCat()
    ? String(scoped.length) + " nesta categoria"
    : String(scoped.length) + " títulos iniciais · A carregar todas as categorias…"
  if (!picker.getActiveCat()) {{
    const heroCount = document.getElementById("{'movie' if kind == 'vod' else 'series'}-hero-count")
    if (heroCount) heroCount.textContent = scoped.length.toLocaleString() + "+"
  }}
  return true
}}
'''
        replace_once(dest, file, f"async function load{page}() {{",
                     fastfunction + f"\nasync function load{page}() {{")
        replace_once(dest, file, f"async function load{page}() {{\n  if (!listStatus) return",
                     f"async function load{page}() {{\n  if (!listStatus) return\n  const pinkFastToken = ++pinkFastGeneration")
        # Full-list cached display must disable the preview mode.
        old = ("  if (hit) {\n    paintMovies(hit.data, true, hit.age)"
               if kind == "vod" else "  if (hit) {\n    await paintSeries(hit.data, true, hit.age)")
        new = ("  if (hit) {\n    pinkFastCategoryOnly = false\n"
               "    document.documentElement.removeAttribute(\"data-pink-fast-category\")\n"
               + ("    paintMovies(hit.data, true, hit.age)" if kind == "vod" else "    await paintSeries(hit.data, true, hit.age)"))
        replace_once(dest, file, old, new)
        # Critical: the scoped category/cache must be checked BEFORE the
        # full 120k-item IndexedDB catalog is deserialized. Otherwise the
        # Android phone still blocks even though the small category is cached.
        full_cache_hydration = f'  await hydrateCache(active._id, "{kind}")'
        # Switching accounts must drop the previous account's category-only
        # view, and rehydrate the new account's explicit All choice.
        replace_once(dest, file,
            "  activePlaylistId = active._id\n  activePlaylistTitle = active.title || \"\"",
            f'''  if (activePlaylistId !== active._id) {{
    pinkFastCategoryOnly = false
    pinkFastCurrentName = ""
    pinkFastCategories = []
    document.documentElement.removeAttribute("data-pink-fast-category")
  }}
  activePlaylistId = active._id
  try {{
    pinkFastAllRequested =
      localStorage.getItem("pink:full-catalog:{kind}:" + activePlaylistId) === "1"
  }} catch {{ pinkFastAllRequested = false }}
  if (PINK_FAST_PHONE) {{
    try {{
      // One-time per-account migration clears the historical automatic
      // category choice; later user choices continue to persist normally.
      const migrated = "pink:all-default-v1:{kind}:" + activePlaylistId
      if (localStorage.getItem(migrated) !== "1") {{
        picker.setActiveCat("", {{silent: true}})
        localStorage.removeItem("pink:full-catalog:{kind}:" + activePlaylistId)
        pinkFastAllRequested = false
        localStorage.setItem(migrated, "1")
      }}
    }} catch {{ /* Storage denied: the picker still works in memory. */ }}
  }}
  activePlaylistTitle = active.title || ""''')
        quick = f'''  if (PINK_FAST_PHONE && !pinkFastAllRequested) {{
    try {{
      creds = await loadCreds()
      if (creds.host && creds.user && creds.pass &&
          await pinkFastLoadCategory(active._id, pinkFastToken)) {{
        if (picker.getActiveCat() === "") {{
          // Allow the first compact poster grid to become interactive before
          // a large full-catalog IndexedDB/provider request starts.
          setTimeout(() => {{ void pinkFastCompleteAll(active._id, pinkFastToken) }}, 180)
        }}
        return
      }}
    }} catch {{ /* Scoped API unsupported: original full catalog still works. */ }}
  }}

'''
        replace_once(dest, file, full_cache_hydration, quick + full_cache_hydration)
        # Restore the full category picker/counts after an explicit global
        # download succeeds (not only when a pre-existing full-cache hit).
        full_paint = ("    paintMovies(data, fromCache, age)"
                      if kind == "vod" else "    await paintSeries(data, fromCache, age)")
        replace_once(dest, file, full_paint,
                     '    pinkFastCategoryOnly = false\n'
                     '    document.documentElement.removeAttribute("data-pink-fast-category")\n'
                     + full_paint)
        # Only an explicit All selection or global search requests the full
        # catalog. Search always remains global (never limited to one category).
        search_marker = (
            'searchEl?.addEventListener(\n  "input",\n  debounce(() => {'
            if kind == "vod" else
            'searchEl?.addEventListener(\n  "input",\n  debounce(() => applyFilter(), 160)\n)'
        )
        replace_once(dest, file, search_marker, f'''searchEl?.addEventListener("input", () => {{
  if (pinkFastCategoryOnly && searchEl?.value?.trim()) {{
    pinkFastAllRequested = true
    picker.setActiveCat("", {{silent: true}})
    void load{page}()
  }}
}})
''' + search_marker)

    style = dest / "src/styles/global.css"
    with style.open("a") as stream:
        stream.write(
            "\n/* Category names are known before per-category item counts exist. */\n"
            "html[data-pink-fast-category=\"1\"] .category-count { visibility: hidden; }\n"
        )
    print("PINK_ANDROID_VOD_SCOPED_NATIVE_CATEGORY_FAST_PATH=PASS")
    print("PINK_ANDROID_FULL_CATALOG_CACHE_KEYS_UNCHANGED=PASS")
    print("PINK_ANDROID_FULL_SEARCH_ALL_CATEGORIES_FALLBACK=PASS")
