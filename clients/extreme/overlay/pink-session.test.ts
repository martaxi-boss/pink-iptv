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
    expect(JSON.parse(request.body)).toEqual({ username: 'fixture-user', password: 'fixture-password' })
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
})
