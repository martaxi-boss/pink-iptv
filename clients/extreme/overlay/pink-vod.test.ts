// @vitest-environment jsdom
import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../src/scripts/lib/preferences.js', () => ({ setProgress: vi.fn(), markCompleted: vi.fn() }))
vi.mock('../src/scripts/lib/app-settings.js', () => ({ getTvOverscan: () => 0, TV_OVERSCAN_EVENT: 'overscan' }))
afterEach(() => { vi.unstubAllGlobals(); vi.resetModules() })

const android = 'src-tauri/gen/android/app/src'
const nativeTracks = readFileSync(`${android}/main/java/com/pinkiptv/extreme/PinkVodTracks.kt`, 'utf8')
const videoActivity = readFileSync(`${android}/main/java/com/pinkiptv/extreme/VideoActivity.kt`, 'utf8')
const vodChecks = readFileSync(`${android}/androidTest/java/com/pinkiptv/extreme/PinkVodTracksChecks.kt`, 'utf8')
const liveChecks = readFileSync(`${android}/androidTest/java/com/pinkiptv/extreme/PinkVpnLiveTest.kt`, 'utf8')

describe('Android VOD native tracks and genuine launch fallback', () => {
  it('keeps one visible, focusable Media3 options entry for Live, movies and episodes', () => {
    expect(videoActivity).toContain('PinkVodTracks.attach(view, player)')
    expect(videoActivity).not.toContain('if (mode == MODE_VOD) PinkVodTracks.attach')
    expect(nativeTracks).toContain('view.setShowSubtitleButton(true)')
    expect(nativeTracks).toContain('options.visibility = View.VISIBLE')
    expect(nativeTracks).toContain('options.isFocusable = available')
    expect(nativeTracks).toContain('R.string.pink_player_options')
    expect(nativeTracks).toContain('.filter { hasTracks(player, it) }')
    expect(nativeTracks).toContain('TrackSelectionDialogBuilder')
  })
  it('drives instrumentation through the visible options control for all content keys', () => {
    expect(vodChecks).toContain('withContentDescription(R.string.pink_player_options)')
    expect(vodChecks).not.toMatch(/withId\([^\n]*exo_(?:audio_track|subtitle)/)
    expect(vodChecks).toContain('"vod:fixture"')
    expect(vodChecks).toContain('"ep:fixture"')
    expect(vodChecks).toContain('video continues after selection')
    expect(liveChecks).toContain('selectAvailableLiveTrack')
    expect(liveChecks).toContain('withContentDescription(R.string.pink_player_options)')
    expect(liveChecks).toContain('LIVE_VISIBLE_TRACK_OPTIONS_SELECTION_AND_CONTINUED_PLAYBACK=PASS')
  })
  it.each(['movies', 'series'])('%s uses the native VOD policy without the Live opt-in', (kind) => {
    const source = readFileSync(`src/scripts/${kind}/detail.ts`, 'utf8')
    expect(source).toContain('preferAndroidNativeVod(nativePlaySrc) &&')
    expect(source).toContain('getAndroidLocalUri')
    expect(source).toContain('const nativeLocalSrc = await getAndroidLocalUri')
    expect(source).toContain('const nativePlaySrc = nativeLocalSrc || playSrc')
    expect(source).toContain('url: nativePlaySrc')
    expect(source).not.toContain('tryAndroidIntentPlayback')
    expect(source).not.toContain('getAndroidNativePlayerEnabled')
    expect(source).toContain('if (launched) return')
    expect(source).toContain('await mountVodPlayback({')
  })
  it('prefers native HTTP VOD with the existing Android bridge', async () => {
    vi.stubGlobal('navigator', { userAgent: 'Android' })
    const launchVod = vi.fn(() => true)
    window.AndroidVideo = { launchVod } as any
    const mod = await import('../src/scripts/lib/android-video-launcher')
    expect(mod.preferAndroidNativeVod('https://provider.example/movie.mkv')).toBe(true)
    expect(mod.preferAndroidNativeVod('content://media/external/video/media/42')).toBe(true)
    expect(mod.preferAndroidNativeVod('asset://localhost/movie.mkv')).toBe(false)
    expect(mod.preferAndroidNativeVod('file:///movie.mp4')).toBe(false)
    expect(mod.launchAndroidNativeVod({ contentKey: 'vod:1', url: 'https://provider.example/movie.mkv' })).toBe(true)
  })
  it('keeps WebView eligibility if bridge rejects or throws', async () => {
    vi.stubGlobal('navigator', { userAgent: 'Android' })
    const launchVod = vi.fn(() => false)
    window.AndroidVideo = { launchVod } as any
    const mod = await import('../src/scripts/lib/android-video-launcher')
    expect(mod.launchAndroidNativeVod({ contentKey: 'ep:2', url: 'https://provider.example/episode.mp4' })).toBe(false)
    launchVod.mockImplementation(() => { throw new Error('synthetic launch failure') })
    expect(mod.launchAndroidNativeVod({ contentKey: 'ep:2', url: 'https://provider.example/episode.mp4' })).toBe(false)
  })
  it('does not select Android playback on desktop or without its bridge', async () => {
    vi.stubGlobal('navigator', { userAgent: 'Desktop' })
    window.AndroidVideo = undefined
    const mod = await import('../src/scripts/lib/android-video-launcher')
    expect(mod.preferAndroidNativeVod('https://provider.example/movie.mp4')).toBe(false)
  })
})
