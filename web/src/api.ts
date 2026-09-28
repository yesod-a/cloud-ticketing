import type { Activity, Seat, Session } from './types'
import { api } from './auth/authApi'
export type ActivityPage = { items: Activity[]; page: number; size: number; total: number; totalPages: number }
export const listActivities = (params: { keyword?: string; organizer?: string; page?: number; size?: number } = {}) => { const q = new URLSearchParams(); Object.entries(params).forEach(([k, v]) => v !== undefined && q.set(k, String(v))); return api<ActivityPage>(`/api/activities?${q}`) }
export const getActivity = async (id: string) => {
  const result = await api<{activity:Activity;sessions:Session[];coverImageUrl?:string;images?:Activity['images']}>(`/api/activities/${id}`)
  return { ...result, activity: { ...result.activity, coverImageUrl: result.coverImageUrl ?? result.activity.coverImageUrl, images: result.images ?? result.activity.images } }
}
export const getSeats = (sessionId: string) => api<Seat[]>(`/api/sessions/${sessionId}/seats`)
export const createOrder = (sessionId: string, seatIds: string[], idempotencyKey: string) => api<Record<string,string>>('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, seatIds: seatIds.join(','), idempotencyKey }) })
export const createQuantityOrder = (sessionId: string, quantity: number, idempotencyKey: string) => api<Record<string,string>>('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, quantity, idempotencyKey }) })
export type PaymentIntent = { paymentId?: string; orderId: string; method: string; amountMinor: number; currency: string; status: string; orderStatus?: string; seatCount: number; quantity?: number; ticketNumbers?: string; qrContent?: string; qrCode?: string; expiresAt?: string }
export const createPayment = (orderId: string, method: string) => api<PaymentIntent>(`/api/orders/${orderId}/payments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ method }) })
export const payOrder = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/pay`, { method: 'POST' })
export const getPayment = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/payment`)
