import { installPinkBridge } from "./pink-bridge.js"
import { catalogActionFailure, catalogFailure } from "./pink-catalog-diagnostic.js"

export async function fetchPinkLiveCatalog(action, entryId, signal) {
  if (!["get_live_categories", "get_live_streams"].includes(action)) throw new Error("Catálogo PINK inválido.")
  signal?.throwIfAborted()
  installPinkBridge()
  if (!window.PinkCatalog) throw new Error("Serviço PINK indisponível neste dispositivo.")
  const text = await window.PinkCatalog.read(action, entryId)
  signal?.throwIfAborted()
  return new Response(text, {status: 200, headers: {"Content-Type": "application/json; charset=utf-8"}})
}

// Each pull transfers one bounded block. Credentials and URLs remain native.
export async function fetchPinkVodCatalog(action, entryId, signal) {
  if (!["get_vod_categories", "get_vod_streams", "get_series_categories", "get_series"].includes(action)) throw new Error("Catálogo PINK inválido.")
  signal?.throwIfAborted()
  installPinkBridge()
  const native = window.PinkCatalog
  // Physical Live TV works on this exact VPN-owned native HTTP path.
  // Categories are small (8 MiB native bound); do not stage them on disk.
  // Full movie/series catalogs still stream to disk in bounded 256 MiB chunks.
  if (action === "get_vod_categories" || action === "get_series_categories") {
    if (!native?.read) throw catalogActionFailure(null, action, "BRIDGE_UNAVAILABLE")
    const text = await native.read(action, entryId).catch(error => {
      throw catalogActionFailure(error, action, "BRIDGE_OPEN")
    })
    signal?.throwIfAborted()
    return new Response(text, {status: 200, headers: {"Content-Type": "application/json; charset=utf-8"}})
  }
  if (!native?.openVod) throw catalogActionFailure(null, action, "BRIDGE_UNAVAILABLE")
  const {token, size} = await native.openVod(action, entryId).catch(error => { throw catalogActionFailure(error, action, "BRIDGE_OPEN") })
  const close = () => native.closeVod(token).catch(() => {})
  try { signal?.throwIfAborted() } catch (error) { await close(); throw error }
  let received = 0, closed = false
  const finish = () => { if (closed) return; closed = true; signal?.removeEventListener("abort", abort); return close() }
  const abort = () => { void finish() }
  signal?.addEventListener("abort", abort, {once:true})
  const body = new ReadableStream({
    async pull(controller) {
      try {
        signal?.throwIfAborted()
        const chunk = await native.readVodChunk(token, entryId)
        signal?.throwIfAborted()
        if (chunk.done) {
          if (received !== size) throw Object.assign(new Error("Catálogo PINK incompleto."), {code:"BODY_SIZE"})
          await finish(); controller.close(); return
        }
        const text = atob(chunk.data)
        const bytes = Uint8Array.from(text, c => c.charCodeAt(0))
        received += bytes.length
        if (received > size) throw catalogFailure("BODY_SIZE")
        controller.enqueue(bytes)
      } catch (error) { await finish(); controller.error(signal?.aborted ? error : catalogActionFailure(error, action, "BRIDGE_CHUNK")) }
    },
    cancel() { return finish() },
  })
  return new Response(body, {status:200, headers:{"Content-Type":"application/json; charset=utf-8", "Content-Length":String(size)}})
}
