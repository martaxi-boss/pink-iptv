export function choosePinkLiveContainer(allowedOutputFormats: unknown): "m3u8" | "ts" {
  const formats = Array.isArray(allowedOutputFormats)
    ? allowedOutputFormats.map((value) => String(value).trim().toLowerCase())
    : []
  if (formats.includes("m3u8")) return "m3u8"
  if (formats.includes("ts")) return "ts"
  return "m3u8"
}

export function pinkNativeOwnsConnectivity(): boolean {
  if (typeof window === "undefined") return false
  const native = (window as Window & { PinkNative?: { postMessage?: unknown } }).PinkNative
  return typeof native?.postMessage === "function"
}
