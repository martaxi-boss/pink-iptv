import { afterEach, describe, expect, it, vi } from 'vitest'
import { installPinkBridge } from '../src/scripts/lib/pink-bridge.js'
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
describe('origin-restricted native transport', () => {
  const create = () => {
    const native = { postMessage: vi.fn(), onmessage: null as any }
    const host: any = { PinkNative: native }
    vi.stubGlobal('window', host)
    expect(installPinkBridge()).toBe(true)
    return { native, host }
  }
  it('does not manufacture authority without the secure native object', () => {
    const host: any = {}
    vi.stubGlobal('window', host)
    expect(installPinkBridge()).toBe(false)
    expect(host.PinkConnection).toBeUndefined()
    expect(host.PinkAccountVault).toBeUndefined()
  })
  it('matches each asynchronous response to its request', async () => {
    const { native, host } = create()
    const waiting = host.PinkConnection.ready()
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    expect(request.operation).toBe('ready')
    native.onmessage({ data: JSON.stringify({ id: 'unknown', ok: true, result: true }) })
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: true }) })
    expect(await waiting).toBe(true)
  })
  it('uses the restricted transport for encrypted account operations', async () => {
    const { native, host } = create()
    const saved = host.PinkAccountVault.write('synthetic-account-blob')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    expect(request.operation).toBe('vaultWrite')
    expect(request.payload).toEqual({ value: 'synthetic-account-blob' })
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: true }) })
    expect(await saved).toBe(true)
  })
  it('bridges native process-only validated account cache operations', async () => {
    const { native, host } = create()
    const read = host.PinkAccountVault.readValidated()
    let request = JSON.parse(native.postMessage.mock.calls.at(-1)[0])
    expect(request.operation).toBe('vaultReadValidated')
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: '' }) })
    expect(await read).toBe('')

    const mark = host.PinkAccountVault.markValidated('synthetic-validated-state')
    request = JSON.parse(native.postMessage.mock.calls.at(-1)[0])
    expect(request.operation).toBe('vaultMarkValidated')
    expect(request.payload).toEqual({ value: 'synthetic-validated-state' })
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: true }) })
    expect(await mark).toBe(true)
  })
  it('never surfaces native error details', async () => {
    const { native, host } = create()
    const waiting = expect(host.PinkConnection.ready()).rejects.toThrow('temporariamente indisponível')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: false, error: 'private detail' }) })
    await waiting
  })
  it('exposes only fixed safe catalog phases and does not accept provider error text', async () => {
    const { native, host } = create()
    for (const [code, expected] of [['READ_IDLE','READ_IDLE'], ['private URL or credentials','CATALOG_FAILED']]) {
      const waiting = host.PinkCatalog.openVod('get_series', 'fixture')
      const request = JSON.parse(native.postMessage.mock.calls.at(-1)[0])
      const assertion = expect(waiting).rejects.toMatchObject({message:'Serviço PINK temporariamente indisponível.', ...(expected ? {code:expected} : {})})
      native.onmessage({data:JSON.stringify({id:request.id,ok:false,code,error:'private body'})})
      await assertion
      try { await waiting } catch (error: any) { expect(error.code).toBe(expected) }
    }
  })

  it('offers only the native Android consent request before login', async () => {
    const { native, host } = create()
    const waiting = host.PinkConnection.requestVpnConsent()
    const request = JSON.parse(native.postMessage.mock.calls.at(-1)[0])
    expect(request).toMatchObject({ operation: 'vpnPermissionRetry', payload: {} })
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: true }) })
    expect(await waiting).toBe(true)
  })
  it.each([
    ['VPN_PERMISSION','VPN_PERMISSION'],
    ['CONTROL_HTTPS','CONTROL_HTTPS'],
    ['VPN_ENROLL','VPN_ENROLL'],
    ['VPN_LIMIT','VPN_LIMIT'],
    ['VPN_ACTIVATION','VPN_ACTIVATION'],
    ['DEVICE_SECURITY','DEVICE_SECURITY'],
    ['private host and token','LOGIN_UNAVAILABLE'],
  ])('only forwards whitelisted native login failure %s', async (raw, expected) => {
    const { native, host } = create()
    const waiting = host.PinkConnection.resolve('fixture-user', 'fixture-password')
    const request = JSON.parse(native.postMessage.mock.calls.at(-1)[0])
    const assertion = expect(waiting).rejects.toMatchObject({
      message: 'Serviço PINK temporariamente indisponível.', code: expected,
    })
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: false, code: raw, error: 'private token' }) })
    await assertion
  })

  it('bounds a missing native response and ignores its later arrival', async () => {
    vi.useFakeTimers()
    const { native, host } = create()
    const waiting = expect(host.PinkConnection.ready()).rejects.toThrow('temporariamente indisponível')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    await vi.advanceTimersByTimeAsync(5000)
    await waiting
    expect(() => native.onmessage({ data: JSON.stringify({ id: request.id, ok: true, result: true }) })).not.toThrow()
  })
})
