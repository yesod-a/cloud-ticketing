<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ChevronDown, ChevronUp, MessageCircle, Send, ThumbsUp } from 'lucide-vue-next'
import { getCommentLikes, getCommentReplies, getComments, getPublicProfiles, likeComment, postComment, replyToComment } from '../api'
import { session } from '../auth/authApi'
import type { ActivityComment } from '../types'

const props = defineProps<{ activityId: string }>()
const comments = ref<ActivityComment[]>([])
const page = ref(0)
const total = ref(0)
const text = ref('')
const loaded = ref(false)
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const likedIds = ref<Set<string>>(new Set())
const pendingLikes = ref<Set<string>>(new Set())
type ReplyState = { open: boolean; loaded: boolean; loading: boolean; sending: boolean; items: ActivityComment[]; text: string; showAll: boolean }
const replyStates = reactive<Record<string, ReplyState>>({})
const profiles = reactive<Record<string, { nickname?: string | null; avatarUrl?: string | null }>>({})
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 10)))

function failureMessage(reason: unknown, action: string) {
  return String(reason).includes('REQUEST_401') ? `登录后即可${action}。` : `${action}失败，请稍后重试。`
}

async function loadLikes(rows: ActivityComment[]) {
  if (!session.token() || rows.length === 0) return
  try {
    const ids = await getCommentLikes(rows.map(row => row.id))
    likedIds.value = new Set([...likedIds.value, ...ids])
  } catch {
    // The comment list remains usable when the optional viewer state is unavailable.
  }
}

async function loadProfiles(rows: ActivityComment[]) {
  const ids = [...new Set(rows.map(row => row.userId).filter(Boolean))]
  if (ids.length === 0) return
  try {
    const result = await getPublicProfiles(ids)
    result.forEach(profile => { profiles[profile.id] = profile })
  } catch {
    // Profile enrichment is optional; comments remain readable with the user id fallback.
  }
}

function displayName(row: ActivityComment) {
  return profiles[row.userId]?.nickname?.trim() || row.nickname?.trim() || row.userId.slice(0, 8)
}

function avatarUrl(row: ActivityComment) {
  return profiles[row.userId]?.avatarUrl || row.avatarUrl || ''
}

function visibleReplies(state: ReplyState) {
  return state.showAll ? state.items : state.items.slice(0, 10)
}

