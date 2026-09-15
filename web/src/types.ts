export type SeatStatus = 'available' | 'selected' | 'locked' | 'sold'
export interface Seat { seatId: string; row: number; number: number; status: SeatStatus }
export interface Activity { activityId: string; title: string; venue: string; date: string; sessions: { sessionId: string; label: string }[] }
