// PINK authenticates the exact mapped provider through the existing backend.
// The client neither guesses hosts nor upgrades their returned HTTP scheme.
import { fetch as nativeFetch } from "@tauri-apps/plugin-http"
const ORIGIN = "https://pink-iptv.duckdns.org"
const MESSAGES = {
  INVALID_CREDENTIALS: "Utilizador ou palavra-passe incorretos.",
  EXPIRED: "A sua conta expirou.",
  DISABLED: "A sua conta está desativada.",
}
export async function resolvePinkSession(username, password, transport = nativeFetch) {
  const response = await transport(`${ORIGIN}/v1/session/resolve`, {
    method: "POST", redirect: "error", maxRedirections: 0, signal: AbortSignal.timeout(20000),
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw new Error("Não foi possível contactar o serviço PINK. Tente novamente.")
  const data = await response.json()
  if (data.code !== "SUCCESS") throw new Error(MESSAGES[data.code] || "Serviço temporariamente indisponível. Tente novamente.")
  const url = new URL(data.xtream_base_url)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error("O serviço devolveu uma configuração inválida.")
  }
  return { serverUrl: url.origin, username, password }
}
