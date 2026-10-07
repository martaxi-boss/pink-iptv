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
