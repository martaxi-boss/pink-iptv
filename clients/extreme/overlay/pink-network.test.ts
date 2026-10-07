import { afterEach, describe, expect, it, vi } from 'vitest'
import { waitPinkConnection } from '../src/scripts/lib/pink-network.js'
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
describe('protected provider admission', () => {
  it('rejects missing native admission without a browser fallback', async () => {
    vi.stubGlobal('window', {})
    await expect(waitPinkConnection()).rejects.toThrow('Serviço PINK indisponível')
  })
  it('waits for the route rather than admitting requests during restoration', async () => {
    vi.useFakeTimers()
    const ready = vi.fn().mockReturnValueOnce(false).mockReturnValue(true)
    vi.stubGlobal('window', { PinkConnection: { ready } })
    let admitted = false
    const waiting = waitPinkConnection().then(() => { admitted = true })
    expect(admitted).toBe(false)
    await vi.advanceTimersByTimeAsync(250)
    await waiting
    expect(admitted).toBe(true)
    expect(ready).toHaveBeenCalledTimes(2)
  })
  it('fails after a bounded outage', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('window', { PinkConnection: { ready: () => false } })
    const waiting = expect(waitPinkConnection()).rejects.toThrow('temporariamente indisponível')
    await vi.advanceTimersByTimeAsync(90000)
    await waiting
  })
  it('does not ignore cancellation while waiting for a route', async () => {
    vi.stubGlobal('window', { PinkConnection: { ready: () => false } })
    const controller = new AbortController()
    controller.abort(new Error('fixture abort'))
    await expect(waitPinkConnection(controller.signal)).rejects.toThrow('fixture abort')
  })
})

import { observePinkBodyInvoke } from '../src/scripts/lib/pink-body-ipc.js'
import { observeHttpBodySource } from '../src/plugins/pink-body-ipc-plugin.mjs'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
describe('fixed protected HTTP IPC boundary observer', () => {
  const host = () => {
    const states: string[] = []
    vi.stubGlobal('window', {location: {pathname: '/livetv'}, PinkNative: {
      postMessage: (raw: string) => {
        const value = JSON.parse(raw)
        expect(value.id).toBe('0')
        expect(value.operation).toBe('bodyIpc')
        expect(Object.keys(value.payload)).toEqual(['stage'])
        states.push(value.payload.stage)
      }
    }})
    return states
  }
  it('preserves an unresolved promise and distinguishes synchronous return from response', async () => {
    const states = host()
    let resolve!: (value: Uint8Array) => void
    const pending = new Promise<Uint8Array>(r => {resolve=r})
    const args = {rid: 17}
    const invoke = vi.fn((command, supplied) => {
      expect(states).toEqual(['calling'])
      expect(command).toBe('plugin:http|fetch_read_body')
      expect(supplied).toBe(args)
      return pending
    })
    expect(observePinkBodyInvoke(invoke, 'plugin:http|fetch_read_body', args)).toBe(pending)
    expect(states).toEqual(['calling','returned'])
    const bytes = new Uint8Array([0,255,1])
    resolve(bytes)
    expect(await pending).toBe(bytes)
    expect(states).toEqual(['calling','returned','resolved'])
    expect(invoke).toHaveBeenCalledTimes(1)
  })
  it('preserves rejection without exposing its private message', async () => {
    const states = host()
    const failure = new Error('private-provider-account-url')
    const pending = Promise.reject(failure)
    expect(observePinkBodyInvoke(() => pending, 'plugin:http|fetch_read_body', {})).toBe(pending)
    await expect(pending).rejects.toBe(failure)
    expect(states).toEqual(['calling','returned','rejected'])
  })
  it('preserves a synchronous throw and records no private error', () => {
    const states = host()
    const failure = new Error('private-account')
    expect(() => observePinkBodyInvoke(() => {throw failure}, 'plugin:http|fetch_read_body', {})).toThrow(failure)
    expect(states).toEqual(['calling','threw'])
  })
  it('passes through outside the actual Live TV route', async () => {
    const native = {postMessage: vi.fn()}
    vi.stubGlobal('window', {location:{pathname:'/login'}, PinkNative:native})
    const pending = Promise.resolve(42)
    expect(observePinkBodyInvoke(() => pending, 'plugin:http|fetch_read_body', {})).toBe(pending)
    await pending
    expect(native.postMessage).not.toHaveBeenCalled()
  })
  it('cannot turn a failing diagnostic transport into a provider failure', async () => {
    vi.stubGlobal('window', {location:{pathname:'/livetv'}, PinkNative:{postMessage:()=>{throw new Error('observer')}}})
    const pending = Promise.resolve(42)
    expect(observePinkBodyInvoke(() => pending, 'plugin:http|fetch_read_body', {})).toBe(pending)
    expect(await pending).toBe(42)
  })
  it('patches exactly the installed pinned HTTP command and leaves all other transports untouched', () => {
    const cjs = createRequire(import.meta.url).resolve('@tauri-apps/plugin-http')
    const esm = cjs.replace(/index\.cjs$/, 'index.js')
    expect(esm.endsWith('/dist-js/index.js')).toBe(true)
    const original = readFileSync(esm,'utf8')
    const result = observeHttpBodySource(original,esm)
    expect(result?.code).toContain("data = await observePinkBodyInvoke(invoke, 'plugin:http|fetch_read_body', {")
    expect(result?.code).toContain("pull: (controller) => readChunk(controller)")
    expect(result?.code).toContain("invoke('plugin:http|fetch_send'")
    expect(observeHttpBodySource(original,'/node_modules/@tauri-apps/api/core.js')).toBeNull()
    expect(() => observeHttpBodySource(original.replace('fetch_read_body','STALE'),esm)).toThrow('drift')
  })
})
