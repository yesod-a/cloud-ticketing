import type { Activity, Seat } from './types'
const json = async <T>(input: RequestInfo, init?: RequestInit): Promise<T> => { const res = await fetch(input, init); if (!res.ok) throw new Error(`REQUEST_${res.status}`); return res.json() }
export const getActivity = (id: string) => json<Activity>(`/api/activities/${id}`)
export const getSeats = (sessionId: string) => json<Seat[]>(`/api/sessions/${sessionId}/seats`)
export const createOrder = (sessionId: string, seatIds: string[], idempotencyKey: string) => json<{ orderId: string; status: string; expiresAt: string }>(`/api/orders`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Trace-Id': idempotencyKey }, body: JSON.stringify({ sessionId, seatIds, idempotencyKey }) })
