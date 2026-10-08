import { catalogFailure } from "./pink-catalog-diagnostic.js"
// One catalog owner per document. A new WebView never inherits a pending job.
export function pinkCatalogRuntime() {
  return typeof window !== "undefined" && !!window.PinkNative
}

// Completion drives the next stage. Callers never have to await background work.
// Document-local ownership also prevents a recreated WebView inheriting jobs.
export function createCatalogBackground() {
  const active = new Map()
  return (key, load) => {
    if (active.has(key)) return active.get(key)
    const job = Promise.resolve().then(async () => {
      const errors = {}
      for (const kind of ["live", "vod", "series"]) {
        try { await load(kind) }
        catch (error) { errors[kind] = error?.code || "CATALOG_FAILED" }
      }
      return errors
    }).finally(() => active.delete(key))
    active.set(key, job)
    return job
  }
}

export const backgroundPinkCatalog = createCatalogBackground()

export function createCatalogQueue(selectedKind = () => "") {
  const pending = new Map()
  const queue = []
  let running = false
  async function drain() {
    if (running) return
    running = true
    try {
      while (queue.length) {
        const preferred = queue.findIndex((task) => task.kind === selectedKind())
        const [task] = queue.splice(preferred < 0 ? 0 : preferred, 1)
        try { task.resolve(await task.fetcher()) }
        catch (error) { task.reject(error) }
        finally { pending.delete(task.key) }
      }
    } finally { running = false }
  }
  return (key, kind, fetcher) => {
    if (pending.has(key)) return pending.get(key)
    const promise = new Promise((resolve, reject) => queue.push({key, kind, fetcher, resolve, reject}))
    pending.set(key, promise)
    queueMicrotask(drain)
    return promise
  }
}

const schedule = createCatalogQueue(() => {
  const path = typeof location === "undefined" ? "" : location.pathname
  return /movies/.test(path) ? "vod" : /series/.test(path) ? "series" : /livetv|live/.test(path) ? "live" : ""
})

export function schedulePinkCatalog(key, kind, fetcher) {
  return pinkCatalogRuntime() && ["live", "vod", "series"].includes(kind)
    ? schedule(key, kind, fetcher) : fetcher()
}

// Parsing, normalization and sorting run away from the WebView UI thread.
// Bounded messages avoid structured-cloning the entire result in one task.
const persistedRows = new WeakMap()
export function takePinkCatalogPersistence(rows, entryId, kind) {
  const saved = persistedRows.get(rows)
  if (!saved || saved.entryId !== entryId || saved.kind !== kind) return null
  persistedRows.delete(rows)
  return saved
}

export function processPinkCatalog(body, kind, categories, entryId, ttl) {
  return new Promise((resolve, reject) => {
    let worker
    try { worker = new Worker(new URL("./pink-catalog-worker.js", import.meta.url), {type: "module"}) }
    catch { reject(catalogFailure("WORKER_LOAD")); return }
    const rows = []
    worker.onerror = () => { worker.terminate(); reject(catalogFailure("WORKER_LOAD")) }
    worker.onmessageerror = () => { worker.terminate(); reject(catalogFailure("WORKER_MESSAGE")) }
    worker.onmessage = ({data}) => {
      if (data.error) { worker.terminate(); reject(catalogFailure(data.error)); return }
      if (data.done) {
        if (data.persisted) persistedRows.set(rows, {entryId, kind, fetchedAt: data.fetchedAt, ttl})
        worker.terminate(); resolve(rows); return
      }
      for (const row of data.rows) rows.push(row)
      // Let input/paint run between batches, with only one batch in transit.
      setTimeout(() => worker.postMessage({next: true}), 0)
    }
    try { worker.postMessage({body, kind, categories, entryId, ttl}) }
    catch { worker.terminate(); reject(catalogFailure("WORKER_MESSAGE")) }
  })
}
