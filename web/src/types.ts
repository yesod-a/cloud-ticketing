export type SeatStatus = 'AVAILABLE' | 'SELECTED' | 'LOCKED' | 'SOLD' | 'available' | 'selected' | 'locked' | 'sold'
export interface Seat { id: string; row: string; number: number; status: SeatStatus; areaLabel?: string; displayName?: string; x?: number; y?: number; type?: string }
export interface Activity { id: string; title: string; organizer: string; description?: string; coverImageUrl?: string; images?: ActivityImage[]; status: string }
export interface ActivityImage { id: string; url?: string; imageType?: 'COVER'|'DETAIL'; sortOrder?: number }
export interface Session { id: string; activityId: string; startsAt: string; endsAt?: string; venue: string; status: string; priceMinor?: number; layoutMode?: 'GRID'|'ROWS'|'GENERAL_ADMISSION'; saleMode?: 'DIRECT'|'QUEUED'; capacity?: number; remainingCapacity?: number; purchaseLimit?: number; remainingPurchaseLimit?: number }
export interface Coupon { id: string; name: string; discountType: string; thresholdAmountMinor: number; discountValue: number; maxDiscountMinor?: number; status?: string }
export interface UserCoupon { id: string; couponId: string; status: string; termBeginAt?: string; termEndAt?: string }
export interface ActivityComment { id: string; activityId: string; userId: string; parentId?: string; content: string; status: string; likeCount: number; replyCount: number; createdAt?: string; updatedAt?: string; nickname?: string; avatarUrl?: string | null }
export interface PublicProfile { id: string; nickname?: string | null; avatarUrl?: string | null }
