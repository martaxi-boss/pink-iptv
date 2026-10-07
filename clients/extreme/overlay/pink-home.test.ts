import { readFileSync } from 'node:fs'
import { describe, expect, it, vi } from 'vitest'

// Exercise the generated home page, including scripts in hidden components.
const home = readFileSync('src/pages/index.astro', 'utf8')
const welcome = readFileSync('src/components/WelcomeCard.astro', 'utf8')
const reconcile = home.match(/async function reconcileFirstRun\(\) \{([\s\S]*?)\n\t\}/)?.[1]
if (!reconcile) throw new Error('Home reconciliation missing')
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor

async function openHome(entries: Promise<unknown[]>) {
  const replace = vi.fn()
  const document = { documentElement: { toggleAttribute: vi.fn() } }
  const location = { replace }
  for (const match of welcome.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) {
    await new AsyncFunction('document', 'location', match[1])(document, location)
  }
  const completion = new AsyncFunction('getEntries', 'document', 'location', reconcile)(
    () => entries, document, location,
  )
  return { replace, completion }
}

describe('PINK home login routing', () => {
  it('stays home after successful login despite the hidden welcome component', async () => {
    const { replace, completion } = await openHome(Promise.resolve([{ _id: 'fixture-id' }]))
    expect(await completion).toBe(true)
    expect(replace).not.toHaveBeenCalled()
  })

  it('opens login automatically when protected account validation returns no entries', async () => {
    const { replace, completion } = await openHome(Promise.resolve([]))
    expect(await completion).toBe(false)
    expect(replace).toHaveBeenCalledExactlyOnceWith('/login')
  })

  it('waits for protected account validation before deciding to redirect', async () => {
    let finish!: (value: unknown[]) => void
    const pending = new Promise<unknown[]>(resolve => { finish = resolve })
    const { replace, completion } = await openHome(pending)
    expect(replace).not.toHaveBeenCalled()
    finish([{ _id: 'fixture-id' }])
    expect(await completion).toBe(true)
    expect(replace).not.toHaveBeenCalled()
  })
})

const harness = readFileSync('src-tauri/gen/android/app/src/androidTest/java/com/pinkiptv/extreme/PinkVpnLiveTest.kt', 'utf8')
const launch = harness.match(/\.evaluateJavascript\("([^"]+)" \+ JSONObject.quote\(path\) \+ "([^"]+)", null\)/)
const checkpoint = harness.match(/waitJs\(activity, "([^"]+)" \+ JSONObject.quote\(path\) \+\s*"([^"]+)", 45\)/)
function destination(window: object, location: object, path: string) {
  if (!checkpoint) throw new Error('New-document navigation barrier missing')
  return new Function('window', 'location', `return ${checkpoint[1]}${JSON.stringify(path)}${checkpoint[2]}`)(window, location)
}
describe('actual Android proof navigation boundary', () => {
  it('cannot accept the old login document while same-route navigation is pending', () => {
    if (!launch) throw new Error('Explicit navigation launch missing')
    const window = {}
    const location = { pathname: '/login', assign: vi.fn() }
    new Function('window', 'location', `${launch[1]}${JSON.stringify('/login')}${launch[2]}`)(window, location)
    expect(location.assign).toHaveBeenCalledExactlyOnceWith('/login')
    expect(destination(window, location, '/login')).toBe(false)
    expect(destination({}, location, '/login')).toBe(true)
  })
  it('requires the intended route in the replacement document', () => {
    expect(destination({}, { pathname: '/login' }, '/livetv')).toBe(false)
    expect(destination({}, { pathname: '/livetv' }, '/livetv')).toBe(true)
  })
})

