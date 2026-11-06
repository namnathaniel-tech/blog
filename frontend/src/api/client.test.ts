import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './client'

afterEach(() => {
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('api()', () => {
  it('prefixes /api and parses JSON', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }))
    await expect(api('/public/meta')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/public/meta')
  })

  it('sends the CSRF header on unsafe methods', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))
    await api('/x', { method: 'POST', body: '{}' })
    const headers = fetchMock.mock.calls[0][1]!.headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123')
  })

  it('fetches a CSRF cookie first when none exists', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (input === '/api/public/meta') document.cookie = 'XSRF-TOKEN=fresh'
      return new Response(null, { status: 204 })
    })
    await api('/x', { method: 'POST', body: '{}' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/public/meta')
    expect((fetchMock.mock.calls[1][1]!.headers as Headers).get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('throws ApiError with the problem detail', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 404, title: 'Not Found', detail: 'No such post' }), { status: 404 }),
    )
    const err = await api('/nope').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(404)
    expect((err as ApiError).message).toBe('No such post')
  })
})
