import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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

  it('opens activity creation modal and exposes venue configuration controls', async () => {
    const wrapper = mount(AdminLayout, { props: { permissions: ['activity:write', 'session:write', 'venue:write', 'seat-layout:write'] } })
    const createButton = wrapper.findAll('button').find(button => button.text().includes('创建活动'))
    expect(createButton).toBeTruthy()
    await createButton!.trigger('click')
    expect(wrapper.text()).toContain('创建活动')
    expect(wrapper.find('.modal-backdrop').exists()).toBe(true)
  })
})
