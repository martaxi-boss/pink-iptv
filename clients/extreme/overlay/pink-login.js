import { addEntry, getEntries, removeEntry } from "@/scripts/lib/creds.js"
import { resolvePinkSession } from "@/scripts/lib/pink-session.js"
export const loginMarkup = `
  <section class="mx-auto w-full max-w-md px-6 py-10">
    <img src="/pink-wordmark.png" alt="PINK IPTV" class="mx-auto mb-8 w-64" />
    <h1 class="mb-2 text-2xl font-semibold text-fg">Bem-vindo à PINK IPTV</h1>
    <p class="mb-7 text-fg-3">Use o mesmo utilizador e palavra-passe da sua conta PINK IPTV. O acesso fica guardado neste dispositivo.</p>
    <form data-pink-login autocomplete="off" class="space-y-5">
      <label class="block text-fg">Utilizador<input name="username" required autocomplete="off" autocapitalize="none" spellcheck="false" data-focus-key="pink:username" class="mt-2 min-h-12 w-full rounded-2xl border border-line bg-surface-2 px-4 text-fg outline-none tv-focus-inset" /></label>
      <label class="block text-fg">Palavra-passe<input name="password" type="password" required autocomplete="off" data-focus-key="pink:password" class="mt-2 min-h-12 w-full rounded-2xl border border-line bg-surface-2 px-4 text-fg outline-none tv-focus-inset" /></label>
      <p data-pink-status role="status" aria-live="polite" class="min-h-6 text-fg-3"></p>
      <button type="submit" data-focus-key="pink:submit" class="min-h-12 w-full rounded-2xl bg-accent px-6 py-3 font-semibold text-black tv-focus-inset">Entrar</button>
    </form>
    <section data-pink-recovery hidden class="mt-6 space-y-3">
      <p class="text-fg-3">Pode libertar uma instalação antiga da sua conta. A instalação escolhida perderá o acesso VPN até voltar a ser autorizada.</p>
      <button type="button" data-pink-manage class="min-h-12 w-full rounded-2xl border border-line px-4 py-3 font-semibold text-fg tv-focus-inset">Gerir instalações VPN</button>
      <div data-pink-device-list class="space-y-2"></div>
    </section>
  </section>`
export function mountPinkLogin(root, navigate) {
  root.innerHTML = loginMarkup
  const form = root.querySelector('form')
  const status = root.querySelector('[data-pink-status]')
  const button = form.querySelector('button')
  const recovery = root.querySelector('[data-pink-recovery]')
  const manage = root.querySelector('[data-pink-manage]')
  const devicesView = root.querySelector('[data-pink-device-list]')
  let recovering = false
  let alive = true
  let busy = false
  form.dataset.pinkPhase = "idle"
  const onSubmit = async (event) => {
    event.preventDefault()
    if (busy) return
    busy = true
    button.disabled = true
    form.dataset.pinkPhase = 'authenticating'
    delete form.dataset.pinkFailure
    status.textContent = 'A verificar a sua conta…'
    try {
      const username = form.elements.namedItem('username').value.trim()
      const password = form.elements.namedItem('password').value
      const account = await resolvePinkSession(username, password)
      if (!alive) return
      // Keep a single managed account, rather than accumulating duplicate logins.
      form.dataset.pinkPhase = 'loading_account'
      for (const entry of await getEntries()) await removeEntry(entry._id)
      form.dataset.pinkPhase = 'saving_account'
      await addEntry({ ...account, type: 'xtream', title: username, accent: 'fuchsia', liveContainer: 'ts' })
      if (alive) {
        form.dataset.pinkPhase = 'navigating'
        status.textContent = 'Conta validada. A abrir…'
        await navigate()
      }
    } catch (error) {
      form.dataset.pinkFailure = form.dataset.pinkPhase
      form.dataset.pinkPhase = "failed"
      if (alive) {
        if (recovery) recovery.hidden = error?.code !== 'VPN_LIMIT'
        status.textContent = error instanceof Error && error.message.startsWith('A sua') ? error.message :
          (error instanceof Error && /^(Utilizador|Não foi|Serviço|O serviço|Limite)/.test(error.message) ? error.message : 'Não foi possível entrar. Verifique a ligação e tente novamente.')
      }
    } finally {
      busy = false
      if (alive) button.disabled = false
    }
  }
  // Installation handles are ephemeral opaque references; the bearer remains
  // native-only. No account passwords, public keys or handles are rendered.
  const onManage = async () => {
    if (!alive || recovering || busy) return
    recovering = true
    if (manage) manage.disabled = true
    try {
      const native = typeof window !== 'undefined' && window.PinkConnection
      if (typeof native?.listInstallations !== 'function' || !devicesView) throw new Error()
      const data = JSON.parse(await native.listInstallations())
      if (!Array.isArray(data) || data.length > 10 || !data.every(x =>
        typeof x.installation_id === 'string' && /^[0-9a-f]{64}$/.test(x.installation_id) &&
        typeof x.is_current === 'boolean')) throw new Error()
      if (!alive) return
      devicesView.replaceChildren()
      data.forEach((device, index) => {
        const row = document.createElement('div')
        row.className = 'rounded-2xl border border-line p-3 text-fg'
        const title = document.createElement('p')
        title.textContent = device.is_current ? 'Esta instalação — protegida' : `Instalação antiga ${index + 1}`
        row.append(title)
        if (!device.is_current) {
          const action = document.createElement('button')
          action.type = 'button'
          action.className = 'mt-2 min-h-12 rounded-xl bg-accent px-4 font-semibold text-black tv-focus-inset'
          action.textContent = 'Libertar esta vaga'
          action.addEventListener('click', async () => {
            if (!alive || recovering || busy) return
            recovering = true
            action.disabled = true
            try {
              if (typeof native.releaseInstallation !== 'function') throw new Error()
              const released = await native.releaseInstallation(device.installation_id)
              if (released !== true) throw new Error()
              if (!alive) return
              devicesView.replaceChildren()
              recovery.hidden = true
              status.textContent = 'Vaga libertada. A entrar novamente…'
              await onSubmit({ preventDefault() {} })
            } catch {
              if (alive) status.textContent = 'Não foi possível libertar esta vaga. Volte a tentar.'
            } finally {
              recovering = false
              if (alive) action.disabled = false
            }
          })
          row.append(action)
        }
        devicesView.append(row)
      })
      if (data.length === 0) status.textContent = 'Não existem instalações disponíveis para libertar.'
    } catch {
      if (alive) status.textContent = 'Não foi possível consultar as instalações. Entre novamente.'
    } finally {
      recovering = false
      if (alive && manage) manage.disabled = false
    }
  }
  form.addEventListener('submit', onSubmit)
  manage?.addEventListener?.('click', onManage)
  return () => {
    alive = false
    form.removeEventListener('submit', onSubmit)
    manage?.removeEventListener?.('click', onManage)
    root.replaceChildren()
  }
}
