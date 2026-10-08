"""Prefer the existing Android VOD player and expose its real Media3 tracks."""
import shutil


def apply(root, dest, replace):
    native = 'src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/'
    shutil.copyfile(root / 'overlay/PinkVodTracks.kt', dest / native / 'PinkVodTracks.kt')
    resources = dest / 'src-tauri/gen/android/app/src/main/res/values/pink-vod.xml'
    resources.write_text('<resources><string name="pink_vod_audio" translatable="false">Áudio</string>'
                         '<string name="pink_vod_subtitles" translatable="false">Legendas / CC</string></resources>\n')
    replace(native + 'VideoActivity.kt', '    view.player = player',
            '    view.player = player\n    if (mode == MODE_VOD) PinkVodTracks.attach(view, player)')
    # Reuse the existing HTTP factory for network media; add Android content
    # reads only for the local VOD path introduced by the active workstream.
    replace(native + 'VideoActivity.kt',
            'return DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)',
            'return DefaultMediaSourceFactory(this).setDataSourceFactory(\n'
            '      if (mode == MODE_VOD) androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory) else httpFactory)')
    shutil.copyfile(root / 'overlay/pink-vod.test.ts', dest / 'tests/pink-vod.test.ts')
    launcher = 'src/scripts/lib/android-video-launcher.ts'
    replace(launcher, 'function pushTvOverscan(): void {', '''// Provider HTTP(S) and persisted Android content URIs use native Media3.
// Asset/file schemes keep the existing WebView fallback when no compatible native URI exists.
export function preferAndroidNativeVod(url: string): boolean {
  return androidNativePlayerAvailable && /^(?:https?:\\/\\/|content:\\/\\/)/i.test(url)
}

function pushTvOverscan(): void {''')
    sources = {
        'src/scripts/movies/detail.ts': 'detailSrc',
        'src/scripts/series/detail.ts': 'src',
    }
    for path, source in sources.items():
        replace(path, '  androidNativePlayerAvailable,', '  preferAndroidNativeVod,')
        replace(path, '  getAndroidNativePlayerEnabled,\n', '')
        replace(path, '  getLocalDownloadPath,\n  tryAndroidIntentPlayback,',
                '  getLocalDownloadPath,\n  getAndroidLocalUri,')
        replace(path, f'  if (await tryAndroidIntentPlayback({source})) return\n', '')
        replace(path, f'  const localDownloadPath = localSrc ? await getLocalDownloadPath({source}) : null',
                f'  const localDownloadPath = localSrc ? await getLocalDownloadPath({source}) : null\n'
                f'  const nativeLocalSrc = await getAndroidLocalUri({source})\n'
                '  const nativePlaySrc = nativeLocalSrc || playSrc')
        replace(path, '    androidNativePlayerAvailable &&\n    getAndroidNativePlayerEnabled() &&',
                '    preferAndroidNativeVod(nativePlaySrc) &&')
        replace(path, '      url: playSrc,', '      url: nativePlaySrc,')
