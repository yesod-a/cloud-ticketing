import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ActivityComments from '../components/ActivityComments.vue'
import { session } from '../auth/authApi'

const api = vi.hoisted(() => ({
  getComments: vi.fn(),
  getCommentLikes: vi.fn(),
  getPublicProfiles: vi.fn(),
  getCommentReplies: vi.fn(),
  likeComment: vi.fn(),
  postComment: vi.fn(),
  replyToComment: vi.fn(),
}))

vi.mock('../api', () => api)

describe('ActivityComments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    session.set('test-token')
    api.getComments.mockResolvedValue({ items: [], total: 0 })
    api.getCommentLikes.mockResolvedValue([])
    api.getPublicProfiles.mockResolvedValue([])
    api.postComment.mockResolvedValue({ id: 'new-comment' })
  })

  afterEach(() => session.clear())

  it('keeps the comment form available when the activity has no comments', async () => {
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="comment-input"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('还没有评论')
  })

  it('posts a new comment from the empty state', async () => {
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="comment-input"]').setValue('还没买票也能评论')
    await wrapper.get('[data-testid="comment-submit"]').trigger('submit')
    await flushPromises()

    expect(api.postComment).toHaveBeenCalledWith('activity-1', '还没买票也能评论')
    expect(wrapper.get('[data-testid="comment-input"]').element.value).toBe('')
  })

  it('loads and posts a reply under the selected comment', async () => {
    api.getComments.mockResolvedValue({ items: [{ id: 'comment-1', userId: 'user-1', content: '现场很棒', likeCount: 0, replyCount: 0 }], total: 1 })
    api.getCommentReplies.mockResolvedValue([])
    api.replyToComment.mockResolvedValue({ id: 'reply-1' })
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('.comment-row > .comment-actions .comment-action')[1].trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="回复 user-1"]').setValue('同意')
    await wrapper.get('.reply-form').trigger('submit')
    await flushPromises()

    expect(api.replyToComment).toHaveBeenCalledWith('comment-1', '同意')
    expect(wrapper.text()).toContain('回复 · 1')
  })

  it('keeps reply text and reports an error when the reply request fails', async () => {
    api.getComments.mockResolvedValue({ items: [{ id: 'comment-1', userId: 'user-1', content: '现场很棒', likeCount: 0, replyCount: 0 }], total: 1 })
    api.getCommentReplies.mockResolvedValue([])
    api.replyToComment.mockRejectedValue(new Error('REQUEST_500'))
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('.comment-row > .comment-actions .comment-action')[1].trigger('click')
    await flushPromises()
    const input = wrapper.get('[aria-label="回复 user-1"]')
    await input.setValue('请保留这段回复')
    await wrapper.get('.reply-form').trigger('submit')
    await flushPromises()

    expect(input.element.value).toBe('请保留这段回复')
    expect(wrapper.get('[role="alert"]').text()).toContain('回复评论失败')
  })

  it('shows commenter nickname and avatar from public profiles', async () => {
    api.getComments.mockResolvedValue({ items: [{ id: 'comment-1', userId: 'user-1', content: '现场很棒', likeCount: 0, replyCount: 0 }], total: 1 })
    api.getPublicProfiles.mockResolvedValue([{ id: 'user-1', nickname: '小云', avatarUrl: '/avatar.png' }])
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.comment-author strong').text()).toBe('小云')
    expect(wrapper.get('.comment-avatar').attributes('src')).toBe('/avatar.png')
  })

  it('renders ten comments per page and pages to the next ten', async () => {
    const rows = Array.from({ length: 10 }, (_, index) => ({ id: `comment-${index}`, userId: `user-${index}`, content: '体验', likeCount: 0, replyCount: 0 }))
    api.getComments.mockResolvedValueOnce({ items: rows, total: 11 }).mockResolvedValueOnce({ items: [{ id: 'comment-10', userId: 'user-10', content: '体验', likeCount: 0, replyCount: 0 }], total: 11 })
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.comment-row')).toHaveLength(10)

    await wrapper.get('[data-testid="next-comment-page"]').trigger('click')
    await flushPromises()

    expect(api.getComments).toHaveBeenLastCalledWith('activity-1', 1, 10)
    expect(wrapper.findAll('.comment-row')).toHaveLength(1)
  })

  it('shows ten replies initially and expands the full reply list without a scroll container', async () => {
    const replies = Array.from({ length: 11 }, (_, index) => ({ id: `reply-${index}`, userId: `user-${index}`, content: `回复${index}`, likeCount: 0, replyCount: 0 }))
    api.getComments.mockResolvedValue({ items: [{ id: 'comment-1', userId: 'user-1', content: '现场很棒', likeCount: 0, replyCount: 11 }], total: 1 })
    api.getCommentReplies.mockResolvedValue(replies)
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('.comment-row > .comment-actions .comment-action')[1].trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.reply-row')).toHaveLength(10)
    await wrapper.get('[data-testid="expand-replies"]').trigger('click')
    expect(wrapper.findAll('.reply-row')).toHaveLength(11)
    expect(wrapper.find('.reply-thread').attributes('style') ?? '').not.toMatch(/overflow/)
  })

  it('restores and toggles the persisted liked state', async () => {
    api.getComments.mockResolvedValue({ items: [{ id: 'comment-1', userId: 'user-1', content: '现场很棒', likeCount: 1, replyCount: 0 }], total: 1 })
    api.getCommentLikes.mockResolvedValue(['comment-1'])
    api.likeComment.mockResolvedValue({ liked: false, likeCount: 0 })
    const wrapper = mount(ActivityComments, { props: { activityId: 'activity-1' } })

    await wrapper.get('[data-testid="load-comments"]').trigger('click')
    await flushPromises()
    const like = wrapper.find('.comment-row > .comment-actions .comment-action')
    expect(like.attributes('aria-pressed')).toBe('true')
    await like.trigger('click')
    await flushPromises()

    expect(api.likeComment).toHaveBeenCalledWith('comment-1', false)
    expect(like.attributes('aria-pressed')).toBe('false')
  })
})
