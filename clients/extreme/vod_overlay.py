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
    shutil.copyfile(root / 'overlay/pink-vod.test.ts', dest / 'tests/pink-vod.test.ts')
    launcher = 'src/scripts/lib/android-video-launcher.ts'
    replace(launcher, 'function pushTvOverscan(): void {', '''// Native HTTP VOD is the Android default; downloaded asset URLs keep the WebView path.
export function preferAndroidNativeVod(url: string): boolean {
  return androidNativePlayerAvailable && /^https?:\\/\\//i.test(url)
}

function pushTvOverscan(): void {''')
    for path in ['src/scripts/movies/detail.ts', 'src/scripts/series/detail.ts']:
        replace(path, '  androidNativePlayerAvailable,', '  preferAndroidNativeVod,')
        replace(path, '  getAndroidNativePlayerEnabled,\n', '')
        replace(path, '    androidNativePlayerAvailable &&\n    getAndroidNativePlayerEnabled() &&',
                '    preferAndroidNativeVod(playSrc) &&')
