import { describe, expect, it, vi } from 'vitest'
import { pinkAccountSubtitle, pinkDiagnosticText } from '../src/scripts/lib/pink-presentation.js'
import { redactUrl } from '../src/scripts/lib/log.ts'
const sourceRead = vi.hoisted(() => vi.fn())
vi.mock('@/scripts/lib/catalog.js', () => ({ ensureLive: sourceRead }))
vi.mock('@/scripts/lib/creds.js', () => ({ entryToCreds: sourceRead, fmtBase: sourceRead }))

describe('PINK customer account and diagnostic privacy', () => {
  it('shows the username without the authoritative account origin', () => {
    expect(pinkAccountSubtitle({ type: 'xtream', username: 'fixture-user', serverUrl: 'https://private-sub.example' })).toBe('fixture-user')
  })
  it('removes complete provider URLs from visible diagnostics while keeping status', () => {
    for (const url of ['https://private-sub.example/live/fixture-user/fixture-pass/10.ts', 'HTTPS://fixture-user:fixture-pass@private-sub.example/player_api.php?token=fixture-token', 'https%3A%2F%2Fprivate-sub.example%2Flive%2Ffixture-user%2Ffixture-pass%2F10.ts']) {
      expect(redactUrl(`HTTP 503 from ${url}`)).toBe('HTTP 503 from [PINK]')
    }
  })
  it('keeps diagnostic JSON valid and removes provider origin fields', () => {
    const output = JSON.parse(pinkDiagnosticText(JSON.stringify({ host: 'private-sub.example', serverUrl: 'https://private-sub.example', status: 503 })))
    expect(output).toEqual({ host: '[PINK]', serverUrl: '[PINK]', status: 503 })
  })
  it('cannot export a managed account as a credential-bearing technical playlist', async () => {
    const { buildM3UEntriesForEntry } = await import('../src/scripts/lib/export-m3u.ts')
    expect(await buildM3UEntriesForEntry({ type: 'xtream', _id: 'fixture-id' })).toEqual({ entries: [], skippedCount: 0 })
    expect(sourceRead).not.toHaveBeenCalled()
  })
})
