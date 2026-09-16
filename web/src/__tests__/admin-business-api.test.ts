import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { offlineAdminActivity } from '../adminApi'
import { session } from '../auth/authApi'

describe('admin business api', () => {
  beforeEach(() => { session.set('admin-token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('posts an activity offline command to the database-backed admin endpoint', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'a1', status: 'OFFLINE' }), { status: 200 }))
    await expect(offlineAdminActivity('a1')).resolves.toMatchObject({ status: 'OFFLINE' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/activities/a1/offline')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST' })
  })
})
