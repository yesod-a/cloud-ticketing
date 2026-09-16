import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginView from '../views/LoginView.vue'
import { register, session } from '../auth/authApi'

describe('registration api', () => {
  beforeEach(() => session.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('registers an email and then logs the account in', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { id: 'u1' } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { accessToken: 'a1', refreshToken: 'r1' } }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(register('person@example.com', 'StrongPass1', '现场观众')).resolves.toMatchObject({ accessToken: 'a1' })
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ phone: null, email: 'person@example.com', password: 'StrongPass1', nickname: '现场观众' })
    expect(session.refreshToken()).toBe('r1')
  })
})

describe('login view registration mode', () => {
  it('shows registration fields after switching mode', async () => {
    const wrapper = mount(LoginView)
    await wrapper.get('[data-testid="register-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="nickname-input"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="submit-auth"]').text()).toContain('注册')
  })
})
