import { describe, expect, it } from 'vitest'
import { catalogFailure, catalogActionFailure, catalogFailureDetail } from '../src/scripts/lib/pink-catalog-diagnostic.js'
import { parsePinkCatalog } from '../src/scripts/lib/pink-catalog-worker.js'

describe('physical catalog decision evidence', () => {
  it('formats only fixed phases, actions, build ID and bounded numeric counters', () => {
    const failure = catalogActionFailure(catalogFailure('READ_IDLE', {bytes:131072,elapsedMs:20001,httpStatus:200}), 'get_series', 'BRIDGE_OPEN')
    expect(catalogFailureDetail(failure)).toMatch(/^PINK [a-f0-9]{8} · SERIES:CATALOG · READ_IDLE · HTTP 200 · 131072 B · 20 s$/)
    const privateInput = {code:'https://private.invalid/password',catalogAction:'username',bytes:'secret',elapsedMs:-2,httpStatus:999,message:'response body',stack:'private stack'}
    expect(catalogFailureDetail(privateInput)).toMatch(/^PINK [a-f0-9]{8} · CATALOG · CATALOG_FAILED$/)
    expect(catalogFailureDetail({code:'STAGE',bytes:Infinity,elapsedMs:NaN})).not.toMatch(/Infinity|NaN/)
  })
  it('preserves fixed pre-transfer native failure phases without leaking exceptions', () => {
    for (const phase of ['ACCOUNT_BINDING', 'SOURCE_VALIDATION', 'VPN_NETWORK', 'STAGE_FILE', 'STAGE_CAPACITY', 'STAGE_EXPIRED', 'STAGE_LIFECYCLE']) {
      const protectedError = catalogActionFailure(
        catalogFailure(phase, {bytes:'sensitive', httpStatus:-1, elapsedMs:-1, providerUrl:'https://private.invalid'}),
        'get_vod_categories', 'BRIDGE_OPEN'
      )
      const detail = catalogFailureDetail(protectedError)
      expect(detail).toContain('MOVIES:CATEGORIES · ' + phase)
      expect(detail).not.toMatch(/private|sensitive|providerUrl|https/)
    }
    expect(catalogFailureDetail(catalogFailure('account/password/secret'))).toContain('CATALOG_FAILED')
  })
  it('distinguishes JSON failure from mapping failure without returning the body', () => {
    expect(() => parsePinkCatalog('<private>', 'vod', new Map())).toThrowError(expect.objectContaining({code:'WORKER_PARSE'}))
    expect(() => parsePinkCatalog('[null]', 'series', new Map())).toThrowError(expect.objectContaining({code:'WORKER_MAP'}))
    expect(parsePinkCatalog('[]', 'vod', new Map())).toEqual([])
  })
})
