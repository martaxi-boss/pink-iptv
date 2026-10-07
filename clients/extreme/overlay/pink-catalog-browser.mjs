// Real browser Worker + IndexedDB regression; synthetic catalogs contain no account data.
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { createServer } from 'vite'
import { resolve } from 'node:path'

const server = await createServer({configFile: false, root: process.cwd(), resolve: {alias: {'@': resolve('src')}}, server: {host: '127.0.0.1', port: 0}})
server.middlewares.use((req, res, next) => {
  if (req.url === '/pink-catalog-fixture') { res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><title>Catalog regression</title>'); return }
  next()
})
let browser
try {
  await server.listen()
  const port = server.httpServer.address().port
  browser = await chromium.launch({headless: true})
  const page = await browser.newPage()
  await page.goto(`http://127.0.0.1:${port}/pink-catalog-fixture`)
  const result = await page.evaluate(async () => {
    window.PinkNative = {postMessage() { throw Error('Unexpected native request') }}
    const pipeline = await import('/src/scripts/lib/pink-catalog-pipeline.js')
    const cache = await import('/src/scripts/lib/cache.js')
    const catalog = await import('/src/scripts/lib/catalog.js')
    const {parsePinkCatalog} = await import('/src/scripts/lib/pink-catalog-worker.js')
    const start = performance.now()
    await catalog.warmupActive('fixture', {background: true})
    const homeMs = performance.now() - start
    let count = 0
    let release
    const loader = () => { count++; return new Promise(resolve => { release = resolve }) }
    const first = cache.cachedFetch('fixture', 'live', 86400000, loader)
    const joined = cache.cachedFetch('fixture', 'live', 86400000, loader, {force: true})
    while (!release) await new Promise(resolve => setTimeout(resolve, 5))
    release([{id: 1, name: 'Fixture'}])
    await Promise.all([first, joined])
    const rows = Array.from({length: 100000}, (_, i) => ({stream_id: i + 1, series_id: i + 1, name: `Português 🎬 ${100000-i}`, category_id: '7', genre: 'Drama', tmdb: i + 10, plot: 'Synthetic plot '.repeat(12)}))
    const body = JSON.stringify(rows)
    const categories = new Map([['7', 'Português']])
    const measure = async (fn) => {
      let maxGap = 0, ticks = 0, previous = performance.now()
      const timer = setInterval(() => { const now = performance.now(); maxGap = Math.max(maxGap, now - previous); previous = now; ticks++ }, 5)
      await new Promise(resolve => setTimeout(resolve, 20))
      const begin = performance.now()
      const result = await fn()
      const elapsedMs = performance.now() - begin
      await new Promise(resolve => setTimeout(resolve, 20))
      clearInterval(timer)
      return {rows: result.length, maxGapMs: maxGap, ticks, elapsedMs}
    }
    const baseline = await measure(() => parsePinkCatalog(body, 'vod', categories))
    const worker = await measure(async () => {
      const mapped = await pipeline.processPinkCatalog(body, 'vod', categories, 'fixture', 86400000)
      cache.setCached('fixture', 'vod', mapped, 86400000)
      return mapped
    })
    const series = await pipeline.processPinkCatalog(JSON.stringify(rows.slice(0, 5000)), 'series', categories, 'fixture', 86400000)
    cache.setCached('fixture', 'series', series, 86400000)
    const failures = []
    if (count !== 1) failures.push('duplicate live catalog')
    if (worker.rows !== rows.length || series.length !== 5000) failures.push('lost rows')
    if (cache.getCached('fixture', 'series').data[0].genre !== 'Drama') failures.push('lost genre')
    // Read the actual persisted value, independent of the in-memory cache.
    const db = await new Promise((resolve, reject) => {const req = indexedDB.open('xt_cache', 4); req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error)})
    const saved = await new Promise((resolve, reject) => {const req = db.transaction('entries').objectStore('entries').get('xt_cache:fixture:vod'); req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error)})
    if (saved.data.length !== rows.length) failures.push('incomplete persisted cache')
    db.close()
    return {homeMs, duplicateRequests: count, bodyBytes: body.length, baseline, worker, failures}
  })
  assert.deepEqual(result.failures, [])
  assert.ok(result.homeMs < 100, 'Home should not wait for catalog work')
  assert.ok(result.worker.maxGapMs < result.baseline.maxGapMs, 'Worker should reduce the longest UI stall')
  await page.reload()
  const restored = await page.evaluate(async () => {
    window.PinkNative = {postMessage() { throw Error('Unexpected native request') }}
    const cache = await import('/src/scripts/lib/cache.js')
    const begin = performance.now()
    const result = await cache.cachedFetch('fixture', 'vod', 86400000, () => { throw Error('Cache re-downloaded') })
    return {fromCache: result.fromCache, rows: result.data.length, elapsedMs: performance.now() - begin}
  })
  assert.equal(restored.fromCache, true)
  assert.equal(restored.rows, 100000)
  console.log('PINK_CATALOG_BROWSER=' + JSON.stringify({...result, restored}))
} finally {
  await browser?.close()
  await server.close()
}
