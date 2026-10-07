import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCommentLikes, getCommentReplies, getPublicProfiles, replyToComment } from '../api'
import { session } from '../auth/authApi'

describe('comment api', () => {
  beforeEach(() => {
    session.set('access-token')
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    session.clear()
    vi.unstubAllGlobals()
  })

  it('loads replies for a comment', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: 'reply-1' }] }), { status: 200 }))

    await expect(getCommentReplies('comment-1')).resolves.toEqual([{ id: 'reply-1' }])
    expect(fetch).toHaveBeenCalledWith('/api/comments/comment-1/replies', expect.objectContaining({ credentials: 'include' }))
  })

  it('loads the current user liked state for a page of comments in one request', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ likedIds: ['comment-2'] }), { status: 200 }))

    await expect(getCommentLikes(['comment-1', 'comment-2'])).resolves.toEqual(['comment-2'])
    expect(fetch).toHaveBeenCalledWith('/api/comments/likes?ids=comment-1%2Ccomment-2', expect.objectContaining({ credentials: 'include' }))
  })

  it('posts a reply to the selected comment', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ id: 'reply-1' }), { status: 200 }))

    await expect(replyToComment('comment-1', '现场很棒')).resolves.toMatchObject({ id: 'reply-1' })
    expect(fetch).toHaveBeenCalledWith('/api/comments/comment-1/replies', expect.objectContaining({ method: 'POST' }))
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual({ content: '现场很棒' })
  })

  it('loads only public nickname and avatar details for a batch of users', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ data: [{ id: 'user-1', nickname: '小云', avatarUrl: '/avatar.png' }] }), { status: 200 }))

    await expect(getPublicProfiles(['user-1'])).resolves.toEqual([{ id: 'user-1', nickname: '小云', avatarUrl: '/avatar.png' }])
    expect(fetch).toHaveBeenCalledWith('/api/auth/public-profiles?ids=user-1', expect.objectContaining({ credentials: 'include' }))
  })
})
