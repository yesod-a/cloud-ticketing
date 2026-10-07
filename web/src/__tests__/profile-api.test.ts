import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { changePassword, getProfile, session, updateProfile, uploadAvatar } from '../auth/authApi'

describe('profile api', () => {
  beforeEach(() => { session.set('access-token', 'refresh-token') })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('loads the authenticated profile', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: {
      id: 'u1', phone: '13800138000', email: null, nickname: 'A', status: 'ACTIVE', avatarUrl: null, createdAt: null
    } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getProfile()).resolves.toMatchObject({ id: 'u1', nickname: 'A' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me')
  })

  it('updates nickname with a JSON PATCH request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { nickname: 'New' } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await updateProfile('New')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'PATCH' })
    expect(JSON.parse(String(fetchMock.mock.calls[0][1].body))).toEqual({ nickname: 'New' })
  })

  it('uploads an avatar as multipart without overriding the browser boundary', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { id: 'u1' } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['image'], 'avatar.png', { type: 'image/png' })

    await uploadAvatar(file)
    const request = fetchMock.mock.calls[0][1]
    expect(request.method).toBe('POST')
    expect(request.body).toBeInstanceOf(FormData)
    expect(request.headers.get('Content-Type')).toBeNull()
    expect((request.body as FormData).get('file')).toBe(file)
  })

  it('clears the local session after a successful password change', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: null }), { status: 200 })))

    await changePassword('OldPass1', 'NewPass1')

    expect(session.token()).toBeNull()
    expect(session.refreshToken()).toBeNull()
  })
})
