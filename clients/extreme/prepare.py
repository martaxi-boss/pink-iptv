#!/usr/bin/env python3
"""Deterministic PINK overlay on the entire pinned Extreme source tree.

Build checkout is disposable. Original upstream remains the source of all
catalogs, playback engines, native Android player, remote navigation and layout.
"""
import json
from pathlib import Path
import shutil
import subprocess
import sys
from upstream import REVISION

ROOT = Path(__file__).resolve().parent
LOCK = json.loads((ROOT / 'upstream.json').read_text())
LOCK['revision'] = REVISION
DEST = Path(sys.argv[1]).resolve()
if subprocess.check_output(['git', '-C', str(DEST), 'rev-parse', 'HEAD'], text=True).strip() != LOCK['revision']:
    raise SystemExit('Refusing unpinned upstream source')

def replace(path, before, after, count=1):
    p = DEST / path
    text = p.read_text()
    if text.count(before) != count:
        raise SystemExit(f'Upstream drift: {path}: expected {count} matches')
    p.write_text(text.replace(before, after))

# Android Java/Rust generated paths and package identity must agree.
for p in DEST.rglob('*'):
    if '.git' in p.parts or not p.is_file() or p.suffix not in {'.kt', '.kts', '.rs', '.xml', '.json', '.pro', '.gradle', '.properties'}:
        continue
    try:
        text = p.read_text()
    except UnicodeDecodeError:
        continue
    updated = text.replace('com.infinitel8p.xtream', LOCK['applicationId'])
    if updated != text:
        p.write_text(updated)
for suffix in ['app/src/main/java', 'buildSrc/src/main/java']:
    base = DEST / 'src-tauri/gen/android' / suffix
    old = base / 'com/infinitel8p/xtream'
    if old.exists():
        new = base / 'com/pinkiptv/extreme'
        new.parent.mkdir(parents=True, exist_ok=True)
        old.rename(new)

config_path = DEST / 'src-tauri/tauri.conf.json'
config = json.loads(config_path.read_text())
config.update(productName='PINK IPTV', mainBinaryName='pink-iptv')
config['bundle']['publisher'] = 'PINK IPTV'
config['bundle']['createUpdaterArtifacts'] = False
config['plugins'].pop('updater', None)
for window in config['app']['windows']:
    window['title'] = 'PINK IPTV'
config_path.write_text(json.dumps(config, indent=2) + '\n')

# Display branding only; licenses and upstream copyright are preserved.
for base in [DEST / 'src', DEST / 'src-tauri/gen/android/app/src/main/res']:
    for p in base.rglob('*'):
        if p.is_file() and p.suffix in {'.astro', '.svelte', '.ts', '.js', '.json', '.xml'}:
            text = p.read_text()
            text = text.replace('Extreme InfiniTV', 'PINK IPTV').replace('Extreme&nbsp;Infini', 'PINK IP')
            p.write_text(text)

for src, dst in {
    'pink-session.js': 'src/scripts/lib/pink-session.js',
    'pink-login.js': 'src/scripts/lib/pink-login.js',
    'login.astro': 'src/pages/login.astro',
    'tv-login.ts': 'src/scripts/tv/views/login.ts',
    'pink-bridge.js': 'src/scripts/lib/pink-bridge.js',
    'pink-catalog.js': 'src/scripts/lib/pink-catalog.js',
    'pink-presentation.js': 'src/scripts/lib/pink-presentation.js',
    'pink-runtime-policy.ts': 'src/scripts/lib/pink-runtime-policy.ts',
    'PinkWebBridge.kt': 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkWebBridge.kt',
    'PinkVault.kt': 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkVault.kt',
    'PinkCatalog.kt': 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkCatalog.kt',
}.items():
    shutil.copyfile(ROOT / 'overlay' / src, DEST / dst)
