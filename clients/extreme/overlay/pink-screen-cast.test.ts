// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
const toast = vi.hoisted(() => vi.fn())
const installPinkBridge = vi.hoisted(() => vi.fn())
vi.mock('../src/scripts/lib/toast.js', () => ({ toast }))
vi.mock('../src/scripts/lib/pink-bridge.js', () => ({ installPinkBridge }))
import { openPinkCastSettings } from '../src/scripts/lib/pink-screen-cast.js'
afterEach(() => { toast.mockClear(); installPinkBridge.mockClear(); delete (window as any).PinkScreenCast })
describe('protected owner Play on TV', () => {
  it('opens the Android picker and never passes provider media URLs', async () => {
    const open = vi.fn(async () => true)
    ;(window as any).PinkScreenCast = { open }
    expect(await openPinkCastSettings()).toBe(true)
    expect(installPinkBridge).toHaveBeenCalledOnce()
    expect(open).toHaveBeenCalledWith()
    expect(toast).not.toHaveBeenCalled()
  })
  it('shows a safe fallback when screen mirroring is unavailable', async () => {
    expect(await openPinkCastSettings()).toBe(false)
    expect(toast).toHaveBeenCalledOnce()
    const open = vi.fn(async () => { throw new Error('unsupported') })
    ;(window as any).PinkScreenCast = { open }
    expect(await openPinkCastSettings()).toBe(false)
    expect(open).toHaveBeenCalledOnce()
  })
})
