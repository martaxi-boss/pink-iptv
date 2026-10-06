// Presentation only: provider origins remain authoritative inside the connection.
export function pinkDiagnosticText(input) {
  return String(input ?? '')
    .replace(/(?:https?|wss?):\/\/[^\s"'<>]+/gi, '[PINK]')
    .replace(/(?:https?|wss?)%3a%2f%2f[^\s"'<>]+/gi, '[PINK]')
    .replace(/("(?:host|hostname|serverUrl|base_url|xtream_base_url|dns_link|portal_url)"\s*:\s*)"[^"]*"/gi, '$1"[PINK]"')
}

export function pinkAccountSubtitle(entry) {
  return entry?.type === 'xtream' ? String(entry.username ?? '') : pinkDiagnosticText(entry?.sourceName ?? '')
}
