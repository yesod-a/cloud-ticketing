import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createOrder, getActivity, getSeats } from '../api'
import { session } from '../auth/authApi'

describe('booking api', () => {
  beforeEach(() => { session.set('access-token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })
  it('unwraps database-backed activity details and seats', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { activity: { id: 'a1' }, sessions: [{ id: 's1' }] } }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: [{ id: 'seat-1', row: 'A', number: 1, status: 'AVAILABLE' }] }), { status: 200 }))
    await expect(getActivity('a1')).resolves.toMatchObject({ sessions: [{ id: 's1' }] })
    await expect(getSeats('s1')).resolves.toHaveLength(1)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/sessions/s1/seats')
  })
  it('sends selected database seat ids with the authenticated request', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'o1', status: 'PENDING' }), { status: 200 }))
    await expect(createOrder('s1', ['seat-1', 'seat-2'], 'key-1')).resolves.toMatchObject({ id: 'o1' })
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({ sessionId: 's1', seatIds: 'seat-1,seat-2', idempotencyKey: 'key-1' })
  })
})
