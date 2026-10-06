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
    for (const phase of ['channels', 'response', 'reading', 'body', 'parsing']) {
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
  it('relays only a fixed phase and cannot fail the catalog if diagnostics are unavailable', () => {
    const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
    const marker = live.match(/function pinkLivePhase\(value\) \{([\s\S]*?)\n\}/)?.[1]
    if (!marker) throw new Error('Live marker missing')
    const document = { documentElement: { dataset: {} } }
    const postMessage = vi.fn()
    const run = new Function('document', 'window', 'value', marker)
    run(document, { PinkNative: { postMessage } }, 'reading')
    expect(JSON.parse(postMessage.mock.calls[0][0])).toEqual({ id: '0', operation: 'livePhase', payload: { phase: 'reading' } })
    expect(() => run(document, {}, 'body')).not.toThrow()
    expect(() => run(document, { PinkNative: { postMessage: () => { throw new Error('unavailable') } } }, 'body')).not.toThrow()
  })
  it('marks the generated live fetch and paint boundaries using only fixed values', () => {
    const live = readFileSync('src/scripts/stream/stream.ts', 'utf8')
    const values = [...live.matchAll(/pinkLivePhase\("([^"]+)"\)/g)].map(match => match[1])
    expect(values).toEqual(['account', 'preferences', 'categories', 'channels', 'response', 'reading', 'body', 'parsing', 'painting', 'painted', 'failed', 'boot'])
    expect(live.indexOf('pinkLivePhase("categories")')).toBeLessThan(live.indexOf('const catMap = await ensureCategoryMap()'))
    expect(live.indexOf('pinkLivePhase("painted")')).toBeGreaterThan(live.indexOf('paintChannels(data, fromCache, age, false)'))
  })
})
