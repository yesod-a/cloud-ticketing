import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../App.vue'
import LoginView from '../views/LoginView.vue'
import ProfileView from '../views/ProfileView.vue'
import AdminLayout from '../views/admin/AdminLayout.vue'
import { session } from '../auth/authApi'

describe('profile navigation', () => {
  beforeEach(() => {
    session.set('access-token', 'refresh-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { items: [], total: 0 } }), { status: 200 })))
  })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('opens personal center for a signed-in user', async () => {
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()

    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')
    await wrapper.get('[data-testid="profile-nav"]').trigger('click')

    expect(wrapper.findComponent(ProfileView).exists()).toBe(true)
  })

  it('returns to login when the profile requests password-change logout', async () => {
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()
    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')
    await wrapper.get('[data-testid="profile-nav"]').trigger('click')
    wrapper.findComponent(ProfileView).vm.$emit('passwordChanged')
    await flushPromises()

    expect(wrapper.findComponent(LoginView).exists()).toBe(true)
    expect(session.token()).toBeNull()
  })

  it('shows customer actions without exposing the admin entry point', async () => {
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()

    expect(wrapper.get('[data-testid="account-menu-trigger"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="admin-menu-entry"]').exists()).toBe(false)
    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')
    expect(wrapper.get('[data-testid="account-menu"]').text()).toContain('我的订单')
    expect(wrapper.get('[data-testid="account-menu"]').text()).toContain('个人中心')
  })

  it('shows the admin entry only for a token with an admin permission', async () => {
    const payload = btoa(JSON.stringify({ roles: ['OPERATOR'], permissions: ['activity:read'] })).replace(/=/g, '')
    session.set(`x.${payload}.x`, 'refresh-token')
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()
    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')

    expect(wrapper.get('[data-testid="admin-menu-entry"]').text()).toContain('管理后台')
  })

  it('hides customer orders from an administrator account menu', async () => {
    const payload = btoa(JSON.stringify({ roles: ['OPERATOR'], permissions: ['activity:read'] })).replace(/=/g, '')
    session.set(`x.${payload}.x`, 'refresh-token')
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()
    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')

    expect(wrapper.get('[data-testid="account-menu"]').text()).not.toContain('我的订单')
    expect(wrapper.get('[data-testid="account-menu"]').text()).toContain('个人中心')
  })

  it('returns an administrator from the personal center to the admin activities view', async () => {
    const payload = btoa(JSON.stringify({ roles: ['OPERATOR'], permissions: ['activity:read'] })).replace(/=/g, '')
    session.set(`x.${payload}.x`, 'refresh-token')
    const wrapper = mount(App, { global: { stubs: { ProfileView: true, AdminLayout: true, OrdersView: true, BookingView: true, PaymentView: true } } })
    await flushPromises()
    wrapper.findComponent(LoginView).vm.$emit('done')
    await flushPromises()
    await wrapper.get('[data-testid="account-menu-trigger"]').trigger('click')
    await wrapper.get('[data-testid="profile-nav"]').trigger('click')

    expect(wrapper.findComponent(ProfileView).exists()).toBe(true)
    wrapper.findComponent(ProfileView).vm.$emit('back')
    await flushPromises()

    expect(wrapper.findComponent(ProfileView).exists()).toBe(false)
    expect(wrapper.findComponent(AdminLayout).exists()).toBe(true)
  })
})
