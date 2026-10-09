import { beforeEach, describe, expect, it, vi } from 'vitest'
const deps = vi.hoisted(() => ({ addEntry: vi.fn(), getEntries: vi.fn(), removeEntry: vi.fn(), resolvePinkSession: vi.fn() }))
vi.mock('@/scripts/lib/creds.js', () => deps)
vi.mock('@/scripts/lib/pink-session.js', () => deps)
import { mountPinkLogin, parseRecoverableInstallations } from '../src/scripts/lib/pink-login.js'
function formHarness() {
  const button = { disabled: false }
  const status = { textContent: '' }
  let submit: any
  const form = { dataset: {} as Record<string,string>, querySelector: () => button,
    elements: { namedItem: (name: string) => ({ value: name === 'username' ? 'fixture-user' : 'fixture-secret' }) },
    addEventListener: (_: string, handler: any) => { submit = handler }, removeEventListener: vi.fn() }
  const root = { innerHTML: '', querySelector: (selector: string) => selector === 'form' ? form : status, replaceChildren: vi.fn() }
  const navigate = vi.fn()
  const cleanup = mountPinkLogin(root, navigate)
  return { form, button, status, navigate, cleanup, submit: () => submit({ preventDefault: vi.fn() }) }
}
beforeEach(() => {
  vi.resetAllMocks()
  deps.resolvePinkSession.mockResolvedValue({ serverUrl: 'https://fixture.example' })
  deps.getEntries.mockResolvedValue([])
  deps.addEntry.mockResolvedValue({})
})
describe('VPN quota self-service recovery', () => {
  const device = (index: number) => ({
    installation_id: String(index % 10).repeat(64),
    last_authenticated_at: '2026-10-08T12:00:00Z',
    is_current: index === 0,
  })
  it('accepts only bounded opaque, uniquely identified installations', () => {
    expect(parseRecoverableInstallations(JSON.stringify([device(0),device(1)]))).toHaveLength(2)
    expect(() => parseRecoverableInstallations(JSON.stringify([device(1),device(1)]))).toThrow()
    expect(() => parseRecoverableInstallations(JSON.stringify(Array.from({length:11},(_,i)=>device(i))))).toThrow()
    expect(() => parseRecoverableInstallations(JSON.stringify([
      {...device(1), installation_id:'invalid-private-key'},
    ]))).toThrow()
    expect(() => parseRecoverableInstallations(JSON.stringify([
      {...device(1), last_authenticated_at:'garbage'},
    ]))).toThrow()
  })
  it('only exposes recovery after the fixed VPN_LIMIT error', async () => {
    const h = formHarness()
    deps.resolvePinkSession.mockRejectedValue(Object.assign(
      new Error('Limite de 10 instalações VPN atingido. (PINK: VPN_LIMIT)'), {code:'VPN_LIMIT'}
    ))
    await h.submit()
    expect(h.status.hidden).toBe(false)
    expect(h.status.textContent).toContain('VPN_LIMIT')
  })
})

describe('protected login transaction checkpoints', () => {
  it('waits for account persistence before navigation', async () => {
    let save: any
    deps.addEntry.mockImplementation(() => new Promise(resolve => { save = resolve }))
    const h = formHarness()
    const pending = h.submit()
    await vi.waitFor(() => expect(h.form.dataset.pinkPhase).toBe('saving_account'))
    expect(h.navigate).not.toHaveBeenCalled()
    expect(h.button.disabled).toBe(true)
    save({}); await pending
    expect(h.navigate).toHaveBeenCalledOnce()
    expect(h.form.dataset.pinkPhase).toBe('navigating')
  })
  it.each(['authenticating','loading_account','saving_account','navigating'])('reports only the fixed failed step %s', async phase => {
    const target = { authenticating: deps.resolvePinkSession, loading_account: deps.getEntries, saving_account: deps.addEntry }[phase]
    const h = formHarness()
    ;(target || h.navigate).mockRejectedValue(new Error('private synthetic detail'))
    await h.submit()
    expect(h.form.dataset).toEqual({ pinkPhase: 'failed', pinkFailure: phase })
    expect(h.status.textContent).not.toContain('private')
    expect(h.button.disabled).toBe(false)
  })
  it('ignores duplicate submission and disposed form completion', async () => {
    let resolve: any
    deps.resolvePinkSession.mockImplementation(() => new Promise(r => { resolve = r }))
    const h = formHarness(); const pending = h.submit()
    await h.submit(); h.cleanup(); resolve({}); await pending
    expect(deps.resolvePinkSession).toHaveBeenCalledOnce()
    expect(deps.addEntry).not.toHaveBeenCalled()
    expect(h.navigate).not.toHaveBeenCalled()
  })
})
