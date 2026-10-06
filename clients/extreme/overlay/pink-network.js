import { installPinkBridge } from "./pink-bridge.js"
// Every provider request waits for native authorization and a live protected route.
export async function waitPinkConnection(signal) {
  installPinkBridge()
  const native = typeof window !== "undefined" && window.PinkConnection
  if (typeof native?.ready !== "function") throw new Error("Serviço PINK indisponível.")
  for (let attempt = 0; attempt < 360; attempt++) {
    if (signal?.aborted) throw signal.reason || new Error("Pedido cancelado.")
    if (await native.ready() === true) return
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error("Serviço temporariamente indisponível. Tente novamente.")
}
