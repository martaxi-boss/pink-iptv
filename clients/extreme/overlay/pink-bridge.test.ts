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
  it('never surfaces native error details', async () => {
    const { native, host } = create()
    const waiting = expect(host.PinkConnection.ready()).rejects.toThrow('temporariamente indisponível')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    native.onmessage({ data: JSON.stringify({ id: request.id, ok: false, error: 'private detail' }) })
    await waiting
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
