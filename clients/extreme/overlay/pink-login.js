import { addEntry, getEntries, removeEntry } from "@/scripts/lib/creds.js"
import { resolvePinkSession } from "@/scripts/lib/pink-session.js"
export const loginMarkup = `
  <section class="mx-auto w-full max-w-md px-6 py-10">
    <img src="/pink-wordmark.png" alt="PINK IPTV" class="mx-auto mb-8 w-64" />
    <h1 class="mb-2 text-2xl font-semibold text-fg">Bem-vindo à PINK IPTV</h1>
    <p class="mb-7 text-fg-3">Entre com a sua conta. O acesso fica guardado neste dispositivo.</p>
    <form data-pink-login class="space-y-5">
      <label class="block text-fg">Utilizador<input name="username" required autocomplete="username" autocapitalize="none" spellcheck="false" data-focus-key="pink:username" class="mt-2 min-h-12 w-full rounded-2xl border border-line bg-surface-2 px-4 text-fg outline-none tv-focus-inset" /></label>
      <label class="block text-fg">Palavra-passe<input name="password" type="password" required autocomplete="current-password" data-focus-key="pink:password" class="mt-2 min-h-12 w-full rounded-2xl border border-line bg-surface-2 px-4 text-fg outline-none tv-focus-inset" /></label>
      <p data-pink-status role="status" aria-live="polite" class="min-h-6 text-fg-3"></p>
      <button type="submit" data-focus-key="pink:submit" class="min-h-12 w-full rounded-2xl bg-accent px-6 py-3 font-semibold text-black tv-focus-inset">Entrar</button>
    </form>
  </section>`
export function mountPinkLogin(root, navigate) {
  root.innerHTML = loginMarkup
  const form = root.querySelector('form')
  const status = root.querySelector('[data-pink-status]')
  const button = form.querySelector('button')
  let alive = true
  let busy = false
  const onSubmit = async (event) => {
    event.preventDefault()
    if (busy) return
    busy = true
    button.disabled = true
    status.textContent = 'A verificar a sua conta…'
    try {
      const username = form.elements.namedItem('username').value.trim()
      const password = form.elements.namedItem('password').value
      const account = await resolvePinkSession(username, password)
      if (!alive) return
      // Keep a single managed account, rather than accumulating duplicate logins.
      for (const entry of await getEntries()) await removeEntry(entry._id)
      await addEntry({ ...account, type: 'xtream', title: username, accent: 'fuchsia', liveContainer: 'ts' })
      if (alive) await navigate()
    } catch (error) {
      if (alive) status.textContent = error instanceof Error && error.message.startsWith('A sua') ? error.message :
        (error instanceof Error && /^(Utilizador|Não foi|Serviço|O serviço)/.test(error.message) ? error.message : 'Não foi possível entrar. Verifique a ligação e tente novamente.')
    } finally {
      busy = false
      if (alive) button.disabled = false
    }
  }
  form.addEventListener('submit', onSubmit)
  return () => { alive = false; form.removeEventListener('submit', onSubmit); root.replaceChildren() }
}
