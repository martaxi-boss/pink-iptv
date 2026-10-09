// Real browser Worker + IndexedDB regression; synthetic catalogs contain no account data.
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'
import { readFileSync } from 'node:fs'
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
    // Exercise the production routing, bounded bridge stream, page fetchers,
    // worker and cache together. Only the network endpoint is synthetic.
    const bodies = new Map([
      ['get_vod_categories', JSON.stringify([{category_id:'7',category_name:'Português'}])],
      ['get_series_categories', JSON.stringify([{category_id:'7',category_name:'Português'}])],
      ['get_vod_streams', body],
      ['get_series', JSON.stringify(rows.slice(0,5000))],
    ])
    const stages = new Map(), opens = {}
    window.PinkNative = {onmessage:null, postMessage(text) {
      const request = JSON.parse(text)
      setTimeout(() => {
        let result = true
        if (request.operation === 'vaultReadValidated') {
          result = JSON.stringify({selectedId:'fixture',entries:[{_id:'fixture',type:'xtream',serverUrl:'https://fixture.invalid',username:'synthetic',password:'synthetic'}]})
        } else if (request.operation === 'liveCatalog') {
          const action = request.payload.action
          if (!['get_vod_categories', 'get_series_categories'].includes(action)) throw Error('Unapproved category operation')
          opens[action] = (opens[action] || 0) + 1
          result = bodies.get(action)
        } else if (request.operation === 'vodCatalog') {
          const action = request.payload.action
          opens[action] = (opens[action] || 0) + 1
          const bytes = new TextEncoder().encode(bodies.get(action))
          stages.set(action,{bytes,offset:0})
          result = {token:action,size:bytes.length}
        } else if (request.operation === 'vodCatalogChunk') {
          const stage = stages.get(request.payload.token)
          if (stage.offset >= stage.bytes.length) result = {done:true}
          else {
            const chunk = stage.bytes.slice(stage.offset,stage.offset+65536)
            stage.offset += chunk.length
            result = {data:btoa(String.fromCharCode(...chunk))}
          }
        } else if (request.operation === 'vodCatalogClose') {
          stages.delete(request.payload.token)
        } else throw Error('Unexpected transport operation')
        window.PinkNative.onmessage({data:JSON.stringify({id:request.id,ok:true,result})})
      },0)
    }}

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
      const load = () => catalog.fetchPinkVodRows('fixture')
      const results = await Promise.all([
        cache.cachedFetch('fixture','vod',86400000,load),
        cache.cachedFetch('fixture','vod',86400000,load,{force:true}),
      ])
      return results[0].data
    })
    const seriesLoad = () => catalog.fetchPinkSeriesRows('fixture')
    const [seriesResult] = await Promise.all([cache.cachedFetch('fixture','series',86400000,seriesLoad),cache.cachedFetch('fixture','series',86400000,seriesLoad,{force:true})])
    const series = seriesResult.data
    const failures = []
    if (count !== 1) failures.push('duplicate live catalog')
    if ([...bodies.keys()].some(action=>opens[action]!==1)) failures.push('duplicate or missing Movies/Series request')
    if (stages.size) failures.push('native stage not released')
    if (worker.rows !== rows.length || series.length !== 5000) failures.push('lost rows')
    if (cache.getCached('fixture', 'series').data[0].genre !== 'Drama') failures.push('lost genre')
    // Read the actual persisted value, independent of the in-memory cache.
    const db = await new Promise((resolve, reject) => {const req = indexedDB.open('xt_cache', 4); req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error)})
    const saved = await new Promise((resolve, reject) => {const req = db.transaction('entries').objectStore('entries').get('xt_cache:fixture:vod'); req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error)})
    if (saved.data.length !== rows.length) failures.push('incomplete persisted cache')
    db.close()
    // Exercise the production raw fetcher/retry policy, not just bridge mocks.
    const originalNative = window.PinkNative
    let rejectedRequests = 0
    window.PinkNative = {onmessage:null, postMessage(text) {
      const request = JSON.parse(text)
      if (request.operation !== 'vodCatalog') return originalNative.postMessage(text)
      rejectedRequests++
      queueMicrotask(() => window.PinkNative.onmessage({data:JSON.stringify({id:request.id,ok:false,code:'HTTP_STATUS',error:'must remain private'})}))
    }}
    try { await catalog.fetchPinkVodRows('fixture'); failures.push('failure swallowed') }
    catch (error) { if (error.code !== 'HTTP_STATUS') failures.push('failure phase lost') }
    if (rejectedRequests !== 1) failures.push('deterministic failure retried')
    // Direct native Live-style category failures must stop before any bulk
    // VOD/series staging. Preserve their safe action and do not retry.
    for (const [categoryAction, fetchRows] of [
      ['get_vod_categories', catalog.fetchPinkVodRows],
      ['get_series_categories', catalog.fetchPinkSeriesRows],
    ]) {
    let categoryOpens = 0
    window.PinkNative = {onmessage:null, postMessage(text) {
      const request = JSON.parse(text)
      let result = true, ok = true
      if (request.operation === 'liveCatalog') {
        categoryOpens++
        if (request.payload.action !== categoryAction) failures.push('wrong category action')
        ok = false
      } else {
        failures.push('bulk catalog transport used after category failure')
        ok = false
      }
      queueMicrotask(() => window.PinkNative.onmessage({data:JSON.stringify({id:request.id,ok,result,code:'STAGE'})}))
    }}
    try { await fetchRows('fixture'); failures.push('category failure swallowed') }
    catch (error) {
      if (error.code !== 'BRIDGE_OPEN' || error.catalogAction !== categoryAction) failures.push('category failure evidence lost')
    }
    if (categoryOpens !== 1) failures.push('category failure retried')
    }
    window.PinkNative = originalNative
    // The preceding deliberate full-catalog failure makes one extra direct
    // category read. Warmup must not add any *further* provider requests.
    if (opens.get_vod_categories !== 2) failures.push('functional direct-category retry coverage lost')
    const beforeBackground = {...opens}
    const start = performance.now()
    await catalog.warmupActive('fixture', {background: true})
    const homeMs = performance.now() - start
    await pipeline.backgroundPinkCatalog('fixture', () => { throw Error('duplicate background') })
    if ([...bodies.keys()].some(action=>opens[action]!==beforeBackground[action])) failures.push('hot background re-downloaded')
    return {homeMs, duplicateRequests: count, nativeCatalogRequests:opens, bodyBytes: body.length, baseline, worker, failures}
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

  // Fullscreen Live TV still uses Video.js by default. Check the generated
  // production button and popup stay inside the fullscreen DOM subtree.
  const stream = readFileSync('src/scripts/stream/stream.ts', 'utf8')
  const fullscreenStart = stream.indexOf('const PINK_FULLSCREEN_MORE_ID = ')
  const fullscreenEnd = stream.indexOf('const CURRENT_MORE_MENU_ID = ', fullscreenStart)
  const menuStart = stream.indexOf('function openCurrentMoreMenu(')
  const menuEnd = stream.indexOf('function showTuningOverlay(', menuStart)
  assert.ok(fullscreenStart > 0 && fullscreenEnd > fullscreenStart && menuStart > 0 && menuEnd > menuStart,
    'live fullscreen owner menu absent from prepared app')
  const ownerOptions = ['Picture-in-Picture', 'Display mode', 'Audio only', 'Mono audio',
    'Playback stats', 'Stream health log', 'Play on TV']
  const bootstrap = [
    'const ICON_DOTS = "⋯"',
    'const t = (value) => value',
    'const all = [{id:"fixture",name:"test"}]',
    'const lastPlayContext = {streamId:"fixture",src:"content://fixture",name:"test"}',
    'const vjs = {}',
    'const CURRENT_MORE_MENU_ID = "current-more-menu"',
    'let currentMoreMenuEl = null, currentMoreMenuTrigger = null',
    'const currentMoreMenuSpatialNav = {open(){},close(){}}',
    'function closeCurrentMoreMenu() { currentMoreMenuEl?.remove(); currentMoreMenuEl=null; currentMoreMenuTrigger=null }',
    'function onCurrentMoreMenuOutside() {}',
    'function onCurrentMoreMenuKey() {}',
    'function closeCurrentMoreMenuOnBlur() {}',
    'function buildCurrentMoreMenuItems(){return ' + JSON.stringify(ownerOptions) +
       '.map(label=>{const button=document.createElement("button");button.textContent=label;button.setAttribute("role","menuitem");return button})}',
  ].join('\n')
  const trailer = [
    'pinkEnsureFullscreenMoreActions();pinkEnsureFullscreenMoreActions();',
    "const buttons=[...document.querySelectorAll('#pink-fullscreen-more-actions')];",
    "if(buttons.length!==1)throw Error('duplicated or missing fullscreen three-dots');",
    'buttons[0].click();',
    "const menu=document.getElementById('current-more-menu');",
    "globalThis.__pinkMenuResult={buttons:buttons.length,inFullscreen:!!menu&&document.querySelector('.video-js').contains(menu),",
    "overlayPosition:menu?.style.position,items:[...menu.querySelectorAll('[role^=\"menuitem\"]')].map(item=>item.textContent)};",
  ].join('\n')
  const extracted = bootstrap + '\n' +
    stream.slice(fullscreenStart, fullscreenEnd) + '\n' +
    stream.slice(menuStart, menuEnd) + '\n' + trailer
  const executable = (await transformWithEsbuild(extracted, 'pink-live-fullscreen.ts', {loader:'ts'})).code
  const menuPage = await browser.newPage()
  try {
    await menuPage.setContent('<!doctype html><div class="video-js vjs-fullscreen" style="position:relative;width:480px;height:360px"><div class="vjs-control-bar"></div></div>')
    const fullscreen = await menuPage.evaluate((code) => { (new Function(code))(); return window.__pinkMenuResult }, executable)
    assert.equal(fullscreen.buttons, 1)
    assert.equal(fullscreen.inFullscreen, true, 'popup hidden outside fullscreen subtree')
    assert.equal(fullscreen.overlayPosition, 'absolute')
    assert.deepEqual(fullscreen.items, ownerOptions)
    console.log('PINK_LIVE_FULLSCREEN_OWNER_MENU=PASS;ITEMS=7;FULLSCREEN_SUBTREE=PASS')
  } finally {
    await menuPage.close()
  }

  console.log('PINK_CATALOG_BROWSER=' + JSON.stringify({...result, restored}))
} finally {
  await browser?.close()
  await server.close()
}
