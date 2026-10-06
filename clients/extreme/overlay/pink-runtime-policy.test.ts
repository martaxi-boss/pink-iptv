import { afterEach, describe, expect, it, vi } from "vitest"
import { buildPinkLiveStreamUrl, pinkNativeOwnsConnectivity } from "../src/scripts/lib/pink-runtime-policy.ts"

afterEach(() => vi.unstubAllGlobals())

describe("PINK runtime provider and connectivity policy", () => {
  it("builds the Mega authoritative legacy live path without a synthetic extension", () => {
    expect(buildPinkLiveStreamUrl(
      { host: "http://provider.example/", user: "fixture user", pass: "fixture/pass" },
      30647,
    )).toBe("http://provider.example/fixture%20user/fixture%2Fpass/30647")
  })

  it("fails closed when a live route cannot be constructed", () => {
    expect(buildPinkLiveStreamUrl({ host: "", user: "u", pass: "p" }, 1)).toBe("")
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
