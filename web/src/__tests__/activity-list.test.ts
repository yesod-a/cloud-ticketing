import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '../App.vue'
import LoginView from '../views/LoginView.vue'
import { session } from '../auth/authApi'
import { listActivities } from '../api'

const page = {
  items: [{ id: 'a1', title: '星河现场 · 城市之声', organizer: '星河文化', status: 'PUBLISHED' }],
  page: 0, size: 12, total: 1, totalPages: 1,
}

describe('public activity list', () => {
  beforeEach(() => { session.set('token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('sends keyword, organizer, page and size to the paginated endpoint', async () => {
    ;(fetch as any).mockResolvedValueOnce(new Response(JSON.stringify({ ...page, total: 30, totalPages: 3 }), { status: 200 }))

    const result = await listActivities({ keyword: '星河', organizer: '星', page: 1, size: 12 })

    const url = String((fetch as any).mock.calls[0][0])
    expect(url).toContain('/api/activities?')
    expect(url).toContain('keyword=%E6%98%9F%E6%B2%B3')
    expect(url).toContain('organizer=%E6%98%9F')
    expect(url).toContain('page=1')
    expect(url).toContain('size=12')
    expect(result.totalPages).toBe(3)
  })

  it('renders filters, activity cards and pagination on the user page', async () => {
    ;(fetch as any).mockImplementation(() => Promise.resolve(new Response(JSON.stringify(page), { status: 200 })))
    const wrapper = mount(App)
    wrapper.findComponent(LoginView).vm.$emit('done')
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.find('[data-testid="filter-keyword"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-organizer"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('星河现场 · 城市之声')
    expect(wrapper.text()).toContain('共 1 条')
    expect(wrapper.find('.activity-card').exists()).toBe(true)
  })
})
