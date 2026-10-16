/** One-shot native Cast handoff. Contains only an item identifier, never stream URLs
 * or account/VPN credentials. Kept in sessionStorage across Android WebView reloads. */
type NativeRoute = "movie" | "series"
type NativeIntent = { playlistId: string; contentKey: string; savedAt: number }
const fallback = new Map<NativeRoute, NativeIntent>()
const MAX_AGE_MS = 12 * 60 * 60 * 1000
const keyFor = (route: NativeRoute) => "pink:native-cast:" + route

export function rememberNativeCastIntent(route: NativeRoute, playlistId: string, contentKey: string): void {
  if (!playlistId || !contentKey) return
  const intent = { playlistId, contentKey, savedAt: Date.now() }
  fallback.set(route, intent)
  try { sessionStorage.setItem(keyFor(route), JSON.stringify(intent)) } catch {}
}

export function matchesNativeCastIntent(route: NativeRoute, playlistId: string, contentKey: string): boolean {
  let intent = fallback.get(route)
  if (!intent) {
    try {
      const parsed: unknown = JSON.parse(sessionStorage.getItem(keyFor(route)) || "null")
      if (parsed && typeof parsed === "object") {
        const record = parsed as Partial<NativeIntent>
        if (typeof record.playlistId === "string" && typeof record.contentKey === "string" &&
            typeof record.savedAt === "number") intent = record as NativeIntent
      }
    } catch {}
  }
  return !!intent && intent.playlistId === playlistId && intent.contentKey === contentKey &&
    Date.now() >= intent.savedAt && Date.now() - intent.savedAt < MAX_AGE_MS
}

export function clearNativeCastIntent(route: NativeRoute): void {
  fallback.delete(route)
  try { sessionStorage.removeItem(keyFor(route)) } catch {}
}
