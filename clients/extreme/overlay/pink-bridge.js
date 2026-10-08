import { catalogFailure } from "./pink-catalog-diagnostic.js"
// Only the local main document receives the origin-restricted native message object.
let installedFor = null
const safeLoginCodes = new Set(["VPN_PERMISSION", "CONTROL_HTTPS", "VPN_ENROLL", "VPN_ACTIVATION", "DEVICE_SECURITY", "LOGIN_UNAVAILABLE"])
export function installPinkBridge() {
  const native = typeof window !== "undefined" && window.PinkNative
  if (typeof native?.postMessage !== "function") return false
  if (installedFor === native) return true
  installedFor = native
  let sequence = 0
  const pending = new Map()
  native.onmessage = (event) => {
    let reply
    try { reply = JSON.parse(event.data) } catch { return }
    const waiting = pending.get(reply.id)
    if (!waiting) return
    pending.delete(reply.id)
    clearTimeout(waiting.timer)
    if (reply.ok === true) waiting.resolve(reply.result)
    else {
      const error = new Error("Serviço PINK temporariamente indisponível.")
      if (waiting.operation === "resolve") {
        error.code = safeLoginCodes.has(reply.code) ? reply.code : "LOGIN_UNAVAILABLE"
      }
      if (waiting.operation.startsWith("vodCatalog")) {
        const safe = catalogFailure(reply.code, reply)
        Object.assign(error, {code:safe.code})
        for (const key of ["bytes", "elapsedMs", "httpStatus"]) if (safe[key] !== undefined) error[key] = safe[key]
      }
      waiting.reject(error)
    }
  }
  const call = (operation, payload = {}, timeout = 5000) => new Promise((resolve, reject) => {
    const id = String(++sequence)
    const timer = setTimeout(() => {
      pending.delete(id)
      reject(operation.startsWith("vodCatalog") ? catalogFailure("BRIDGE_TIMEOUT") :
        operation === "resolve" ? Object.assign(new Error("Serviço PINK temporariamente indisponível."), {code: "LOGIN_TIMEOUT"}) :
        new Error("Serviço PINK temporariamente indisponível."))
    }, timeout)
    pending.set(id, { resolve, reject, timer, operation })
    try { native.postMessage(JSON.stringify({ id, operation, payload })) }
    catch {
      clearTimeout(timer)
      pending.delete(id)
      reject(new Error("Serviço PINK indisponível."))
    }
  })
  window.PinkConnection = {
    ready: () => call("ready"),
    requestVpnConsent: () => call("vpnPermissionRetry"),
    resolve: (username, password) => call("resolve", { username, password }, 150000),
  }
  window.PinkAccountVault = {
    read: () => call("vaultRead"),
    readValidated: () => call("vaultReadValidated"),
    markValidated: (value) => call("vaultMarkValidated", { value }),
    write: (value) => call("vaultWrite", { value }),
  }
  window.PinkCatalog = {
    read: (action, entryId) => call("liveCatalog", { action, entryId }, 60000),
    // 15s connect + 20s response idle + 180s body + 5s bridge margin.
    openVod: (action, entryId) => call("vodCatalog", {action, entryId}, 220000),
    readVodChunk: (token, entryId) => call("vodCatalogChunk", {token, entryId}, 15000),
    closeVod: (token) => call("vodCatalogClose", {token}, 15000),
  }
  return true
}