shutil.copyfile(ROOT / 'overlay/pink-session.test.ts', DEST / 'tests/pink-session.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-storage.test.ts', DEST / 'tests/pink-storage.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-home.test.ts', DEST / 'tests/pink-home.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-login.test.ts', DEST / 'tests/pink-login.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-bridge.test.ts', DEST / 'tests/pink-bridge.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-catalog.test.ts', DEST / 'tests/pink-catalog.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-presentation.test.ts', DEST / 'tests/pink-presentation.test.ts')
shutil.copyfile(ROOT / 'overlay/pink-runtime-policy.test.ts', DEST / 'tests/pink-runtime-policy.test.ts')

replace('src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/MainActivity.kt',
        'webView.addJavascriptInterface(PipBridge(this), "AndroidPip")',
        'PinkWebBridge.attach(this, webView)\n    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n      webView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS\n    }\n    webView.addJavascriptInterface(PipBridge(this), "AndroidPip")')
replace('src-tauri/gen/android/app/src/main/AndroidManifest.xml', '<application', '<application android:allowBackup="false"')

# Native PINK owns actual connectivity state. Generic WebView navigator.onLine can
# report offline while the protected WireGuard route is healthy, so never show
# the upstream browser-offline toast from that signal in the native app.
replace('src/scripts/lib/connectivity.ts',
        'import { t } from "@/scripts/lib/i18n.js"\n',
        'import { t } from "@/scripts/lib/i18n.js"\nimport { pinkNativeOwnsConnectivity } from "@/scripts/lib/pink-runtime-policy.ts"\n')
replace('src/scripts/lib/connectivity.ts',
        '  if (navigator.onLine === false) showOfflineToast()\n  window.addEventListener("offline", showOfflineToast)\n',
        '  if (navigator.onLine === false && !pinkNativeOwnsConnectivity()) showOfflineToast()\n  window.addEventListener("offline", () => {\n    if (!pinkNativeOwnsConnectivity()) showOfflineToast()\n  })\n')

# Mega's authoritative M3U and the physical playback evidence use the legacy
# extensionless live path. The nominal Xtream /live/... route authenticates the
# catalog but returns HTTP 401 for media on this provider. Patch the shared live
# URL builder once so native playback, casting and every existing Extreme caller
# use the provider-certified path without duplicating routing policy.
replace('src/scripts/lib/stream-urls.ts',
        'import { fmtBase } from "@/scripts/lib/creds.js"\n',
        'import { fmtBase } from "@/scripts/lib/creds.js"\nimport { buildPinkLiveStreamUrl } from "@/scripts/lib/pink-runtime-policy.ts"\n')
replace('src/scripts/lib/stream-urls.ts',
        '''export function buildLiveStreamUrl(
  creds: Creds,
  streamId: string | number,
  containerExt: string | null | undefined
): string {
  const ext = containerExt === "ts" ? ".ts" : ".m3u8"
  return (
    fmtBase(creds.host, creds.port) +
    "/live/" +
    encodeURIComponent(creds.user) +
    "/" +
    encodeURIComponent(creds.pass) +
    "/" +
    encodeURIComponent(streamId) +
    ext
  )
}''',
        '''export function buildLiveStreamUrl(
  creds: Creds,
  streamId: string | number,
  _containerExt: string | null | undefined
): string {
  return buildPinkLiveStreamUrl(creds, streamId)
}''')

