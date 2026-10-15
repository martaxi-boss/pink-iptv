// Pure, privacy-safe category scoping decisions for native PINK Android.
// A provider can return two IDs with the same label: never guess the ID.
export function pickPinkFastCategory(categoryMap, selectedName) {
  if (!(categoryMap instanceof Map)) return null
  if (selectedName && (selectedName.startsWith("__") || selectedName.startsWith("genre:"))) return null
  const seen = new Map()
  for (const [id, rawName] of categoryMap) {
    const name = String(rawName || "").trim()
    if (!/^[0-9]{1,12}$/.test(String(id)) || !name) continue
    const current = seen.get(name)
    seen.set(name, current ? null : String(id))
  }
  const choices = [...seen].filter(([, id]) => id).map(([name, id]) => ({name, id}))
  if (selectedName) return choices.find(c => c.name === selectedName) || null
  const allowed = choices.filter(c => !/(^|[^a-z])(adult|porn|xxx|erotic|sex|18\+)([^a-z]|$)/i.test(c.name))
  if (!allowed.length) return null // Never auto-open age-gated categories.
  return allowed.find(c => /(top|recent|new|novidad|lan[çc]amento|2026)/i.test(c.name)) || allowed[0]
}
export function filterPinkScopedRows(rows, categoryName) {
  return (Array.isArray(rows) ? rows : []).filter(row => row && row.category === categoryName)
}
