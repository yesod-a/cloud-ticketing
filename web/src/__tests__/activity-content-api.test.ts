import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getActivity, listActivities } from '../api'
import { createAdminActivity, reorderAdminActivityImages, uploadAdminActivityImage } from '../adminApi'
import { session } from '../auth/authApi'

describe('activity content api', () => {
  beforeEach(() => { session.set('token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('keeps description and cover metadata in public activity responses', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: 'a1', description: '详情', coverImageUrl: '/cover' }] }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { activity: { id: 'a1', description: '详情', images: [{ id: 'i1', url: '/detail' }] } , sessions: [] } }), { status: 200 }))
    await expect(listActivities()).resolves.toMatchObject({ items: [{ description: '详情', coverImageUrl: '/cover' }] })
    await expect(getActivity('a1')).resolves.toMatchObject({ activity: { images: [{ url: '/detail' }] } })
  })

  it('sends description on admin create and multipart image uploads', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'a1', title: 'A', organizer: 'Org', description: 'D' }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'i1', objectKey: 'hidden' }), { status: 200 }))
    await createAdminActivity('A', 'Org', 'D')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ description: 'D' })
    await uploadAdminActivityImage('a1', new File(['x'], 'poster.png', { type: 'image/png' }), 'COVER')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/activities/a1/images')
    expect(fetchMock.mock.calls[1][1]?.body).toBeInstanceOf(FormData)
  })

  it('submits the complete detail image order for admin reordering', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OK' }), { status: 200 }))
    await reorderAdminActivityImages('a1', ['i3', 'i1', 'i2'])
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/activities/a1/images/order')
    expect(fetchMock.mock.calls[0][1]?.method).toBe('PUT')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ imageIds: ['i3', 'i1', 'i2'] })
  })
})