# Strict Android credential storage: no plaintext cookie/localStorage/store fallback.
p = DEST / 'src/scripts/lib/creds.js'
text = 'import { installPinkBridge } from "./pink-bridge.js"\n' + p.read_text()
begin = text.index('async function readRaw() {')
end = text.index('// ---------------------------------------------------------------------------\n// Migration from the legacy flat keys', begin)
text = text[:begin] + '''let pinkValidatedBlob = null
function pinkVault() {
  installPinkBridge()
  const vault = typeof window !== "undefined" && window.PinkAccountVault
  if (!vault) throw new Error("Protected account storage unavailable")
  return vault
}
async function readRaw() {
  if (!pinkValidatedBlob) {
    pinkValidatedBlob = (async () => {
      const cached = await pinkVault().readValidated()
      if (cached) return JSON.parse(cached)

      const raw = await pinkVault().read()
      if (!raw) return null
      const data = JSON.parse(raw)
      const entry = data.entries?.find(e => e._id === data.selectedId)
      if (entry) {
        try {
          const { resolvePinkSession } = await import("./pink-session.js")
          const account = await resolvePinkSession(entry.username, entry.password)
          Object.assign(entry, account)
        } catch { return null } // Keep encrypted account; show login/retry, never stale authority.
      }
      await pinkVault().markValidated(JSON.stringify(data))
      return data
    })()
  }
  return await pinkValidatedBlob
}
async function writeRaw(data) {
  if (!await pinkVault().write(JSON.stringify(data))) throw new Error("Protected account save failed")
  // The native bridge retains this already-validated blob only for this Android
  // process, so route swaps do not trigger a second control-plane login.
  // Persistent credentials remain only in the encrypted Android Keystore vault.
  localStorage.setItem("xt_playlists", JSON.stringify({ selectedId: data.selectedId || "", entries: [] }))
  pinkValidatedBlob = Promise.resolve(data)
  migrationPromise = Promise.resolve(data)
}

''' + text[end:]
begin = text.index('async function readLegacy() {')
end = text.index('async function clearLegacy()', begin)
text = text[:begin] + 'async function readLegacy() { return { host: "", port: "", user: "", pass: "" } }\n\n' + text[end:]
# Remove obsolete plaintext fallback and legacy migration implementation entirely.
begin = text.index('let storePromise = null')
end = text.index('let _uuidCounter = 0', begin)
text = text[:begin] + text[end:]
begin = text.index('async function readLegacy()')
end = text.index('let migrationPromise = null', begin)
text = text[:begin] + text[end:]
begin = text.index('    const legacy = await readLegacy()')
end = text.index('    return seed', begin) + len('    return seed')
text = text[:begin] + '    return { entries: [], selectedId: "" }' + text[end:]
text = text.replace('import { Store } from "@tauri-apps/plugin-store"\n', '')
text = text.replace('const STORAGE_KEY = "xt_playlists"\n', '').replace('const LEGACY_KEYS = ["host", "port", "user", "pass"]\n', '')
text = text.replace('// Tauri builds persist via @tauri-apps/plugin-store; web/SSR via localStorage\n// + cookies. Old "xt_host" / "xt_port" / "xt_user" / "xt_pass" keys are\n// auto-migrated into one entry on first read.', '// PINK Android uses an encrypted Keystore vault; only the selected ID is mirrored.')
p.write_text(text)

# Backups keep viewing preferences but can never export or restore an account.
replace('src/scripts/lib/backup.js', '    creds: {\n      entries,\n      selectedId: credsState.selectedId || "",\n    },', '    creds: { entries: [], selectedId: "" },')
replace('src/scripts/lib/backup.js', '  if (b.creds && typeof b.creds === "object") {', '  if (false) { // Managed PINK accounts are never imported from backups.')
p = DEST / 'src/scripts/lib/creds.js'
text = p.read_text()
begin = text.index('export async function restoreState(state) {')
end = text.index('/** Force a re-fetch', begin)
text = text[:begin] + 'export async function restoreState() { throw new Error("Entre com a sua conta PINK para restaurar o acesso.") }\n\n' + text[end:]
p.write_text(text)

welcome = DEST / 'src/components/WelcomeCard.astro'
welcome.write_text('''<div aria-hidden="true"></div>
''')

# Live TV uses the working native protected HTTP path rather than streaming its
# body through the Android Tauri binary IPC boundary that repeatedly stalls.
replace('src/scripts/lib/xtream-api.js',
        'import { providerFetch } from "@/scripts/lib/provider-fetch.js"',
        'import { providerFetch } from "@/scripts/lib/provider-fetch.js"\nimport { fetchPinkLiveCatalog } from "./pink-catalog.js"')
replace('src/scripts/lib/xtream-api.js',
        '  const startIndex = Math.min(getMirrorPin(entry._id), candidates.length - 1)\n\n  const lastAllFailed',
        '''  if (typeof window !== "undefined" && window.PinkNative &&
      ["get_live_categories", "get_live_streams"].includes(action)) {
    if (Object.keys(params).length) throw new Error("Catálogo PINK inválido.")
    return fetchPinkLiveCatalog(action, entry._id, fetchOpts.signal)
  }
  const startIndex = Math.min(getMirrorPin(entry._id), candidates.length - 1)

  const lastAllFailed''')

