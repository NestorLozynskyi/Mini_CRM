import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, query, setUnauthorizedHandler } from './client'

const json = (status: number, body: unknown, type = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })

describe('api client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok123; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => {})
  })

  it('sends time zone on every request and CSRF token on mutations', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(201, { id: 1 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api('/api/x', { method: 'POST', body: { a: 1 } })).resolves.toEqual({ id: 1 })
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('tok123')
    expect(headers.get('X-Time-Zone')).toBeTruthy()
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(fetchMock.mock.calls[0][1].body).toBe('{"a":1}')
  })

  it('maps validation problems to field errors (first message per field)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(400, {
      status: 400, code: 'validation_failed',
      errors: [{ field: 'name', message: 'Name is required' }, { field: 'email', message: 'Enter a valid email address' }],
    }, 'application/problem+json')))
    const e = (await api('/api/x', { method: 'PUT', body: {} }).catch((x) => x)) as ApiError
    expect(e).toBeInstanceOf(ApiError)
    expect(e.isValidation).toBe(true)
    expect(e.fieldErrors()).toEqual({ name: 'Name is required', email: 'Enter a valid email address' })
  })

  it('notifies the global handler on 401 with the problem code (demo expiry)', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { status: 401, code: 'demo_expired' })))
    await expect(api('/api/clients')).rejects.toMatchObject({ code: 'demo_expired' })
    expect(handler).toHaveBeenCalledWith('demo_expired')
    handler.mockClear()
    await expect(api('/api/auth/me', { quiet401: true })).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()
  })

  it('reports network failures and non-JSON errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(api('/api/x')).rejects.toMatchObject({ problem: { status: 0, code: 'network_error' } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad Gateway', { status: 502 })))
    await expect(api('/api/x')).rejects.toMatchObject({ problem: { status: 502, code: 'internal_error' } })
  })

  it('builds query strings without empty values', () => {
    expect(query({ q: 'north', type: undefined, tag: ['VIP', 'Local'], x: '' })).toBe('?q=north&tag=VIP&tag=Local')
    expect(query({})).toBe('')
  })
})
