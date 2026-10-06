type PinkLiveCredentials = {
  host?: string
  user?: string
  pass?: string
}

/**
 * Mega OTT's authoritative M3U currently exposes live sources as
 *   /<username>/<password>/<stream_id>
 * and those URLs return MPEG-TS bytes. The standard Xtream
 *   /live/<username>/<password>/<stream_id>.<ext>
 * route is not equivalent on this provider and has returned HTTP 401.
 *
 * Keep this provider compatibility in one PINK-owned helper so the complete
 * pinned Extreme application can keep using its normal player/cast paths.
 */
export function buildPinkLiveStreamUrl(
  creds: PinkLiveCredentials,
  streamId: string | number
): string {
  const base = String(creds?.host || "").replace(/\/+$/, "")
  const user = encodeURIComponent(String(creds?.user || ""))
  const pass = encodeURIComponent(String(creds?.pass || ""))
  const id = encodeURIComponent(String(streamId))
  if (!base || !user || !pass || !id) return ""
  return `${base}/${user}/${pass}/${id}`
}

export function pinkNativeOwnsConnectivity(): boolean {
  if (typeof window === "undefined") return false
  const native = (window as Window & { PinkNative?: { postMessage?: unknown } }).PinkNative
  return typeof native?.postMessage === "function"
}