# Fixed progress markers let the Android proof distinguish a disposed callback
# from a catalog stall without recording provider data or JavaScript errors.
replace('src/scripts/stream/stream.ts',
        'async function loadChannels() {',
        '''let pinkLivePulseTimer = null
function pinkLivePulse() {
  try { window.PinkNative?.postMessage(JSON.stringify({id:"0", operation:"livePulse", payload:{}})) } catch {}
}
function pinkLivePhase(value) {
  document.documentElement.dataset.pinkLivePhase = value
  // One-way fixed progress survives a later blocked WebView callback. No URL,
  // account, response body or error text crosses this diagnostic boundary.
  try { window.PinkNative?.postMessage(JSON.stringify({id:"0", operation:"livePhase", payload:{phase:value}})) } catch {}
  if (value === "painted" || value === "failed") {
    clearInterval(pinkLivePulseTimer)
    pinkLivePulseTimer = null
  } else if (pinkLivePulseTimer === null) {
    pinkLivePulseTimer = setInterval(pinkLivePulse, 1000)
    window.addEventListener?.("pagehide", () => clearInterval(pinkLivePulseTimer), {once:true})
  }
}
async function loadChannels() {
  pinkLivePhase("account")''')
replace('src/scripts/stream/stream.ts', '  await Promise.allSettled([',
        '  pinkLivePhase("preferences")\n  await Promise.allSettled([')
replace('src/scripts/stream/stream.ts', '        const catMap = await ensureCategoryMap()',
        '        pinkLivePhase("categories")\n        const catMap = await ensureCategoryMap()\n        pinkLivePhase("channels")')
replace('src/scripts/stream/stream.ts',
        '        const r = await xtreamApiFetch("get_live_streams")',
        '        const r = await xtreamApiFetch("get_live_streams")\n        pinkLivePhase("response")')
replace('src/scripts/stream/stream.ts', '        const body = await r.text()',
        '        pinkLivePhase("reading")\n        const body = await r.text()\n        pinkLivePhase("body")')
replace('src/scripts/stream/stream.ts', '        const parsed = JSON.parse(body)',
        '        pinkLivePhase("parsing")\n        const parsed = JSON.parse(body)')
replace('src/scripts/stream/stream.ts', '    paintChannels(data, fromCache, age, false)',
        '    pinkLivePhase("painting")\n    paintChannels(data, fromCache, age, false)\n    pinkLivePhase("painted")')
replace('src/scripts/stream/stream.ts', '    log.error("[xt:livetv] loadChannels threw:", e)',
        '    pinkLivePhase("failed")\n    log.error("[xt:livetv] loadChannels threw:", e)')
replace('src/scripts/stream/stream.ts', '  log.log("[xt:livetv] boot start")',
        '  pinkLivePhase("boot")\n  log.log("[xt:livetv] boot start")')

# A hidden Astro component still executes its scripts. Only the home account
# reconciliation may redirect, after the protected vault has been checked.
replace('src/pages/index.astro',
        '\t\tdocument.documentElement.toggleAttribute("data-first-run", !hasEntries);\n\t\treturn hasEntries;',
        '\t\tdocument.documentElement.toggleAttribute("data-first-run", !hasEntries);\n\t\tif (!hasEntries) location.replace("/login");\n\t\treturn hasEntries;')

# The encrypted account is intentionally absent from localStorage. Teach the
# upstream first-run probe to use the non-sensitive selectedId metadata instead
# of demanding plaintext entry objects, so a validated account opens the menus
# immediately while a fresh install is redirected straight to login.
replace('src/pages/index.astro',
        'hasEntries = !!(\n\t\t\t\t\tparsed &&\n\t\t\t\t\tArray.isArray(parsed.entries) &&\n\t\t\t\t\tparsed.entries.length\n\t\t\t\t);',
        'hasEntries = !!(parsed && typeof parsed.selectedId === "string" && parsed.selectedId);')