describe('actual channel click media evidence', () => {
  it('requires progressing playback and both decoded video and audio', () => {
    const playback = harness.match(/decoded = js\(activity, """\(\(\)=>\{([\s\S]*?)\}\)\(\)"""\)/)?.[1]
    if (!playback) throw new Error('Actual UI media observation missing')
    const observe = (video: object | null) => new Function('document', playback)({ querySelector: () => video })
    const playing = { paused: false, error: null, currentTime: 2,
      getVideoPlaybackQuality: () => ({ totalVideoFrames: 10 }), webkitAudioDecodedByteCount: 400 }
    expect(observe(playing)).toBe(true)
    expect(observe({ ...playing, webkitAudioDecodedByteCount: 0 })).toBe(false)
    expect(observe({ ...playing, getVideoPlaybackQuality: () => ({ totalVideoFrames: 0 }) })).toBe(false)
    expect(observe({ ...playing, getVideoPlaybackQuality: () => ({ totalVideoFrames: 10, droppedVideoFrames: 10 }) })).toBe(false)
    expect(observe({ ...playing, paused: true })).toBe(false)
    expect(observe(null)).toBe(false)
  })
})

describe('bounded live progress diagnostics', () => {
  it('executes the actual Android observation without returning account data', () => {
    const observation = harness.match(/val raw = js\(activity, """([\s\S]*?)"""\)/)?.[1]
    if (!observation) throw new Error('Live observation missing')
    const script = observation.replace('$expression', 'false')
    const observe = (phase: string) => JSON.parse(new Function('document', `return ${script}`)({
      readyState: 'complete',
      documentElement: { dataset: { pinkLivePhase: phase } },
    }))
    expect(observe('categories')).toEqual({ matched: false, phase: 'categories' })
    for (const phase of ['channels', 'response', 'reading', 'streaming', 'pulling_first', 'decode_small', 'decode_medium', 'decode_large', 'decoded_first', 'pulling_next', 'body', 'parsing']) {
      expect(observe(phase)).toEqual({ matched: false, phase })
    }
    expect(observe('https://private-sub.example/fixture-user/fixture-pass')).toEqual({ matched: false, phase: 'absent' })
  })
  it('cannot certify a route with no old form while its new document is loading', () => {
    const observation = harness.match(/val raw = js\(activity, """([\s\S]*?)"""\)/)?.[1]
    if (!observation) throw new Error('Loading-aware live observation missing')
    const script = observation.replace('$expression', 'true')
    const observe = (readyState: string) => JSON.parse(new Function('document', `return ${script}`)({
      readyState, documentElement: { dataset: {} },
    }))
    expect(observe('loading').matched).toBe(false)
    expect(observe('interactive').matched).toBe(false)
    expect(observe('complete').matched).toBe(true)
  })
  it('keeps an independent renderer pulse while reading and retires it at completion', () => {
    vi.useFakeTimers()
    try {
      const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
      const probe = live.slice(live.indexOf('let pinkLivePulseTimer'), live.indexOf('async function pinkLiveBody'))
      const document = { documentElement: { dataset: {} } }
      const postMessage = vi.fn()
      const phase = new Function('document', 'window', probe+';return pinkLivePhase')(document, { PinkNative: { postMessage } })
      phase('reading')
      expect(JSON.parse(postMessage.mock.calls[0][0])).toEqual({ id: '0', operation: 'livePhase', payload: { phase: 'reading' } })
      vi.advanceTimersByTime(2000)
      expect(postMessage.mock.calls.slice(1).map(([message]) => JSON.parse(message))).toEqual([
        { id: '0', operation: 'livePulse', payload: {} }, { id: '0', operation: 'livePulse', payload: {} },
      ])
      phase('painted')
      postMessage.mockClear()
      vi.advanceTimersByTime(2000)
      expect(postMessage).not.toHaveBeenCalled()
      phase('reading'); phase('failed'); postMessage.mockClear()
      vi.advanceTimersByTime(2000)
      expect(postMessage).not.toHaveBeenCalled()
      const absent = new Function('document', 'window', probe+';return pinkLivePhase')(document, {})
      expect(() => { absent('reading'); absent('failed') }).not.toThrow()
    } finally { vi.useRealTimers() }
  })
  it('observes body bytes while preserving split UTF-8 and releasing the reader on failure', async () => {
    const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
    const drain = live.slice(live.indexOf('async function pinkLiveBody'), live.indexOf('async function loadChannels'))
    const phase = vi.fn()
    const read = new AsyncFunction('pinkLivePhase', drain+';return pinkLiveBody')(phase)
    const bytes = new TextEncoder().encode('[{"name":"televisão"}]')
    const releaseLock = vi.fn()
    const values = [bytes.slice(0,19), bytes.slice(19)]
    const reader = { read: vi.fn(async () => values.length ? {done:false,value:values.shift()} : {done:true}), releaseLock }
    expect(await (await read)({body:{getReader:()=>reader}})).toBe('[{"name":"televisão"}]')
    expect(phase.mock.calls.map(([value]) => value)).toEqual(['pulling_first', 'streaming', 'decode_small', 'decoded_first', 'pulling_next'])
    expect(releaseLock).toHaveBeenCalledOnce()
    const failedReader = { read: vi.fn().mockRejectedValue(new Error('synthetic body failure')), releaseLock:vi.fn() }
    await expect((await read)({body:{getReader:()=>failedReader}})).rejects.toThrow('synthetic body failure')
    expect(failedReader.releaseLock).toHaveBeenCalledOnce()
  })
  it('distinguishes a completed first decode from a still-pending next native pull', async () => {
    const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
    const drain = live.slice(live.indexOf('async function pinkLiveBody'), live.indexOf('async function loadChannels'))
    const phase = vi.fn()
    const read = await new AsyncFunction('pinkLivePhase', drain+';return pinkLiveBody')(phase)
    let end: (value: {done: boolean}) => void = () => { throw new Error('pending native pull missing') }
    const pending = new Promise(resolve => { end = resolve })
    const reader = { read: vi.fn()
      .mockResolvedValueOnce({done:false,value:new Uint8Array([65])})
      .mockReturnValueOnce(pending), releaseLock:vi.fn() }
    const completion = read({body:{getReader:()=>reader}})
    await vi.waitFor(() => expect(phase).toHaveBeenLastCalledWith('pulling_next'))
    expect(phase.mock.calls.map(([value]) => value)).toEqual(['pulling_first','streaming','decode_small','decoded_first','pulling_next'])
    expect(reader.releaseLock).not.toHaveBeenCalled()
    end({done:true})
    expect(await completion).toBe('A')
    expect(reader.releaseLock).toHaveBeenCalledOnce()
  })
  it.each([[65536,'decode_small'],[65537,'decode_medium'],[1048577,'decode_large']])(
    'reports only a fixed first-chunk size bucket for %i bytes', async (size, bucket) => {
      const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
      const drain = live.slice(live.indexOf('async function pinkLiveBody'), live.indexOf('async function loadChannels'))
      const phase = vi.fn()
      const read = await new AsyncFunction('pinkLivePhase', drain+';return pinkLiveBody')(phase)
      const reader = {read:vi.fn().mockResolvedValueOnce({done:false,value:new Uint8Array(Number(size))}).mockResolvedValue({done:true}),releaseLock:vi.fn()}
      await read({body:{getReader:()=>reader}})
      expect(phase.mock.calls.map(([value]) => value)).toEqual(['pulling_first','streaming',bucket,'decoded_first','pulling_next'])
      expect(reader.releaseLock).toHaveBeenCalledOnce()
    })
  it('marks the generated live fetch and paint boundaries using only fixed values', () => {
    const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
    const values = [...live.matchAll(/pinkLivePhase\("([^"]+)"\)/g)].map(match => match[1])
    expect(values).toEqual(['pulling_first', 'pulling_next', 'streaming', 'decoded_first', 'account', 'preferences', 'categories', 'channels', 'response', 'reading', 'body', 'parsing', 'painting', 'painted', 'failed', 'boot'])
    expect(live.indexOf('pinkLivePhase("categories")')).toBeLessThan(live.indexOf('const catMap = await ensureCategoryMap()'))
    expect(live.indexOf('pinkLivePhase("painted")')).toBeGreaterThan(live.indexOf('paintChannels(data, fromCache, age, false)'))
  })
})
