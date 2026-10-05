"""Native WireGuard integration for the pinned generated Extreme Android host."""
from pathlib import Path
import shutil


def apply(root, dest, replace):
    replace('src-tauri/gen/android/build.gradle.kts',
            'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25',
            'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0')
    native = 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/'
    for source in (root / 'overlay/vpn').glob('*.kt'):
        shutil.copyfile(source, dest / native / source.name)
    tests = dest / 'src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme'
    tests.mkdir(parents=True, exist_ok=True)
    for source in (root / 'overlay/vpn-tests').glob('*.kt'):
        shutil.copyfile(source, tests / source.name)

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
    replace(native + 'MainActivity.kt', '    hostedWebView = webView', '''    hostedWebView = webView
    PinkVpnRuntime.get(applicationContext).startup(this, pinkPermission)
    webView.addJavascriptInterface(PinkConnection(applicationContext), "PinkConnection")''')
    replace(native + 'MainActivity.kt', '    super.onResume()',
            '    super.onResume()\n    PinkVpnRuntime.get(applicationContext).resume()')
    replace(native + 'MainActivity.kt', '    val uri = parseUri(url) ?: return false',
            '    if (true) return false // App-scoped PINK cannot protect another player package.\n    val uri = parseUri(url) ?: return false', count=3)
    replace(native + 'MainActivity.kt', '    val uri = parseUri(url) ?: return "[]"',
            '    return "[]" // No unprotected external player handoff.')
    replace(native + 'MainActivity.kt', '  private fun tryLaunch(mode: String, configure: (android.content.Intent) -> Unit): Boolean {',
            '  private fun tryLaunch(mode: String, configure: (android.content.Intent) -> Unit): Boolean {\n    if (!PinkVpnRuntime.isReady()) return false')
    replace(native + 'VideoActivity.kt', '    super.onCreate(savedInstanceState)',
            '    super.onCreate(savedInstanceState)\n    if (!PinkVpnRuntime.isReady()) { finish(); return }')
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
    for source in (dest / native).glob('*.kt'):
        text = source.read_text()
        if source.name != 'PinkSafeLog.kt':
            source.write_text(text.replace('import android.util.Log', 'import com.pinkiptv.extreme.PinkSafeLog as Log'))
    replace('src-tauri/src/lib.rs', '#[cfg(not(target_os = "ios"))]\n    let builder = builder.plugin(build_log_plugin().build());',
            '#[cfg(not(any(target_os = "android", target_os = "ios")))]\n    let builder = builder.plugin(build_log_plugin().build());')
