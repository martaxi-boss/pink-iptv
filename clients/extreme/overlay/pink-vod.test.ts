// @vitest-environment jsdom
import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../src/scripts/lib/preferences.js', () => ({ setProgress: vi.fn(), markCompleted: vi.fn() }))
vi.mock('../src/scripts/lib/app-settings.js', () => ({ getTvOverscan: () => 0, TV_OVERSCAN_EVENT: 'overscan' }))
afterEach(() => { vi.unstubAllGlobals(); vi.resetModules() })

describe('Android VOD native tracks and genuine launch fallback', () => {
  it.each(['movies', 'series'])('%s uses the native VOD policy without the Live opt-in', (kind) => {
    const source = readFileSync(`src/scripts/${kind}/detail.ts`, 'utf8')
    expect(source).toContain('preferAndroidNativeVod(nativePlaySrc) &&')
    expect(source).toContain('getAndroidLocalUri')
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
