import { afterEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { createCatalogQueue, createCatalogBackground } from '../src/scripts/lib/pink-catalog-pipeline.js'
import { parsePinkCatalog } from '../src/scripts/lib/pink-catalog-worker.js'

afterEach(() => vi.unstubAllGlobals())
const source = (path: string) => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
describe('demand-first catalog ownership', () => {
  it('advances Live → Movies → Series only on completion, joins triggers and continues after failure', async () => {
    const background = createCatalogBackground()
    const order: string[] = []
    let release: any
    const load = vi.fn(async (kind: string) => {
      order.push(kind)
      if (kind === 'live') await new Promise(resolve => { release = resolve })
      if (kind === 'vod') throw Object.assign(Error('bounded failure'), {code:'READ_IDLE'})
    })
    const a = background('account', load)
    expect(background('account', load)).toBe(a)
    await Promise.resolve()
    expect(order).toEqual(['live'])
    release()
    expect(await a).toEqual({vod:'READ_IDLE'})
    expect(order).toEqual(['live', 'vod', 'series'])
  })
  it('places an explicit Series request immediately after active Live without duplicating background work', async () => {
    const queue = createCatalogQueue(() => 'series')
    const background = createCatalogBackground()
    const hot = new Set<string>()
    const order: string[] = []
    let release: any
    const load = (kind: string) => hot.has(kind) ? Promise.resolve() : queue(kind, kind, async () => {
      order.push(kind)
      if (kind === 'live') await new Promise(resolve => { release = resolve })
      hot.add(kind)
    })
    const job = background('account', load)
    await Promise.resolve(); await Promise.resolve()
    const page = load('series')
    release()
    await Promise.all([job, page])
    expect(order).toEqual(['live', 'series', 'vod'])
    await background('account', load)
    expect(order).toEqual(['live', 'series', 'vod'])
    expect(createCatalogBackground.toString()).not.toMatch(/setTimeout|setInterval/)
  })
  it('joins a page and warmup request and serializes different catalogs', async () => {
    const queue = createCatalogQueue()
    let release: any
    const fetcher = vi.fn(() => new Promise(resolve => { release = resolve }))
    const a = queue('account:vod', 'vod', fetcher)
    const b = queue('account:vod', 'vod', vi.fn(() => { throw Error('duplicate') }))
    const series = vi.fn(async () => ['series'])
    const c = queue('account:series', 'series', series)
    await Promise.resolve()
    expect(a).toBe(b)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(series).not.toHaveBeenCalled()
    release(['movie'])
    expect(await a).toEqual(['movie'])
    expect(await c).toEqual(['series'])
  })
  it('prioritizes the opened view over queued siblings and continues after failure', async () => {
    let selected = 'series'
    const queue = createCatalogQueue(() => selected)
    const order: string[] = []
    const load = (kind: string) => queue(kind, kind, async () => { order.push(kind); return kind })
    await Promise.all([load('live'), load('vod'), load('series')])
    expect(order).toEqual(['series', 'live', 'vod'])
    await expect(queue('vod', 'vod', async () => { throw Error('failure') })).rejects.toThrow('failure')
    expect(await load('vod')).toBe('vod')
  })
  it('new documents have no inherited pending state', async () => {
    const old = createCatalogQueue()
    void old('vod', 'vod', () => new Promise(() => {}))
    const reopened = createCatalogQueue()
    expect(await reopened('vod', 'vod', async () => ['restored'])).toEqual(['restored'])
  })
  it('startup does not start native staging or wait for all catalog caches', () => {
    const catalog = source('scripts/lib/catalog.js')
    const background = catalog.slice(catalog.indexOf('export async function warmupActive'), catalog.indexOf('  let creds', catalog.indexOf('export async function warmupActive')))
    expect(background).toContain('opts.background')
    expect(background).toContain('void backgroundPinkCatalog')
    expect(background).not.toContain('await backgroundPinkCatalog')
    expect(catalog.match(/if \(isTauri && !pinkCatalogRuntime\(\)\)/g)).toHaveLength(5)
    expect(source('components/Sidebar.astro')).toContain('background: true')
  })
  it('pages reuse the same uncached fetchers and retain persistent cache-first painting', () => {
    for (const [path, kind, helper] of [['movies/movies.ts', 'vod', 'fetchPinkVodRows'], ['series/series.ts', 'series', 'fetchPinkSeriesRows']]) {
      const page = source('scripts/' + path)
      expect(page).toContain(helper + '(activePlaylistId)')
      expect(page).not.toContain('JSON.parse(body)')
      expect(page).toContain('await hydrateCache(active._id, "' + kind + '")')
      expect(page).toContain('if (hit) return')
    }
  })
})

describe('complete catalog worker mapping', () => {
  it('preserves every row, Unicode, metadata and category in a large VOD/series fixture', () => {
    const raw = Array.from({length: 25000}, (_, i) => ({stream_id: i + 1, series_id: i + 1, name: `Filme 🎬 ${i}`, category_id: '7', genre: 'Drama', tmdb: i + 10}))
    for (const kind of ['vod', 'series']) {
      const mapped = parsePinkCatalog(JSON.stringify(raw), kind, new Map([['7', 'Português']]))
      expect(mapped).toHaveLength(raw.length)
      expect(new Set(mapped.map(row => row.id)).size).toBe(raw.length)
      expect(mapped.find(row => row.id === 1)).toMatchObject({name: 'Filme 🎬 0', category: 'Português', tmdb: 10})
      if (kind === 'series') expect(mapped[0].genre).toBe('Drama')
    }
  })
  it('handles empty/wrapped catalogs and rejects malformed responses without caching partial rows', () => {
    expect(parsePinkCatalog('{"movies":[]}', 'vod', new Map())).toEqual([])
    expect(parsePinkCatalog('{"series":[]}', 'series', new Map())).toEqual([])
    expect(() => parsePinkCatalog('{"error":"unauthorized"}', 'vod', new Map())).toThrow()
    expect(() => parsePinkCatalog('[', 'series', new Map())).toThrow()
  })
})
