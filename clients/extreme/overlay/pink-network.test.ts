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