replace('src/scripts/lib/app-settings.js', 'return readLS(KEY_USER_AGENT, "")', 'return readLS(KEY_USER_AGENT, "PINK-IPTV/0.1")')
replace('src/components/PlaylistSwitcher.svelte', '<span data-i18n="playlist.add" class="truncate">Add playlist</span>', '<span class="truncate">Conta PINK</span>')

# Use the supplied P ribbon identity, including launcher and loading screen.
shutil.copyfile(ROOT / 'assets/pink-icon.png', DEST / 'public/pink-icon.png')
shutil.copyfile(ROOT / 'assets/pink-wordmark.png', DEST / 'public/pink-wordmark.png')
resources = DEST / 'src-tauri/gen/android/app/src/main/res'
for banner in resources.glob('drawable-*/ic_banner.png'):
    shutil.copyfile(ROOT / 'assets/pink-tv-banner.png', banner)
# Remove obsolete upstream themed-icon artwork; the platform uses the PINK icon.
for mono in resources.glob('mipmap-*/ic_launcher_monochrome.png'):
    mono.unlink()
for icon_xml in resources.glob('mipmap-*/ic_launcher*.xml'):
    text = icon_xml.read_text()
    text = '\n'.join(line for line in text.splitlines() if '<monochrome ' not in line) + '\n'
    icon_xml.write_text(text)
p = DEST / 'src/layouts/Layout.astro'
text = p.read_text()
start = text.index('          <svg\n', text.index('class="xt-app-splash__stars'))
end = text.index('          </svg>', start) + len('          </svg>')
text = text[:start] + '          <img class="xt-app-splash__svg" src="/pink-icon.png" alt="" />' + text[end:]
text = text.replace('type="image/svg+xml" href="/favicon.svg"', 'type="image/png" href="/pink-icon.png"')
p.write_text(text)

# PINK owns startup notices and customer-facing support, not upstream releases.
# Original copyright and LICENSE remain in the corresponding source.
replace('src/layouts/Layout.astro', '  import { initWhatsNew } from "@/scripts/lib/whats-new"\n', '')
replace('src/layouts/Layout.astro', '  initWhatsNew()\n', '')
replace('src/scripts/lib/changelog.ts', 'repoSlug = "infinitel8p/Extreme-InfiniTV"', 'repoSlug = "martaxi-boss/pink-iptv"')
replace('src/scripts/lib/changelog.ts', 'const CACHE_KEY = "xt_changelog_cache"', 'const CACHE_KEY = "pink_changelog_cache_v1"')
settings = DEST / 'src/pages/settings.astro'
text = settings.read_text()
discord_start = text.rindex('          <div data-settings-item class="settings-row mt-1 border-t border-line-soft pt-4">', 0, text.index('data-i18n="settings.help.discordTitle"'))
discord_end = text.index('\n        </div>', discord_start)
text = text[:discord_start] + text[discord_end:]
support_start = text.index('        <div id="settings-support"')
support_end = text.index('\n      </div>\n    </section>', support_start)
text = text[:support_start] + text[support_end:]
for suffix in ['releases/latest', 'releases', 'issues/new/choose', 'discussions/new/choose']:
    text = text.replace('https://github.com/infinitel8p/Extreme-InfiniTV/' + suffix,
                        'https://github.com/martaxi-boss/pink-iptv/' + suffix.replace('/new/choose', '/new'))
settings.write_text(text)

# Upstream notices + exact patched source are distributed alongside the APK.
shutil.copyfile(ROOT / 'NOTICE.md', DEST / 'PINK-NOTICE.md')
from vpn_overlay import apply as apply_vpn
apply_vpn(ROOT, DEST, replace)
from vod_overlay import apply as apply_vod
apply_vod(ROOT, DEST, replace)
from catalog_overlay import apply as apply_catalog
apply_catalog(ROOT, DEST)
print('Applied PINK overlay to pinned complete Extreme application')
