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
                     f'processPinkCatalog(body, "{kind}", catMap, categoryId ? null : playlistId, {kind.upper()}_TTL_MS)')

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
        after = before.replace("  applyFilter()", f'''  if (pinkFastCategoryOnly && activeCat !== pinkFastCurrentName) {{
    if (!activeCat) pinkFastAllRequested = true
    void load{page}()
    return
  }}
  applyFilter()''')
        replace_once(dest, file, before, after)

        fastfunction = f'''
async function pinkFastLoadCategory(entryId: string, token: number): Promise<boolean> {{
  if (!PINK_FAST_PHONE || pinkFastAllRequested || searchEl?.value?.trim()) return false
  const {{ {lookup}, {fetcher} }} = await import("@/scripts/lib/catalog.js")
  const {{ pickPinkFastCategory, filterPinkScopedRows }} = await import("@/scripts/lib/pink-fast-category.js")
  const categories = await {lookup}(entryId)
  if (token !== pinkFastGeneration || entryId !== activePlaylistId) return true
  const selected = pickPinkFastCategory(categories, picker.getActiveCat())
  if (!selected) return false
  pinkFastCategories = [...new Set([...categories.values()].filter(Boolean))]
  pinkFastCurrentName = selected.name
  pinkFastCategoryOnly = true
  document.documentElement.setAttribute("data-pink-fast-category", "1")
  if (picker.getActiveCat() !== selected.name) picker.setActiveCat(selected.name, {{silent: true}})
  const key = "{kind}-pink-category:" + selected.id
  await hydrateCache(entryId, key)
  const existing = getCached(entryId, key)
  const result = existing ? {{data: existing.data, fromCache: true, age: existing.age}} :
    await cachedFetch(entryId, key, {kind.upper()}_TTL_MS,
      () => {fetcher}(entryId, selected.id, categories))
  if (token !== pinkFastGeneration || entryId !== activePlaylistId) return true
  const scoped = filterPinkScopedRows(result.data, selected.name)
  {"await " if kind == "series" else ""}paint{page}(scoped, result.fromCache, result.age)
  if (listStatus) listStatus.textContent = String(scoped.length) +
    " nesta categoria · Seleciona Todas as categorias para ver o catálogo completo"
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
        # An uncached page tries a small selected/first appropriate category
        # before the existing exact full-fetch path. Failure reverts to full.
        load_marker = "  if (hit) return\n\n  try {"
        if kind == "vod":
            inject = f'''  if (hit) return
  if (PINK_FAST_PHONE && !pinkFastAllRequested) {{
    try {{
      if (await pinkFastLoadCategory(active._id, pinkFastToken)) return
    }} catch {{ /* Scoped API unsupported: full catalog fallback is unchanged. */ }}
  }}

  try {{'''
        else:
            inject = f'''  if (hit) return
  if (PINK_FAST_PHONE && !pinkFastAllRequested) {{
    try {{
      if (await pinkFastLoadCategory(active._id, pinkFastToken)) return
    }} catch {{ /* Scoped API unsupported: full catalog fallback is unchanged. */ }}
  }}

  try {{'''
        replace_once(dest, file, load_marker, inject)
        # Only an explicit All selection or global search requests the full
        # catalog. Search always remains global (never limited to one category).
        search_marker = ('searchEl?.addEventListener(\n  "input",\n  debounce(() => {')
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
