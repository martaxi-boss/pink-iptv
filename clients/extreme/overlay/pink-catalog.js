import { installPinkBridge } from "./pink-bridge.js"

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
  if (!native?.openVod) throw new Error("Serviço PINK indisponível neste dispositivo.")
  const {token, size} = await native.openVod(action, entryId)
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
          if (received !== size) throw new Error("Catálogo PINK incompleto.")
          await finish(); controller.close(); return
        }
        const text = atob(chunk.data)
        const bytes = Uint8Array.from(text, c => c.charCodeAt(0))
        received += bytes.length
        if (received > size) throw new Error("Catálogo PINK inválido.")
        controller.enqueue(bytes)
      } catch (error) { await finish(); controller.error(error) }
    },
    cancel() { return finish() },
  })
  return new Response(body, {status:200, headers:{"Content-Type":"application/json; charset=utf-8", "Content-Length":String(size)}})
}
