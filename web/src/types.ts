export type SeatStatus = 'AVAILABLE' | 'SELECTED' | 'LOCKED' | 'SOLD' | 'available' | 'selected' | 'locked' | 'sold'
export interface Seat { id: string; row: string; number: number; status: SeatStatus; areaLabel?: string; displayName?: string; x?: number; y?: number; type?: string }
export interface Activity { id: string; title: string; organizer: string; status: string }
export interface Session { id: string; activityId: string; startsAt: string; endsAt?: string; venue: string; status: string }
