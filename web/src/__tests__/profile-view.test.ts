import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ProfileView from '../views/ProfileView.vue'
import { session } from '../auth/authApi'

const profile = { id: 'u1', phone: '13800138000', email: 'person@example.com', nickname: '现场观众', status: 'ACTIVE', avatarUrl: null, createdAt: '2026-09-27T12:00:00Z' }

describe('profile view', () => {
  beforeEach(() => { session.set('access-token', 'refresh-token') })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('renders account details and initials when no avatar exists', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: profile }), { status: 200 })))
    const wrapper = mount(ProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('现场观众')
    expect(wrapper.find('.profile-readonly input').element.value).toBe('13800138000')
    expect(wrapper.get('[data-testid="avatar-fallback"]').text()).toBe('现')
  })

  it('saves a trimmed nickname and refreshes the profile', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: profile }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { ...profile, nickname: '新昵称' } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ProfileView)
    await flushPromises()
    await wrapper.get('[data-testid="nickname-input"]').setValue('  新昵称  ')
    await wrapper.get('.profile-form').trigger('submit')
    await flushPromises()
    await flushPromises()

    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ nickname: '新昵称' })
    expect(wrapper.text()).toContain('资料已更新')
  })

  it('emits passwordChanged after the password request succeeds', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: profile }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: null }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ProfileView)
    await flushPromises()
    await wrapper.get('[data-testid="current-password"]').setValue('OldPass1')
    await wrapper.get('[data-testid="new-password"]').setValue('NewPass1')
    await wrapper.findAll('.profile-form')[1].trigger('submit')
    await flushPromises()
    await flushPromises()

    expect(wrapper.emitted('passwordChanged')).toHaveLength(1)
    expect(session.token()).toBeNull()
  })

  it('uploads a selected avatar after client-side validation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: profile }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { ...profile, avatarUrl: '/api/auth/avatars/u1' } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ProfileView)
    await flushPromises()
    const file = new File(['image'], 'avatar.png', { type: 'image/png' })
    const input = wrapper.get('[data-testid="avatar-input"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await wrapper.get('[data-testid="upload-avatar"]').trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/me/avatar')
    expect(fetchMock.mock.calls[1][1].body).toBeInstanceOf(FormData)
  })
})
