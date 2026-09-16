import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { logout, session } from '../auth/authApi'

describe('logout api', () => {
  beforeEach(() => { session.set('access', 'refresh-token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('revokes the refresh session on the server before clearing local tokens', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: null }), { status: 200 }))
    await logout()
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/logout')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ refreshToken: 'refresh-token', accessToken: 'access' })
    expect(session.token()).toBeNull()
    expect(session.refreshToken()).toBeNull()
  })
})
