import { installPinkBridge } from "./pink-bridge.js"
// PINK authenticates the exact mapped provider through the existing backend.
// The client neither guesses hosts nor upgrades their returned HTTP scheme.
const ORIGIN = "https://pink-iptv.duckdns.org"
const MESSAGES = {
  INVALID_CREDENTIALS: "Utilizador ou palavra-passe incorretos. Use os dados da sua conta PINK IPTV.",
  EXPIRED: "A sua conta expirou.",
  DISABLED: "A sua conta está desativada.",
}
// Bounded public status codes: never display native exception text or endpoint/account data.
const NATIVE_ERRORS = {
  VPN_PERMISSION: "Não foi possível autorizar a VPN PINK no Android. Confirme o pedido de VPN e toque novamente em Entrar. (PINK: VPN_PERMISSION)",
  CONTROL_HTTPS: "Não foi possível contactar o serviço de autenticação PINK nesta rede. Verifique Wi-Fi/dados móveis e tente novamente. (PINK: CONTROL_HTTPS)",
  VPN_ENROLL: "Não foi possível registar a ligação VPN PINK. Tente novamente. (PINK: VPN_ENROLL)",
  VPN_ACTIVATION: "Não foi possível estabelecer a ligação VPN PINK neste dispositivo. (PINK: VPN_ACTIVATION)",
  DEVICE_SECURITY: "Não foi possível preparar o armazenamento seguro do Android. (PINK: DEVICE_SECURITY)",
  LOGIN_TIMEOUT: "Não foi possível concluir o login no tempo disponível. (PINK: LOGIN_TIMEOUT)",
  LOGIN_UNAVAILABLE: "Serviço PINK temporariamente indisponível. (PINK: LOGIN_UNAVAILABLE)",
}
export async function resolvePinkSession(username, password, transport = null) {
  installPinkBridge()
  let data
  if (!transport) {
    const native = typeof window !== 'undefined' && window.PinkConnection
    if (typeof native?.resolve !== "function") throw new Error("Serviço PINK indisponível neste dispositivo.")
    try {
      // A new tap on Entrar can re-open official Android consent after a denial.
      // Android still exclusively decides VPN permission; no browser/provider fallback.
      await native.requestVpnConsent?.()
      data = JSON.parse(await native.resolve(username, password))
    } catch (error) {
      if (error?.code && Object.hasOwn(NATIVE_ERRORS, error.code))
        throw new Error(NATIVE_ERRORS[error.code])
      throw error
    }
  } else {
    // Explicit transport injection is for deterministic tests; production has no fallback.
    const response = await transport(`${ORIGIN}/v1/session/resolve`, {
    method: "POST", redirect: "error", maxRedirections: 0, signal: AbortSignal.timeout(20000),
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw new Error("Não foi possível contactar o serviço PINK. Tente novamente.")
    data = await response.json()
  }
  if (data.code !== "SUCCESS") throw new Error(MESSAGES[data.code] || "Serviço temporariamente indisponível. Tente novamente.")
  const url = new URL(data.xtream_base_url)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error("O serviço devolveu uma configuração inválida.")
  }
  return { serverUrl: url.origin, username, password }
}
