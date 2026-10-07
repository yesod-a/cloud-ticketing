import type { Activity, ActivityComment, Coupon, PublicProfile, Seat, Session, UserCoupon } from './types'
import { api } from './auth/authApi'
export type ActivityPage = { items: Activity[]; page: number; size: number; total: number; totalPages: number }
export const listActivities = (params: { keyword?: string; organizer?: string; page?: number; size?: number } = {}) => { const q = new URLSearchParams(); Object.entries(params).forEach(([k, v]) => v !== undefined && q.set(k, String(v))); return api<ActivityPage>(`/api/activities?${q}`) }
export const getActivity = async (id: string) => {
  const result = await api<{activity:Activity;sessions:Session[];coverImageUrl?:string;images?:Activity['images']}>(`/api/activities/${id}`)
  return { ...result, activity: { ...result.activity, coverImageUrl: result.coverImageUrl ?? result.activity.coverImageUrl, images: result.images ?? result.activity.images } }
}
export const getSeats = (sessionId: string) => api<Seat[]>(`/api/sessions/${sessionId}/seats`)
export const createOrder = (sessionId: string, seatIds: string[], idempotencyKey: string, couponId?: string) => api<Record<string,string>>('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, seatIds: seatIds.join(','), idempotencyKey, ...(couponId ? { couponId } : {}) }) })
export const createQuantityOrder = (sessionId: string, quantity: number, idempotencyKey: string, couponId?: string) => api<Record<string,string>>('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, quantity, idempotencyKey, ...(couponId ? { couponId } : {}) }) })
export const createQueuedReservation = (sessionId: string, request: { seatIds?: string[]; quantity?: number }, idempotencyKey: string) => api<Record<string, string>>('/api/queued-orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, ...request, idempotencyKey }) })
export const getQueuedReservation = (reservationId: string, sessionId: string) => api<Record<string, string>>(`/api/queued-orders/${reservationId}?sessionId=${encodeURIComponent(sessionId)}`)
export type PaymentIntent = { paymentId?: string; orderId: string; method: string; amountMinor: number; currency: string; status: string; orderStatus?: string; seatCount: number; quantity?: number; ticketNumbers?: string; qrContent?: string; qrCode?: string; expiresAt?: string }
export const createPayment = (orderId: string, method: string) => api<PaymentIntent>(`/api/orders/${orderId}/payments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ method }) })
export const payOrder = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/pay`, { method: 'POST' })
export const getPayment = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/payment`)
export const getAvailableCoupons = (activityId: string, sessionId: string) => api<{ items: Coupon[] }>(`/api/promotions/coupons/available?activityId=${encodeURIComponent(activityId)}&sessionId=${encodeURIComponent(sessionId)}`)
export const getMyCoupons = (page = 0, size = 20) => api<{ items: UserCoupon[]; page: number; size: number; total: number }>(`/api/promotions/coupons/my?page=${page}&size=${size}`)
export const getComments = (activityId: string, page = 0, size = 10) => api<{ items: ActivityComment[]; page: number; size: number; total: number }>(`/api/activities/${activityId}/comments?page=${page}&size=${size}`)
export const getPublicProfiles = async (ids: string[]) => ids.length === 0 ? [] : api<PublicProfile[]>(`/api/auth/public-profiles?ids=${encodeURIComponent(ids.join(','))}`)
export const postComment = (activityId: string, content: string, parentId?: string) => api<ActivityComment>(`/api/activities/${activityId}/comments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content, ...(parentId ? { parentId } : {}) }) })
export const getCommentReplies = async (commentId: string) => (await api<{ items: ActivityComment[] }>(`/api/comments/${commentId}/replies`)).items
export const replyToComment = (commentId: string, content: string) => api<ActivityComment>(`/api/comments/${commentId}/replies`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) })
export const getCommentLikes = async (commentIds: string[]) => {
  if (commentIds.length === 0) return []
  const ids = encodeURIComponent(commentIds.join(','))
  return (await api<{ likedIds: string[] }>(`/api/comments/likes?ids=${ids}`)).likedIds
}
export const likeComment = (commentId: string, liked: boolean) => api<{ liked: boolean; likeCount: number }>(`/api/comments/${commentId}/like`, { method: liked ? 'POST' : 'DELETE' })
