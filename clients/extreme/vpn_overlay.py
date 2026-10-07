"""Native WireGuard integration for the pinned generated Extreme Android host."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


def apply(root, dest, replace):
    replace('src-tauri/gen/android/build.gradle.kts',
            'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25',
            'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0')
    shutil.copyfile(root / 'overlay/pink-network.js', dest / 'src/scripts/lib/pink-network.js')
    shutil.copyfile(root / 'overlay/pink-network.test.ts', dest / 'tests/pink-network.test.ts')
    replace('src/scripts/lib/provider-fetch.js',
            'export async function providerFetch(url, init = {}) {',
            '''export async function providerFetch(url, init = {}) {
  const { waitPinkConnection } = await import("./pink-network.js")
  await waitPinkConnection(init.signal)''')
    native = 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/'
    for source in (root / 'overlay/vpn').glob('*.kt'):
        shutil.copyfile(source, dest / native / source.name)
    tests = dest / 'src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme'
    tests.mkdir(parents=True, exist_ok=True)
    for source in (root / 'overlay/vpn-tests').glob('*.kt'):
        shutil.copyfile(source, tests / source.name)
    jvm = dest / 'src-tauri/gen/android/app/src/test/java/com/pinkiptv/extreme'
    jvm.mkdir(parents=True, exist_ok=True)
    for source in (root / 'overlay/vpn-jvm').glob('*.kt'):
        shutil.copyfile(source, jvm / source.name)

    replace('src-tauri/gen/android/app/build.gradle.kts',
            '        minSdk = 26',
            '        minSdk = 26\n        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"')
    replace('src-tauri/gen/android/app/build.gradle.kts',
            '        targetCompatibility = JavaVersion.VERSION_17',
            '        targetCompatibility = JavaVersion.VERSION_17\n        isCoreLibraryDesugaringEnabled = true')
    replace('src-tauri/gen/android/app/build.gradle.kts', 'dependencies {', '''dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")''')
    replace('src-tauri/gen/android/app/build.gradle.kts', '    buildFeatures { buildConfig = true }',
            '    packaging { jniLibs.excludes += setOf("**/libwg.so", "**/libwg-quick.so") }\n    buildFeatures { buildConfig = true }')
    manifest = 'src-tauri/gen/android/app/src/main/AndroidManifest.xml'
    replace(manifest, '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">')
    replace(manifest, '    <!-- Permissions -->', '''    <!-- Permissions -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />''')
    replace(manifest, '    </application>', '''        <service android:name="com.wireguard.android.backend.GoBackend$VpnService"
            android:exported="false" android:permission="android.permission.BIND_VPN_SERVICE"
            tools:replace="android:exported">
            <intent-filter><action android:name="android.net.VpnService" /></intent-filter>
            <meta-data android:name="android.net.VpnService.SUPPORTS_ALWAYS_ON" android:value="false" />
        </service>
        <service android:name=".PinkVpnRuntimeService" android:exported="false"
            android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="PINK application-scoped WireGuard connection lifecycle" />
        </service>
    </application>''')

    replace(native + 'MainActivity.kt', 'class MainActivity : TauriActivity() {', '''class MainActivity : TauriActivity() {
  private val pinkPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    PinkVpnRuntime.get(applicationContext).permissionResult(it.resultCode == android.app.Activity.RESULT_OK)
  }''')
    replace(native + 'MainActivity.kt', '    super.onResume()',
            '    super.onResume()\n    PinkVpnRuntime.get(applicationContext).startup(this, pinkPermission)\n    PinkVpnRuntime.get(applicationContext).resume()')
    replace(native + 'MainActivity.kt',
            '''  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
    delegate.shouldInterceptRequest(view, request)''',
            '''  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val remote = request.url.scheme in setOf("http", "https") && request.url.host != "tauri.localhost"
    if (remote && !PinkVpnRuntime.isReady()) return WebResourceResponse(
      "text/plain", "UTF-8", 503, "Connection unavailable", emptyMap(),
      java.io.ByteArrayInputStream(ByteArray(0)))
    return delegate.shouldInterceptRequest(view, request)
  }''')
    replace(native + 'MainActivity.kt', '    val uri = parseUri(url) ?: return false',
            '    if (true) return false // App-scoped PINK cannot protect another player package.\n    val uri = parseUri(url) ?: return false', count=3)
    replace(native + 'MainActivity.kt', '    val uri = parseUri(url) ?: return "[]"',
            '    if (true) return "[]" // No unprotected external player handoff.\n    val uri = parseUri(url) ?: return "[]"')
    # Remote Live/VOD must stay on the admitted PINK tunnel. A persisted Android
    # content:// download is local data, so it may use the same native Media3
    # Activity without weakening the protected provider/media network path.
    replace(native + 'MainActivity.kt', '''      configure(intent)
      activity.runOnUiThread {''', '''      configure(intent)
      val pinkLocalVod = mode == VideoActivity.MODE_VOD &&
        intent.getStringExtra(VideoActivity.EXTRA_URL)?.let {
          android.net.Uri.parse(it).scheme?.equals("content", ignoreCase = true) == true
        } == true
      if (!pinkLocalVod && !PinkVpnRuntime.isReady()) return false
      activity.runOnUiThread {''')
    replace(native + 'VideoActivity.kt', '    super.onCreate(savedInstanceState)', '''    super.onCreate(savedInstanceState)
    val pinkLocalVod = intent.getStringExtra(EXTRA_MODE) == MODE_VOD &&
      intent.getStringExtra(EXTRA_URL)?.let {
        android.net.Uri.parse(it).scheme?.equals("content", ignoreCase = true) == true
      } == true
    if (!pinkLocalVod && !PinkVpnRuntime.isReady()) { finish(); return }''')
    replace(native + 'VideoActivity.kt', 'put("message", error.message ?: "")',
            'put("message", "Reprodução temporariamente indisponível")', count=2)
    # Opt in at the implementation boundary instead of propagating an unstable
    # API requirement to callers of the application's own Activity/constants.
    replace(native + 'VideoActivity.kt', '@UnstableApi\nclass VideoActivity',
            '@androidx.annotation.OptIn(UnstableApi::class)\nclass VideoActivity')
    # Preserve the existing TV back hierarchy while supporting Android gestures.
    replace(native + 'VideoActivity.kt', '    setupCustomControls()', '''    setupCustomControls()
    onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        when {
          volumeAdjustActive -> setVolumeAdjustActive(false)
          overlayVisible -> hideChannelOverlay()
          controllerVisible -> playerView?.hideController()
          else -> {
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
          }
        }
      }
    })''')
    replace(native + 'VideoActivity.kt', '''        KeyEvent.KEYCODE_BACK -> {
          if (!volumeAdjustActive) return@setOnKeyListener false
          if (pressed) setVolumeAdjustActive(false)
          true
        }
''', '')
    replace(native + 'VideoActivity.kt', '''      if (keyCode == KeyEvent.KEYCODE_BACK) {
        hideChannelOverlay()
        return true
      }
''', '')
    replace(native + 'VideoActivity.kt', '''      if (keyCode == KeyEvent.KEYCODE_BACK) {
        playerView?.hideController()
        return true
      }
''', '')
    # Upstream explicitly ships these English-only native fallbacks. Keep every
    # existing locale value and fallback unchanged, and declare that intention.
    resources = dest / 'src-tauri/gen/android/app/src/main/res'
    locales = [ET.parse(path).getroot() for path in resources.glob('values-*/strings.xml')]
    untranslated = {node.attrib['name'] for node in ET.parse(resources / 'values/strings.xml').getroot()
                    if not any(any(item.attrib.get('name') == node.attrib['name'] for item in locale)
                               for locale in locales)}
    strings = resources / 'values/strings.xml'
    value = strings.read_text()
    for name in sorted(untranslated):
        value = value.replace('<string name="'+name+'">', '<string name="'+name+'" translatable="false">')
    strings.write_text(value)
    replace(native + 'XtreamDreamService.kt', '    super.onAttachedToWindow()',
            '    super.onAttachedToWindow()\n    if (!PinkVpnRuntime.isReady()) { finish(); return }')

    # Disable cross-device cast POST before any credential-bearing receiver request.
    replace('src/scripts/lib/tv-cast.ts', '    return await providerFetch(url, init)',
            '    throw new Error("Reprodução disponível apenas neste dispositivo PINK.")')
    replace('src/scripts/lib/play-on-tv-button.ts', '    button.hidden = !isTauri || !hasSource', '    button.hidden = true')
    replace('src/scripts/lib/player-runtime.ts', '''export const androidExternalAvailable =
  isTauri &&
  isAndroid &&
  typeof window !== "undefined" &&
  !!(window as any).AndroidIntent''', 'export const androidExternalAvailable = false')
    # Mobile receiver IPC can start a separate lifecycle without authenticated account.
    lib = dest / 'src-tauri/src/lib.rs'
    text = lib.read_text()
    start = text.index('    #[cfg(any(target_os = "android", target_os = "ios"))]\n    let builder = builder.invoke_handler')
    end = text.index('    ]);', start)
    region = text[start:end]
    region = '\n'.join(line for line in region.split('\n') if 'receiver::' not in line)
    lib.write_text(text[:start] + region + text[end:])

    # Existing upstream logs sometimes contain stream URLs. Android logs remain generic.
    # Managed provider origins are never customer-facing, including diagnostics.
    replace('src/scripts/lib/log.ts', 'const isDev = Boolean(import.meta.env?.DEV)',
            'import { pinkDiagnosticText } from "./pink-presentation.js"\n\nconst isDev = Boolean(import.meta.env?.DEV)')
    replace('src/scripts/lib/log.ts', 'const text = typeof input === "string" ? input : String(input)',
            'const text = pinkDiagnosticText(typeof input === "string" ? input : String(input))')
    replace('src/scripts/lib/playlist-rows.js', 'const COMPACT_ICON_ACTION_CLASS =',
            'import { pinkAccountSubtitle } from "./pink-presentation.js"\n\nconst COMPACT_ICON_ACTION_CLASS =')
    replace('src/scripts/lib/playlist-rows.js', '''  const subtitle = isCompact
    ? ""
    : entry.type === "xtream"
    ? `${entry.serverUrl} · ${entry.username}`
    : entry.type === "local-m3u"
    ? entry.sourceName || ""
    : entry.url || ""''', '  const subtitle = isCompact ? "" : pinkAccountSubtitle(entry)')
    replace('src/scripts/lib/playlist-rows.js', 'async function exportEntryM3U(entry) {',
            'async function exportEntryM3U(entry) {\n  if (entry?.type === "xtream") return')
    replace('src/scripts/lib/playlist-rows.js', '  btn.addEventListener("click", async (ev) => {',
            '  if (entry?.type === "xtream") { btn.hidden = true; btn.disabled = true; btn.style.display = "none"; return btn }\n  btn.addEventListener("click", async (ev) => {')
    replace('src/scripts/lib/export-m3u.ts', 'export async function buildM3UEntriesForEntry(entry: any): Promise<BuildM3UResult> {',
            'export async function buildM3UEntriesForEntry(entry: any): Promise<BuildM3UResult> {\n  if (entry?.type === "xtream") return { entries: [], skippedCount: 0 }')
    replace('src/scripts/lib/net-log.ts', '    store.entries = validEntries',
            '    store.entries = validEntries.map(entry => ({ ...entry, url: redactUrl(entry.url), ...(entry.error ? { error: redactUrl(entry.error) } : {}) }))')
    for source in (dest / native).glob('*.kt'):
        text = source.read_text()
        if source.name != 'PinkSafeLog.kt':
            source.write_text(text.replace('import android.util.Log', 'import com.pinkiptv.extreme.PinkSafeLog as Log'))
    replace('src-tauri/src/lib.rs', '#[cfg(not(target_os = "ios"))]\n    let builder = builder.plugin(build_log_plugin().build());',
            '#[cfg(not(any(target_os = "android", target_os = "ios")))]\n    let builder = builder.plugin(build_log_plugin().build());')
