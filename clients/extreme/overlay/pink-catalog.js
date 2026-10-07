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
