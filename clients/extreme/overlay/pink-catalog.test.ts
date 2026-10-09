import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchPinkLiveCatalog } from '../src/scripts/lib/pink-catalog.js'

afterEach(() => vi.unstubAllGlobals())
describe('fixed native protected Live TV catalog', () => {
  it('sends only the action and selected account ID and returns complete Unicode rows', async () => {
    const native: any = {postMessage: vi.fn(), onmessage: null}
    vi.stubGlobal('window', {PinkNative: native})
    const response = fetchPinkLiveCatalog('get_live_streams', 'selected-fixture')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    expect(request).toEqual({id:'1', operation:'liveCatalog', payload:{action:'get_live_streams', entryId:'selected-fixture'}})
    native.onmessage({data:JSON.stringify({id:request.id, ok:true, result:'[{"stream_id":7,"name":"Televisão 🎬"}]'})})
    expect(await (await response).json()).toEqual([{stream_id:7,name:'Televisão 🎬'}])
  })
  it('rejects unsupported actions before issuing any request', async () => {
    const native = {postMessage:vi.fn()}
    vi.stubGlobal('window',{PinkNative:native})
    await expect(fetchPinkLiveCatalog('http://provider.example/', 'fixture')).rejects.toThrow('inválido')
    expect(native.postMessage).not.toHaveBeenCalled()
  })
  it('fails without a native bridge and never uses browser fetch', async () => {
    vi.stubGlobal('window',{})
    const fetch = vi.fn(); vi.stubGlobal('fetch',fetch)
    await expect(fetchPinkLiveCatalog('get_live_categories','fixture')).rejects.toThrow('indisponível')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('propagates a protected failure without provider fallback or private errors', async () => {
    const native: any = {postMessage:vi.fn(), onmessage:null}
    vi.stubGlobal('window',{PinkNative:native})
    const fetch = vi.fn(); vi.stubGlobal('fetch',fetch)
    const response = fetchPinkLiveCatalog('get_live_categories','fixture')
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    native.onmessage({data:JSON.stringify({id:request.id,ok:false,error:'private-provider-url'})})
    await expect(response).rejects.toThrow('temporariamente indisponível')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('rejects cancellation before sending and after the native response', async () => {
    const native: any = {postMessage:vi.fn(),onmessage:null}
    vi.stubGlobal('window',{PinkNative:native})
    const controller = new AbortController(); controller.abort(new Error('cancelled'))
    await expect(fetchPinkLiveCatalog('get_live_streams','fixture',controller.signal)).rejects.toThrow('cancelled')
    expect(native.postMessage).not.toHaveBeenCalled()
    const pending = new AbortController()
    const response = fetchPinkLiveCatalog('get_live_streams','fixture',pending.signal)
    const request = JSON.parse(native.postMessage.mock.calls[0][0])
    pending.abort(new Error('cancelled'))
    native.onmessage({data:JSON.stringify({id:request.id,ok:true,result:'[]'})})
    await expect(response).rejects.toThrow('cancelled')
  })
})

vi.mock('../src/scripts/lib/app-settings.js', () => ({getNetworkTimeoutSeconds:()=>30}))
vi.mock('../src/scripts/lib/toast.ts', () => ({toast:vi.fn()}))
vi.mock('../src/scripts/lib/creds.js', () => ({
  getActiveEntry: async () => ({_id:'selected-fixture'}),
  getEntries: async () => [{_id:'selected-fixture'}],
  xtreamCandidatesFor: () => [{host:'fixture.invalid', user:'private', pass:'private'}],
  buildApiUrl: vi.fn(() => {throw Error('Generic provider route used')}),
  getMirrorPin: () => 0, setMirrorPin: vi.fn(),
}))
vi.mock('../src/scripts/lib/provider-fetch.js', () => ({providerFetch: vi.fn(() => {throw Error('Generic fetch used')})}))
import { xtreamApiFetch } from '../src/scripts/lib/xtream-api.js'
import { fetchPinkVodCatalog } from '../src/scripts/lib/pink-catalog.js'
import { readFileSync } from 'node:fs'

function vodBridge(body: string, fail = '', truncate = false) {
  const bytes = new TextEncoder().encode(body)
  let offset = 0
  const native: any = {onmessage:null,postMessage:vi.fn((text: string) => {
    const request = JSON.parse(text)
    queueMicrotask(() => {
      let result: any = true
      if (request.operation === 'liveCatalog') result = body
      if (request.operation === 'vodCatalog') result = {token:'fixture-token',size:bytes.length}
      if (request.operation === 'vodCatalogChunk') {
        if (truncate || offset >= bytes.length) result = {done:true}
        else { result = {data:Buffer.from(bytes.slice(offset,offset+7)).toString('base64')}; offset += 7 }
      }
      native.onmessage({data:JSON.stringify({id:request.id,ok:request.operation!==fail,result})})
    })
  })}
  vi.stubGlobal('window',{PinkNative:native})
  return native
}

describe('protected Movies/Series transport', () => {
  it.each(['get_vod_categories','get_vod_streams','get_series_categories','get_series'])('routes %s natively without generic fetch and preserves Unicode across byte boundaries', async action => {
    const body = '[{"stream_id":7,"series_id":9,"name":"Português 🎬"}]'
    const native = vodBridge(body)
    const response = await xtreamApiFetch(action, {}, {entryId:'selected-fixture'})
    expect(await response.text()).toBe(body)
    const calls = native.postMessage.mock.calls.map(([text]:any) => JSON.parse(text))
    const category = action === 'get_vod_categories' || action === 'get_series_categories'
    const operation = category ? 'liveCatalog' : 'vodCatalog'
    expect(calls.filter((c:any)=>c.operation===operation)).toEqual([{id:'1',operation,payload:{action,entryId:'selected-fixture'}}])
    expect(calls.some((c:any)=>c.operation==='vodCatalogClose')).toBe(!category)
    expect(calls.some((c:any)=>c.operation===(category ? 'vodCatalog' : 'liveCatalog'))).toBe(false)
    expect(calls.every((c:any)=>!JSON.stringify(c).includes('private'))).toBe(true)
  })
  it('rejects protected failure and incomplete transfer rather than using another network', async () => {
    vodBridge('[]','vodCatalog')
    await expect(xtreamApiFetch('get_series')).rejects.toMatchObject({code:'CATALOG_FAILED',catalogAction:'get_series'})
    const native = vodBridge('[{"id":1}]','',true)
    await expect((await fetchPinkVodCatalog('get_series','selected-fixture')).text()).rejects.toMatchObject({code:'BODY_SIZE',catalogAction:'get_series'})
    expect(native.postMessage.mock.calls.some(([s]:any)=>JSON.parse(s).operation==='vodCatalogClose')).toBe(true)
  })
  it('refuses a failed native category request without entering the staged or generic provider transports', async () => {
    const native = vodBridge('[]', 'liveCatalog')
    await expect(fetchPinkVodCatalog('get_vod_categories','selected-fixture')).rejects.toMatchObject({
      code: 'BRIDGE_OPEN', catalogAction: 'get_vod_categories'
    })
    expect(native.postMessage.mock.calls.map(([value]:any)=>JSON.parse(value).operation)).toEqual(['liveCatalog'])
  })
  it('cancellation and stream cancellation release the native private stage', async () => {
    const native = vodBridge('[{"id":1}]')
    const controller = new AbortController()
    const response = await fetchPinkVodCatalog('get_vod_streams','selected-fixture',controller.signal)
    controller.abort(new Error('cancelled'))
    await expect(response.text()).rejects.toThrow('cancelled')
    expect(native.postMessage.mock.calls.some(([s]:any)=>JSON.parse(s).operation==='vodCatalogClose')).toBe(true)
    const other = vodBridge('[{"id":1}]')
    await (await fetchPinkVodCatalog('get_series','selected-fixture')).body!.cancel()
    expect(other.postMessage.mock.calls.some(([s]:any)=>JSON.parse(s).operation==='vodCatalogClose')).toBe(true)
  })
  it('keeps native admission, selected-account binding and owned Network opener mandatory', () => {
    const native = readFileSync(new URL('../src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkVodCatalog.kt',import.meta.url),'utf8')
    expect(native).toContain('runtime.openProtectedConnection(url)')
    const live = readFileSync(new URL('../src-tauri/gen/android/app/src/main/java/com/pinkiptv/extreme/PinkCatalog.kt',import.meta.url),'utf8')
    expect(live).toContain('return readHttp(runtime.openProtectedConnection(url), action)')
    expect(live).toContain('"get_vod_categories", "get_series_categories"')
    expect(live).not.toContain('url.openConnection')
    expect(native).toContain('if (!lease.active()) throw PinkCatalogFailure("CANCELLED")')
    expect(native).toContain('if (!PinkVpnRuntime.isReady()) throw PinkCatalogFailure("VPN_LOST")')
    expect(native).toContain('if (account.getString("selectedId") != entryId) throw PinkCatalogFailure("ACCOUNT_BINDING")')
    expect(native).not.toContain('url.openConnection')
    expect(native).not.toMatch(/(?<!get)JSONArray\(/)
  })
})
