import { describe, expect, it, vi } from 'vitest'
import { resolvePinkSession } from '../src/scripts/lib/pink-session.js'
describe('PINK managed account contract', () => {
  const reply = (body: object) => vi.fn().mockResolvedValue({ ok: true, json: async () => body })
  it('uses the existing backend with only username/password and preserves HTTP origin', async () => {
    const transport = reply({ code: 'SUCCESS', xtream_base_url: 'http://provider.example:8080' })
    const account = await resolvePinkSession('fixture-user', 'fixture-password', transport)
    expect(account.serverUrl).toBe('http://provider.example:8080')
    const [endpoint, request] = transport.mock.calls[0]
    expect(endpoint).toBe('https://pink-iptv.duckdns.org/v1/session/resolve')
    expect(request.redirect).toBe('error')
    expect(request.maxRedirections).toBe(0)
    expect(JSON.parse(request.body)).toEqual({ username: 'fixture-user', password: 'fixture-password' }) // pragma: allowlist secret — synthetic test fixture
  })
  it.each(['INVALID_CREDENTIALS', 'EXPIRED', 'DISABLED', 'UPSTREAM_ERROR'])('does not accept %s', async code => {
    await expect(resolvePinkSession('fixture-user', 'fixture-password', reply({ code }))).rejects.toThrow()
  })
  it.each(['ftp://provider.example', 'https://u:p@provider.example', 'https://provider.example/?token=x', 'https://provider.example/api', 'https://provider.example/#x'])('rejects malformed returned origin %s', async url => {
    await expect(resolvePinkSession('fixture-user', 'fixture-password', reply({ code: 'SUCCESS', xtream_base_url: url }))).rejects.toThrow()
  })
  it('does not expose a server error body', async () => {
    const transport = vi.fn().mockResolvedValue({ ok: false, json: () => { throw new Error('private detail') } })
    await expect(resolvePinkSession('fixture-user', 'fixture-password', transport)).rejects.toThrow('Não foi possível contactar')
  })
  it('uses only the native protected startup path in production', async () => {
    const native = { resolve: vi.fn(() => JSON.stringify({ code: 'SUCCESS', xtream_base_url: 'http://provider.example:8080' })) }
    vi.stubGlobal('window', { PinkConnection: native })
    try {
      expect((await resolvePinkSession('fixture-user', 'fixture-password')).serverUrl).toBe('http://provider.example:8080')
      expect(native.resolve).toHaveBeenCalledWith('fixture-user', 'fixture-password')
    } finally { vi.unstubAllGlobals() }
  })

  it('requests Android VPN consent through the native bridge before resolving credentials', async () => {
    const order: string[] = []
    const native = {
      requestVpnConsent: vi.fn(async () => { order.push('consent'); return true }),
      resolve: vi.fn(async () => {
        order.push('resolve')
        return JSON.stringify({code:'SUCCESS',xtream_base_url:'https://provider.example/'})
      }),
    }
    vi.stubGlobal('window', { PinkConnection: native })
    try {
      const account = await resolvePinkSession('fixture-user', 'fixture-password')
      expect(account.serverUrl).toBe('https://provider.example')
      expect(order).toEqual(['consent','resolve'])
      expect(native.resolve).toHaveBeenCalledWith('fixture-user','fixture-password')
    } finally { vi.unstubAllGlobals() }
  })
  it.each([
    ['VPN_PERMISSION','VPN_PERMISSION'],
    ['CONTROL_HTTPS','CONTROL_HTTPS'],
    ['VPN_ENROLL','VPN_ENROLL'],
    ['VPN_LIMIT','VPN_LIMIT'],
    ['VPN_ACTIVATION','VPN_ACTIVATION'],
    ['DEVICE_SECURITY','DEVICE_SECURITY'],
    ['LOGIN_TIMEOUT','LOGIN_TIMEOUT'],
    ['LOGIN_UNAVAILABLE','LOGIN_UNAVAILABLE'],
  ])('shows only a fixed safe error for %s', async (code, marker) => {
    const privateError = Object.assign(new Error('private endpoint and credentials'), {code})
    vi.stubGlobal('window', { PinkConnection: {
      requestVpnConsent: vi.fn(async () => true),
      resolve: vi.fn(async () => { throw privateError }),
    } })
    try {
      await expect(resolvePinkSession('fixture-user', 'fixture-password')).rejects.toThrow('PINK: ' + marker)
      await expect(resolvePinkSession('fixture-user', 'fixture-password')).rejects.not.toThrow('private endpoint')
    } finally { vi.unstubAllGlobals() }
  })

  it('cannot silently use a direct transport without the native bridge', async () => {
    vi.stubGlobal('window', {})
    try { await expect(resolvePinkSession('fixture-user', 'fixture-password')).rejects.toThrow('Serviço PINK indisponível') }
    finally { vi.unstubAllGlobals() }
  })
  it('does not release a provider account when native tunnel preparation fails', async () => {
    vi.stubGlobal('window', { PinkConnection: { resolve: () => JSON.stringify({ code: 'VPN_UNAVAILABLE' }) } })
    try { await expect(resolvePinkSession('fixture-user', 'fixture-password')).rejects.toThrow('Serviço temporariamente') }
    finally { vi.unstubAllGlobals() }
  })
})