async function loadComments() {
  loading.value = true
  error.value = ''
  try {
    const result = await getComments(props.activityId, page.value, 10)
    comments.value = result.items
    total.value = result.total
    loaded.value = true
    await Promise.all([loadLikes(result.items), loadProfiles(result.items)])
  } catch {
    error.value = '评论加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function sendComment() {
  const content = text.value.trim()
  if (!content || sending.value) return
  sending.value = true
  error.value = ''
  try {
    await postComment(props.activityId, content)
    text.value = ''
    page.value = 0
    await loadComments()
  } catch (reason) {
    error.value = failureMessage(reason, '发表评论')
  } finally {
    sending.value = false
  }
}

function stateFor(id: string): ReplyState {
  return replyStates[id] ??= { open: false, loaded: false, loading: false, sending: false, items: [], text: '', showAll: false }
}

async function toggleReplies(row: ActivityComment) {
  const state = stateFor(row.id)
  state.open = !state.open
  if (!state.open || state.loaded || state.loading) return
  state.loading = true
  error.value = ''
  try {
    state.items = await getCommentReplies(row.id)
    state.loaded = true
    await Promise.all([loadLikes(state.items), loadProfiles(state.items)])
  } catch {
    error.value = '回复加载失败，请稍后重试。'
  } finally {
    state.loading = false
  }
}

async function sendReply(row: ActivityComment) {
  const state = stateFor(row.id)
  const content = state.text.trim()
  if (!content || state.sending) return
  state.sending = true
  error.value = ''
  try {
    const created = await replyToComment(row.id, content)
    state.text = ''
    row.replyCount = (row.replyCount ?? 0) + 1
    try {
      state.items = await getCommentReplies(row.id)
    } catch {
      state.items = [...state.items, created]
    }
    state.loaded = true
    await Promise.all([loadLikes(state.items), loadProfiles(state.items)])
  } catch (reason) {
    error.value = failureMessage(reason, '回复评论')
  } finally {
    state.sending = false
  }
}

async function toggleLike(row: ActivityComment) {
  if (pendingLikes.value.has(row.id)) return
  pendingLikes.value = new Set([...pendingLikes.value, row.id])
  const liked = likedIds.value.has(row.id)
  try {
    const result = await likeComment(row.id, !liked)
    row.likeCount = result.likeCount
    const next = new Set(likedIds.value)
    result.liked ? next.add(row.id) : next.delete(row.id)
    likedIds.value = next
  } catch (reason) {
    error.value = failureMessage(reason, '点赞')
  } finally {
    const next = new Set(pendingLikes.value)
    next.delete(row.id)
    pendingLikes.value = next
  }
}
</script>

<template>
  <section class="comment-section" aria-labelledby="comments-title">
    <div class="profile-card-title">
      <div><p class="eyebrow">COMMUNITY</p><h2 id="comments-title">活动评论</h2></div>
      <span class="muted">{{ total }} 条</span>
    </div>

    <button v-if="!loaded && !loading" class="secondary-btn" type="button" data-testid="load-comments" @click="loadComments">
      <MessageCircle :size="16" /> 查看评论并参与讨论
    </button>
    <p v-if="loading" class="muted" role="status">正在加载评论...</p>
    <p v-if="error" class="alert" role="alert">{{ error }}</p>

    <template v-if="loaded">
      <form class="comment-form" @submit.prevent="sendComment">
        <label class="muted-label" for="new-comment">写下你的现场体验</label>
        <textarea id="new-comment" v-model="text" maxlength="1000" placeholder="分享你的看法" data-testid="comment-input"></textarea>
        <div class="comment-compose-footer">
          <span class="muted">{{ text.length }}/1000</span>
          <button class="primary-btn compact" type="submit" data-testid="comment-submit" :disabled="!text.trim() || sending">
            <Send :size="15" /> {{ sending ? '发布中...' : '发表评论' }}
          </button>
        </div>
      </form>

      <p v-if="!comments.length" class="comment-empty">还没有评论，来发表第一条吧。</p>
      <article v-for="row in comments" :key="row.id" class="comment-row">
        <div class="comment-author">
          <img v-if="avatarUrl(row)" class="comment-avatar" :src="avatarUrl(row)" :alt="`${displayName(row)}的头像`" />
          <span v-else class="comment-avatar-fallback" aria-hidden="true">{{ displayName(row).slice(0, 1) }}</span>
          <strong>{{ displayName(row) }}</strong>
          <small>{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '' }}</small>
        </div>
        <p class="comment-content">{{ row.content }}</p>
        <div class="comment-actions">
          <button class="comment-action" :class="{ active: likedIds.has(row.id) }" type="button" :aria-pressed="likedIds.has(row.id)" :title="likedIds.has(row.id) ? '取消点赞' : '点赞'" @click="toggleLike(row)">
            <ThumbsUp :size="15" /> {{ row.likeCount ?? 0 }}
          </button>
          <button class="comment-action" type="button" :aria-expanded="stateFor(row.id).open" @click="toggleReplies(row)">
            <component :is="stateFor(row.id).open ? ChevronUp : ChevronDown" :size="15" />
            {{ stateFor(row.id).open ? '收起回复' : '回复' }} · {{ row.replyCount ?? 0 }}
          </button>
        </div>

        <div v-if="stateFor(row.id).open" class="reply-thread">
          <p v-if="stateFor(row.id).loading" class="muted">正在加载回复...</p>
          <p v-else-if="stateFor(row.id).loaded && !stateFor(row.id).items.length" class="muted">还没有回复</p>
          <div v-for="reply in visibleReplies(stateFor(row.id))" :key="reply.id" class="reply-row">
            <div class="comment-author">
              <img v-if="avatarUrl(reply)" class="comment-avatar" :src="avatarUrl(reply)" :alt="`${displayName(reply)}的头像`" />
              <span v-else class="comment-avatar-fallback" aria-hidden="true">{{ displayName(reply).slice(0, 1) }}</span>
              <strong>{{ displayName(reply) }}</strong>
              <small>{{ reply.createdAt ? new Date(reply.createdAt).toLocaleString() : '' }}</small>
            </div>
            <p class="comment-content">{{ reply.content }}</p>
            <button class="comment-action" :class="{ active: likedIds.has(reply.id) }" type="button" :aria-pressed="likedIds.has(reply.id)" :title="likedIds.has(reply.id) ? '取消点赞' : '点赞'" @click="toggleLike(reply)">
              <ThumbsUp :size="15" /> {{ reply.likeCount ?? 0 }}
            </button>
          </div>
          <button v-if="stateFor(row.id).items.length > 10 && !stateFor(row.id).showAll" data-testid="expand-replies" class="comment-action" type="button" @click="stateFor(row.id).showAll = true">展开全部回复（{{ stateFor(row.id).items.length }}）</button>
          <form class="reply-form" @submit.prevent="sendReply(row)">
            <textarea v-model="stateFor(row.id).text" maxlength="1000" placeholder="写下你的回复" :aria-label="`回复 ${row.userId.slice(0, 8)}`"></textarea>
            <button class="secondary-btn" type="submit" :disabled="!stateFor(row.id).text.trim() || stateFor(row.id).sending">
              <Send :size="14" /> 回复
            </button>
          </form>
        </div>
      </article>

      <div v-if="pageCount > 1" class="pagination">
        <button class="secondary-btn" type="button" :disabled="page === 0 || loading" @click="page--; loadComments()">上一页</button>
        <span class="muted">{{ page + 1 }} / {{ pageCount }}</span>
        <button data-testid="next-comment-page" class="secondary-btn" type="button" :disabled="page + 1 >= pageCount || loading" @click="page++; loadComments()">下一页</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.comment-section{margin-top:28px;padding-top:22px;border-top:1px solid #253044}.comment-form{display:grid;gap:10px;margin:18px 0 12px}.comment-form textarea,.reply-form textarea{min-height:88px;width:100%;background:#111927;border:1px solid #31405a;border-radius:8px;color:#dce5f1;padding:10px;resize:vertical}.comment-compose-footer{display:flex;align-items:center;justify-content:space-between}.comment-empty{padding:18px 0;color:#98a6ba}.comment-row{padding:16px 0;border-bottom:1px solid #1f2a3a}.comment-author{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.comment-author strong{font-size:12px}.comment-author small{color:#77859a}.comment-avatar,.comment-avatar-fallback{width:32px;height:32px;flex:none;border-radius:50%;object-fit:cover;background:#253b4a;color:#8de4ca;display:grid;place-items:center;font-size:13px}.comment-content{margin:8px 0;color:#dce5f1;white-space:pre-wrap;overflow-wrap:anywhere}.comment-actions{display:flex;gap:16px;flex-wrap:wrap}.comment-action{display:inline-flex;align-items:center;gap:6px;padding:5px 0;border:0;background:transparent;color:#9eacc0;cursor:pointer}.comment-action:hover,.comment-action.active{color:#50d2b1}.reply-thread{margin:14px 0 0 12px;padding:2px 0 2px 14px;border-left:2px solid #2b3a50}.reply-row{padding:10px 0}.reply-form{display:flex;align-items:flex-end;gap:10px;margin-top:12px}.reply-form textarea{min-height:64px;flex:1;min-width:0}.reply-form button{width:auto;min-width:72px;height:40px;flex:none;margin:0;padding:0 14px}.comment-compose-footer button{display:inline-flex;align-items:center;justify-content:center;gap:7px}.pagination{display:flex;align-items:center;justify-content:center;gap:16px;margin-top:18px}
</style>
