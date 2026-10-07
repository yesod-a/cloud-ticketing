import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AdminLayout from '../views/admin/AdminLayout.vue'
import { session } from '../auth/authApi'

describe('admin console', () => {
  beforeEach(() => { session.set('admin-token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('renders actionable modules instead of static links', async () => {
    const wrapper = mount(AdminLayout, { props: { permissions: ['activity:write', 'order:read', 'inventory:read', 'user:manage', 'audit:read'] } })
    expect(wrapper.get('[data-testid="admin-module-activities"]').attributes('type')).toBe('button')
    expect(wrapper.get('[data-testid="admin-module-orders"]').attributes('type')).toBe('button')
    expect(wrapper.get('[data-testid="admin-module-audit"]').attributes('type')).toBe('button')
  })

  it('shows scope management only for scope managers', () => {
    const wrapper = mount(AdminLayout, { props: { permissions: ['scope:manage'] } })
    expect(wrapper.get('[data-testid="admin-module-scopes"]').attributes('type')).toBe('button')
  })

  it('starts on the first module the current administrator can access', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ data: { items: [], total: 0 } }), { status: 200 }))
    const wrapper = mount(AdminLayout, { props: { permissions: ['order:read'] } })
    await flushPromises()

    expect(wrapper.find('[data-testid="admin-module-activities"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="admin-module-orders"]').exists()).toBe(true)
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/orders/admin?status=&page=0&size=10')
  })

  it('opens activity creation modal and exposes venue configuration controls', async () => {
    const wrapper = mount(AdminLayout, { props: { permissions: ['activity:write', 'session:write', 'venue:write', 'seat-layout:write'] } })
    const createButton = wrapper.findAll('button').find(button => button.text().includes('创建活动'))
    expect(createButton).toBeTruthy()
    await createButton!.trigger('click')
    expect(wrapper.text()).toContain('创建活动')
    expect(wrapper.find('.modal-backdrop').exists()).toBe(true)
  })

  it('exposes detail image ordering controls in the activity editor', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { items: [{ id: 'activity-1', title: 'abc', organizer: 'Org', status: 'OFFLINE' }], total: 1 } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { activity: { id: 'activity-1', title: 'abc', organizer: 'Org', description: '', images: [
        { id: 'detail-1', imageType: 'DETAIL', sortOrder: 0, url: '/one' },
        { id: 'detail-2', imageType: 'DETAIL', sortOrder: 1, url: '/two' }
      ] }, sessions: [] } }), { status: 200 }))
    const wrapper = mount(AdminLayout, { props: { permissions: ['activity:write'] } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.admin-image-row')).toHaveLength(2)
    expect(wrapper.findAll('.admin-image-row button').some(button => button.text() === '上移')).toBe(true)
    expect(wrapper.findAll('.admin-image-row button').some(button => button.text() === '下移')).toBe(true)
  })

  it('keeps the session management table inside a responsive modal container', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({
      data: { items: [{ id: 'activity-1', title: 'abc', organizer: 'Org', status: 'OFFLINE' }], total: 1 }
    }), { status: 200 }))
    const wrapper = mount(AdminLayout, { props: { permissions: ['activity:write', 'session:write'] } })

    await flushPromises()
    const sessionButton = wrapper.findAll('button').find(button => button.text() === '场次')
    expect(sessionButton).toBeTruthy()
    await sessionButton!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-card.wide').exists()).toBe(true)
    expect(wrapper.find('.session-table-wrap').exists()).toBe(true)
  })

  it('configures a capacity-only venue layout without rendering seat rows', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { items: [{ id: 'venue-1', name: '开放场', address: '', capacity: 20 }], total: 1 } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { items: [] } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: [] }), { status: 200 }))

    const wrapper = mount(AdminLayout, { props: { permissions: ['venue:read', 'seat-layout:write'] } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '座位布局')!.trigger('click')

    const layoutMode = wrapper.get('[data-testid="venue-layout-mode"]')
    expect(layoutMode.find('option[value="GENERAL_ADMISSION"]').exists()).toBe(true)
    await layoutMode.setValue('GENERAL_ADMISSION')
    expect(wrapper.find('[data-testid="venue-capacity"]').exists()).toBe(true)
    await wrapper.get('[data-testid="venue-capacity"]').setValue(100)
    await wrapper.get('.layout-form').trigger('submit')
    await flushPromises()

    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[2][1]?.body))).toMatchObject({ mode: 'GENERAL_ADMISSION', capacity: 100 })
    expect(wrapper.find('.seat-preview-row').exists()).toBe(false)
    expect(wrapper.text()).toContain('100 个票号')
    expect(wrapper.find('table').text()).toContain('100')
  })
})
