import { api } from './auth/authApi'
export type Page<T>={items:T[];page:number;size:number;total:number;totalPages:number}
export type AdminActivity={id:string;title:string;organizer:string;status:string;layoutFrozen:boolean}
export const adminActivities=(params:{keyword?:string;status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminActivity>>(`/api/admin/activities?${q}`)}
export const createAdminActivity=(title:string,organizer:string)=>api<AdminActivity>('/api/admin/activities',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,organizer})})
export const updateAdminActivity=(id:string,title:string,organizer:string)=>api<AdminActivity>(`/api/admin/activities/${id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,organizer})})
export const publishAdminActivity=(id:string)=>api<AdminActivity>(`/api/admin/activities/${id}/publish`,{method:'POST'})
export const offlineAdminActivity=(id:string)=>api<AdminActivity>(`/api/admin/activities/${id}/offline`,{method:'POST'})
export type AdminSession={id:string;activityId:string;startsAt:string;endsAt?:string;venue:string;status:string}
export const adminActivitySessions=(activityId:string,params:{status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminSession>>(`/api/admin/activities/${activityId}/sessions?${q}`)}
export const createAdminSession=(activityId:string,body:{venueId:string;startsAt:string;endsAt:string;status?:string})=>api<AdminSession>(`/api/admin/activities/${activityId}/sessions`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export type AdminVenue={id:string;activityId:string;name:string;address:string;capacity:number}
export const adminVenues=(activityId?:string)=>api<{items:AdminVenue[]}>(`/api/admin/venues?activityId=${encodeURIComponent(activityId||'')}`)
export type AdminVenueSeat={id:string;venueId:string;rowLabel:string;seatNumber:number;position:string;status:string}
export const createAdminVenue=(activityId:string,name:string,address:string,capacity:number)=>api<AdminVenue>('/api/admin/venues',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({activityId,name,address,capacity})})
export const adminVenueSeats=(venueId:string)=>api<{items:AdminVenueSeat[]}>(`/api/admin/venues/${venueId}/seats`)
export const createAdminVenueSeat=(venueId:string,body:{rowLabel:string;seatNumber:number;position?:string;status?:string})=>api<AdminVenueSeat>(`/api/admin/venues/${venueId}/seats`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const updateAdminVenueSeat=(venueId:string,seatId:string,body:{rowLabel:string;seatNumber:number;position?:string;status?:string})=>api<AdminVenueSeat>(`/api/admin/venues/${venueId}/seats/${seatId}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const deleteAdminVenueSeat=(venueId:string,seatId:string)=>api<{code:string}>(`/api/admin/venues/${venueId}/seats/${seatId}`,{method:'DELETE'})
export type AdminOrder={id:string;userId:string;sessionId:string;seatIds:string;status:string}
export const adminOrders=(params:{status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminOrder>>(`/api/orders/admin?${q}`)}
export type UserOrder={id:string;userId:string;sessionId:string;seatIds:string;status:string;createdAt:string;updatedAt:string}
export const myOrders=(params:{page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<UserOrder>>(`/api/orders/me?${q}`)}
export const cancelOrder=(id:string)=>api<UserOrder>(`/api/orders/${id}/cancel`,{method:'POST'})
export const requestOrderRefund=(id:string,reason:string)=>api<RefundRequest>(`/api/orders/${id}/refund`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason})})
export type RefundRequest={id:string;orderId:string;userId:string;reason:string;status:string;reviewedBy:string;createdAt:string}
export const adminRefunds=(params:{status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<RefundRequest>>(`/api/orders/admin/refunds?${q}`)}
export const reviewRefund=(id:string,decision:'approve'|'reject')=>api<RefundRequest>(`/api/orders/admin/refunds/${id}/${decision}`,{method:'POST'})
export type AdminUser={id:string;phone:string;email:string;nickname:string;status:string;createdAt:string}
export const adminUsers=(params:{keyword?:string;status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminUser>>(`/api/admin/auth/users?${q}`)}
export type Audit={id:string;actor:string;action:string;resourceType:string;resourceId:string;traceId:string;createdAt:string}
export const adminAudits=(params:{action?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<Audit>>(`/api/admin/auth/audits?${q}`)}
export const updateUserStatus=(id:string,status:string)=>api<{id:string;status:string}>(`/api/admin/auth/users/${id}/status`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status})})
export const grantUserRole=(id:string,roleCode:string)=>api<{id:string;roleCode:string}>(`/api/admin/auth/users/${id}/role`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({roleCode})})
export type AdminRole={id:string;code:string;name:string;status:string}
export const adminRoles=()=>api<{items:AdminRole[]}>('/api/admin/auth/roles')
export type AdminScope={id:string;resourceType:string;resourceId:string;status:string;userCount:number;createdAt:string}
export const adminScopes=(params:{resourceType?:string;status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminScope>>(`/api/admin/auth/scopes?${q}`)}
export const createAdminScope=(resourceType:string,resourceId:string)=>api<AdminScope>('/api/admin/auth/scopes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({resourceType,resourceId})})
export const bindAdminScope=(scopeId:string,userId:string)=>api<{scopeId:string;userId:string;status:string}>(`/api/admin/auth/scopes/${scopeId}/users/${userId}`,{method:'PUT'})
export const unbindAdminScope=(scopeId:string,userId:string)=>api<{scopeId:string;userId:string;status:string}>(`/api/admin/auth/scopes/${scopeId}/users/${userId}`,{method:'DELETE'})
export type InventorySeat={id:string;sessionId:string;row:string;number:number;status:string;updatedAt:string}
export const adminInventory=(params:{sessionId?:string;status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<InventorySeat>>(`/api/admin/inventory?${q}`)}
export const updateInventorySeat=(id:string,status:string,reason:string)=>api<{id:string;status:string}>(`/api/admin/inventory/seats/${id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,reason})})
