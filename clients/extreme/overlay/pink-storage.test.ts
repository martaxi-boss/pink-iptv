import { beforeEach, describe, expect, it, vi } from 'vitest'
const resolve = vi.hoisted(() => vi.fn())
vi.mock('../src/scripts/lib/pink-session.js', () => ({ resolvePinkSession: resolve }))
vi.mock('../src/scripts/lib/log.js', () => ({ log: { warn: vi.fn(), error: vi.fn(), log: vi.fn() } }))
vi.mock('../src/scripts/lib/app-settings.js', () => ({ ACCENT_PRESETS: ['fuchsia'] }))
describe('protected PINK account restore', () => {
  const fixture = { entries: [{ _id: 'fixture-id', type: 'xtream', title: 'Fixture', serverUrl: 'http://old.example', username: 'fixture-user', password: 'fixture-password' }], selectedId: 'fixture-id' }
  let vault: { read: ReturnType<typeof vi.fn>, write: ReturnType<typeof vi.fn> }
  let mirror: ReturnType<typeof vi.fn>
  beforeEach(() => {
    vi.resetModules()
    resolve.mockReset()
    vault = { read: vi.fn().mockReturnValue(JSON.stringify(fixture)), write: vi.fn().mockReturnValue(true) }
    mirror = vi.fn()
    vi.stubGlobal('window', { PinkAccountVault: vault })
    vi.stubGlobal('localStorage', { setItem: mirror })
  })
  it('restores credentials only after backend resolution and takes its current origin', async () => {
    resolve.mockResolvedValue({ serverUrl: 'https://current.example', username: 'fixture-user', password: 'fixture-password' }) // pragma: allowlist secret — synthetic test fixture
    const { getState } = await import('../src/scripts/lib/creds.js')
    const state = await getState()
    expect(resolve).toHaveBeenCalledWith('fixture-user', 'fixture-password')
    expect(state.entries[0].serverUrl).toBe('https://current.example')
    expect(vault.write).not.toHaveBeenCalled()
    expect(mirror).not.toHaveBeenCalled()
  })
  it('keeps a newly validated account in process across route-module reloads', async () => {
    vault.read.mockReturnValue("")
    const first = await import('../src/scripts/lib/creds.js')
    await first.addEntry({
      type: 'xtream',
      title: 'Fixture',
      serverUrl: 'https://current.example',
      username: 'fixture-user',
      password: 'fixture-password', // pragma: allowlist secret — synthetic test fixture
      liveContainer: 'ts',
    })
    expect(vault.write).toHaveBeenCalledTimes(1)
    const mirrored = JSON.parse(mirror.mock.calls.at(-1)?.[1] || "{}")
    expect(mirrored.selectedId).toBeTruthy()
    expect(mirrored.entries).toEqual([])
    const readsBeforeRouteReload = vault.read.mock.calls.length

    vi.resetModules()
    resolve.mockReset()
    const second = await import('../src/scripts/lib/creds.js')
    const state = await second.getState()

    expect(state.entries).toHaveLength(1)
    expect(state.entries[0].username).toBe('fixture-user')
    expect(vault.read.mock.calls.length).toBe(readsBeforeRouteReload)
    expect(resolve).not.toHaveBeenCalled()
  })
  it('preserves the encrypted account on temporary backend failure without granting stale access', async () => {
    resolve.mockRejectedValue(new Error('temporary fixture outage'))
    const { getState } = await import('../src/scripts/lib/creds.js')
    expect((await getState()).entries).toEqual([])
    expect(vault.write).not.toHaveBeenCalled()
    expect(mirror).not.toHaveBeenCalled()
  })
  it('fails closed when protected storage is missing', async () => {
    vi.stubGlobal('window', {})
    const { getState } = await import('../src/scripts/lib/creds.js')
    await expect(getState()).rejects.toThrow('Protected account storage unavailable')
  })
})
