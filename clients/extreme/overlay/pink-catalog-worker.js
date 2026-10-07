import { mapXtreamVodRows, mapXtreamSeriesRows } from "./catalog-mappers.js"
import { persistPinkCatalog } from "./pink-catalog-store.js"
import { isTrustedWorkerMessage } from "./worker-origin.ts"

export function parsePinkCatalog(body, kind, categories) {
  const parsed = JSON.parse(body)
  const raw = Array.isArray(parsed) ? parsed : kind === "vod"
    ? parsed?.movies || parsed?.results : parsed?.series || parsed?.results
  if (!Array.isArray(raw)) throw new Error("Invalid catalog")
  if (kind === "vod") return mapXtreamVodRows(raw, categories)
  if (kind === "series") return mapXtreamSeriesRows(raw, categories)
  throw new Error("Invalid catalog kind")
}

if (typeof self !== "undefined" && typeof document === "undefined") {
  let rows = null
  let offset = 0
  let persisted = null
  let fetchedAt = 0
  self.onmessage = async (event) => {
    if (!isTrustedWorkerMessage(event)) return
    const {data} = event
    try {
      if (!data.next) {
        rows = parsePinkCatalog(data.body, data.kind, data.categories); offset = 0
        fetchedAt = Date.now()
        // Same IndexedDB schema/key as the existing cache. Its large structured
        // clone happens in this worker, and a failed write keeps the normal path.
        persisted = data.entryId
          ? persistPinkCatalog(data.entryId, data.kind, {data: rows, fetchedAt, ttl: data.ttl})
          : Promise.resolve(false)
      }
      if (offset >= rows.length) {
        self.postMessage({done: true, persisted: await persisted, fetchedAt}); rows = null; return
      }
      self.postMessage({rows: rows.slice(offset, offset + 512)})
      offset += 512
    } catch { rows = null; self.postMessage({error: true}) }
  }
}
