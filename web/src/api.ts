import type { Activity, Seat, Session } from './types'
import { api } from './auth/authApi'
export const getActivity = (id: string) => api<{activity:Activity;sessions:Session[]}>(`/api/activities/${id}`)
export const getSeats = (sessionId: string) => api<Seat[]>(`/api/sessions/${sessionId}/seats`)
export const createOrder = (sessionId: string, seatIds: string[], idempotencyKey: string) => api<Record<string,string>>('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, seatIds: seatIds.join(','), idempotencyKey }) })
export type PaymentIntent = { paymentId?: string; orderId: string; method: string; amountMinor: number; currency: string; status: string; orderStatus?: string; seatCount: number; qrContent?: string; qrCode?: string; expiresAt?: string }
export const createPayment = (orderId: string, method: string) => api<PaymentIntent>(`/api/orders/${orderId}/payments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ method }) })
export const payOrder = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/pay`, { method: 'POST' })
export const getPayment = (orderId: string) => api<PaymentIntent>(`/api/orders/${orderId}/payment`)
