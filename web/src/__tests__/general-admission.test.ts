import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BookingView from '../views/BookingView.vue'
import { session } from '../auth/authApi'

describe('general admission booking', () => {
  beforeEach(() => { session.set('token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('renders quantity controls and sends quantity instead of seat ids', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { activity: { id: 'a1', description: '现场演出', images: [{ id: 'i1', url: '/poster.jpg', sortOrder: 0 }] }, sessions: [{ id: 's1', activityId: 'a1', startsAt: '2026-10-01T10:00:00Z', endsAt: '2026-10-01T11:00:00Z', venue: 'Hall', status: 'ONSALE', layoutMode: 'GENERAL_ADMISSION', capacity: 100, remainingCapacity: 98, purchaseLimit: 4, remainingPurchaseLimit: 4 }] } }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { items: [], page: 0, size: 100, total: 0, totalPages: 0 } }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: 'o1', status: 'PENDING', seatIds: '' }), { status: 200 }))
    const wrapper = mount(BookingView, { props: { activity: { id: 'a1', title: 'A', organizer: 'Org', status: 'PUBLISHED' } } })
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.find('[data-testid="quantity-input"]').exists()).toBe(true)
    expect(wrapper.find('.seat-stage').exists()).toBe(false)
    expect(wrapper.text()).toContain('现场演出')
    expect(wrapper.find('img[src="/poster.jpg"]').exists()).toBe(true)
    await wrapper.get('[data-testid="quantity-input"]').setValue(2)
    await wrapper.get('[data-testid="quantity-submit"]').trigger('click')
    expect(JSON.parse(String(fetchMock.mock.calls[2][1]?.body))).toMatchObject({ sessionId: 's1', quantity: 2 })
  })

  it('subtracts existing pending and paid quantity from the purchase limit', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { activity: { id: 'a1' }, sessions: [{ id: 's1', activityId: 'a1', startsAt: '2026-10-01T10:00:00Z', endsAt: '2026-10-01T11:00:00Z', venue: 'Hall', status: 'ONSALE', layoutMode: 'GENERAL_ADMISSION', capacity: 10, remainingCapacity: 8, purchaseLimit: 4 }] } }), { status: 200 }))
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ data: { items: [{ id: 'o1', sessionId: 's1', quantity: 2, status: 'PAID' }], page: 0, size: 100, total: 1, totalPages: 1 } }), { status: 200 }))

    const wrapper = mount(BookingView, { props: { activity: { id: 'a1', title: 'A', organizer: 'Org', status: 'PUBLISHED' } } })
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.find('[data-testid="quantity-input"]').attributes('max')).toBe('2')
    expect(wrapper.text()).toContain('剩余可购 2 张')
  })
})
