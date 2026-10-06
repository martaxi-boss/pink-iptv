import { afterEach, describe, expect, it, vi } from "vitest"
import { choosePinkLiveContainer, pinkNativeOwnsConnectivity } from "../src/scripts/lib/pink-runtime-policy.ts"

afterEach(() => vi.unstubAllGlobals())

describe("PINK runtime presentation and live-container policy", () => {
  it("prefers HLS when the provider allows it", () => {
    expect(choosePinkLiveContainer(["ts", "m3u8"])).toBe("m3u8")
  })

  it("uses MPEG-TS only when HLS is not advertised", () => {
    expect(choosePinkLiveContainer(["ts"])).toBe("ts")
  })

  it("defaults safely to HLS when provider format metadata is absent", () => {
    expect(choosePinkLiveContainer(null)).toBe("m3u8")
  })

  it("does not let generic WebView navigator state own connectivity in native PINK", () => {
    vi.stubGlobal("window", { PinkNative: { postMessage: vi.fn() } })
    expect(pinkNativeOwnsConnectivity()).toBe(true)
  })

  it("keeps browser connectivity semantics outside the native PINK bridge", () => {
    vi.stubGlobal("window", {})
    expect(pinkNativeOwnsConnectivity()).toBe(false)
  })
})
