import {afterEach, describe, expect, it, vi} from "vitest"
import {readFileSync} from "node:fs"
import {pickPinkFastCategory, filterPinkScopedRows} from "../src/scripts/lib/pink-fast-category.js"

afterEach(() => vi.unstubAllGlobals())

describe("PINK native quick-category selection", () => {
  it("chooses the saved category or safe recent category, never adult content by default", () => {
    const map = new Map([["1","XXX ADULT"],["2","[MULTI-LANG] TOP 2026 MOVIES"],["3","Documentary"]])
    expect(pickPinkFastCategory(map,"Documentary")).toEqual({id:"3",name:"Documentary"})
    expect(pickPinkFastCategory(map,"")).toEqual({id:"2",name:"[MULTI-LANG] TOP 2026 MOVIES"})
    expect(pickPinkFastCategory(new Map([["1","XXX ADULT"]]),"")).toBeNull()
  })
  it("rejects ambiguous category names, nonnumeric IDs and virtual categories", () => {
    const map = new Map([["7","Drama"],["8","Drama"],["9","Comedy"],["1&injected=true","Bad"]])
    expect(pickPinkFastCategory(map,"Drama")).toBeNull()
    expect(pickPinkFastCategory(map,"Comedy")).toEqual({id:"9",name:"Comedy"})
    expect(pickPinkFastCategory(map,"__favorites__")).toBeNull()
    expect(pickPinkFastCategory(map,"Bad")).toBeNull()
  })
  it("restricts a provider that ignores category_id instead of polluting all-cache keys", () => {
    const records=[{id:1,category:"Drama"},{id:2,category:"Comedy"},{id:3,category:"Drama"}]
    expect(filterPinkScopedRows(records,"Drama")).toEqual([records[0],records[2]])
    const generated = readFileSync(new URL("../src/scripts/lib/catalog.js", import.meta.url), "utf8")
    expect(generated).toContain('categoryId ? null : playlistId')
    const native = readFileSync(new URL("../src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkVodCatalog.kt", import.meta.url),"utf8")
    expect(native).toContain('runtime.openProtectedConnection(url)')
    expect(native).toContain('require(Regex("[0-9]{1,12}").matches(categoryId))')
    expect(native).not.toContain("url.openConnection")
  })
})

vi.mock("../src/scripts/lib/creds.js", () => ({
  getActiveEntry: async () => ({_id:"selected-fixture"}),
  getEntries: async () => [{_id:"selected-fixture"}],
  xtreamCandidatesFor: () => [{host:"fixture.invalid",user:"secret",pass:"private"}],
  buildApiUrl: () => { throw Error("unprotected browser route") },
  getMirrorPin: () => 0,
  setMirrorPin: vi.fn()
}))
vi.mock("../src/scripts/lib/provider-fetch.js", () => ({
  providerFetch: () => {throw Error("unprotected network route")}
}))
vi.mock("../src/scripts/lib/app-settings.js", () => ({getNetworkTimeoutSeconds:() => 30}))
vi.mock("../src/scripts/lib/toast.ts", () => ({toast:vi.fn()}))
import {xtreamApiFetch} from "../src/scripts/lib/xtream-api.js"

describe("PINK native scoped category bridge", () => {
  it.each(["get_vod_streams", "get_series"])("sends only a vetted numeric category_id for %s", async action => {
    const sent: any[] = []
    const native: any = {onmessage:null,postMessage(text: string) {
      const req=JSON.parse(text)
      sent.push(req)
      const result=req.operation==="vodCatalog" ? {token:"fixture",size:0} :
        req.operation==="vodCatalogChunk" ? {done:true} : true
      queueMicrotask(() => native.onmessage({data:JSON.stringify({id:req.id,ok:true,result})}))
    }}
    vi.stubGlobal("window",{PinkNative:native})
    const response=await xtreamApiFetch(action,{category_id:"71"},{entryId:"selected-fixture"})
    expect(await response.text()).toBe("")
    const open=sent.find(x=>x.operation==="vodCatalog")
    expect(open.payload).toEqual({action,entryId:"selected-fixture",categoryId:"71"})
    expect(sent.some(x=>x.operation==="vodCatalogClose")).toBe(true)
    expect(sent.every(x=>!JSON.stringify(x).includes("private"))).toBe(true)
  })
  it("rejects unsafe category filters and all new parameters on metadata requests", async () => {
    const native:any={postMessage:vi.fn(),onmessage:null}
    vi.stubGlobal("window",{PinkNative:native})
    await expect(xtreamApiFetch("get_series",{category_id:"7&evil=true"})).rejects.toThrow("inválido")
    await expect(xtreamApiFetch("get_series_categories",{category_id:"7"})).rejects.toThrow("inválido")
    await expect(xtreamApiFetch("get_vod_streams",{category_id:"7",extra:"1"})).rejects.toThrow("inválido")
    expect(native.postMessage).not.toHaveBeenCalled()
  })
})
