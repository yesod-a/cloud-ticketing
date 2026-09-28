export type SeatStatus = 'AVAILABLE' | 'SELECTED' | 'LOCKED' | 'SOLD' | 'available' | 'selected' | 'locked' | 'sold'
export interface Seat { id: string; row: string; number: number; status: SeatStatus; areaLabel?: string; displayName?: string; x?: number; y?: number; type?: string }
export interface Activity { id: string; title: string; organizer: string; description?: string; coverImageUrl?: string; images?: ActivityImage[]; status: string }
export interface ActivityImage { id: string; url?: string; imageType?: 'COVER'|'DETAIL'; sortOrder?: number }
export interface Session { id: string; activityId: string; startsAt: string; endsAt?: string; venue: string; status: string; priceMinor?: number; layoutMode?: 'GRID'|'ROWS'|'GENERAL_ADMISSION'; capacity?: number; remainingCapacity?: number; purchaseLimit?: number; remainingPurchaseLimit?: number }
