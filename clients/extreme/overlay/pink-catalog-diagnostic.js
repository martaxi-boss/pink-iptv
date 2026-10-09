// Fixed codes and bounded counters only. Never format an exception message,
// account identifier, URL, provider response, or arbitrary native field.
const phases = new Set(["CONNECT", "HTTP_STATUS", "READ", "READ_IDLE", "TOTAL_DEADLINE", "MAX_BYTES", "VPN_LOST", "STAGE", "STAGE_FILE", "STAGE_CAPACITY", "STAGE_EXPIRED", "STAGE_LIFECYCLE", "SOURCE_VALIDATION", "VPN_NETWORK", "ACCOUNT_BINDING", "CANCELLED", "BRIDGE_OPEN", "BRIDGE_CHUNK", "BRIDGE_TIMEOUT", "BRIDGE_UNAVAILABLE", "BODY_SIZE", "CATEGORY_PARSE", "WORKER_LOAD", "WORKER_PARSE", "WORKER_MAP", "WORKER_MESSAGE", "CATALOG_FAILED"])
const actions = new Map([["get_vod_categories", "MOVIES:CATEGORIES"], ["get_vod_streams", "MOVIES:CATALOG"], ["get_series_categories", "SERIES:CATEGORIES"], ["get_series", "SERIES:CATALOG"]])
const count = (value, max) => Number.isSafeInteger(value) && value >= 0 && value <= max ? value : undefined

export function catalogFailure(code, source = {}) {
  const error = new Error("Não foi possível carregar o catálogo PINK.")
  error.code = phases.has(code) ? code : "CATALOG_FAILED"
  for (const [key, max] of [["bytes", 512 * 1024 * 1024], ["elapsedMs", 3600000], ["httpStatus", 599]]) {
    const value = count(source[key], max)
    if (value !== undefined) error[key] = value
  }
  return error
}

export function catalogActionFailure(error, action, fallback) {
  const safe = catalogFailure(phases.has(error?.code) ? error.code : fallback, error || {})
  if (actions.has(action)) safe.catalogAction = action
  return safe
}

export function catalogFailureDetail(error) {
  const safe = catalogFailure(error?.code, error || {})
  const parts = ["PINK __PINK_CATALOG_REVISION__", actions.get(error?.catalogAction) || "CATALOG", safe.code]
  if (safe.httpStatus >= 100) parts.push("HTTP " + safe.httpStatus)
  if (safe.bytes !== undefined) parts.push(safe.bytes + " B")
  if (safe.elapsedMs !== undefined) parts.push(Math.round(safe.elapsedMs / 1000) + " s")
  return parts.join(" · ")
}
