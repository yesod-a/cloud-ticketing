import { api } from './auth/authApi'
export type Page<T>={items:T[];page:number;size:number;total:number;totalPages:number}
export type AdminActivity={id:string;title:string;organizer:string;description?:string;status:string;layoutFrozen:boolean}
export const adminActivities=(params:{keyword?:string;status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminActivity>>(`/api/admin/activities?${q}`)}
export const createAdminActivity=(title:string,organizer:string,description='')=>api<AdminActivity>('/api/admin/activities',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,organizer,description})})
export const updateAdminActivity=(id:string,title:string,organizer:string,description='')=>api<AdminActivity>(`/api/admin/activities/${id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,organizer,description})})
export type ActivityImage={id:string;url?:string;imageType?:'COVER'|'DETAIL';sortOrder?:number;contentType?:string}
export const uploadAdminActivityImage=(id:string,file:File,imageType:'COVER'|'DETAIL')=>{const form=new FormData();form.append('file',file);form.append('imageType',imageType);return api<ActivityImage>(`/api/admin/activities/${id}/images`,{method:'POST',body:form})}
export const deleteAdminActivityImage=(activityId:string,imageId:string)=>api<{code:string}>(`/api/admin/activities/${activityId}/images/${imageId}`,{method:'DELETE'})
export const setAdminActivityCover=(activityId:string,imageId:string)=>api<ActivityImage>(`/api/admin/activities/${activityId}/images/${imageId}/cover`,{method:'PUT'})
export const reorderAdminActivityImages=(activityId:string,imageIds:string[])=>api<{code:string}>(`/api/admin/activities/${activityId}/images/order`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({imageIds})})
export const publishAdminActivity=(id:string)=>api<AdminActivity>(`/api/admin/activities/${id}/publish`,{method:'POST'})
export const offlineAdminActivity=(id:string)=>api<AdminActivity>(`/api/admin/activities/${id}/offline`,{method:'POST'})
export const deleteAdminActivity=(id:string)=>api<{code:string}>(`/api/admin/activities/${id}`,{method:'DELETE'})
export type AdminSession={id:string;activityId:string;startsAt:string;endsAt?:string;venue:string;status:string;priceMinor:number;layoutMode?:'GRID'|'ROWS'|'GENERAL_ADMISSION';capacity?:number;purchaseLimit?:number}
export const adminActivitySessions=(activityId:string,params:{status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminSession>>(`/api/admin/activities/${activityId}/sessions?${q}`)}
export const createAdminSession=(activityId:string,body:{venueId:string;startsAt:string;endsAt:string;status?:string;price?:number;layoutMode?:string;capacity?:number;purchaseLimit?:number})=>api<AdminSession>(`/api/admin/activities/${activityId}/sessions`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const updateAdminSession=(activityId:string,sessionId:string,body:{startsAt:string;endsAt:string;status?:string;price?:number;layoutMode?:string;capacity?:number;purchaseLimit?:number})=>api<AdminSession>(`/api/admin/activities/${activityId}/sessions/${sessionId}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const publishAdminSession=(activityId:string,sessionId:string)=>api<AdminSession>(`/api/admin/activities/${activityId}/sessions/${sessionId}/publish`,{method:'POST'})
export const offlineAdminSession=(activityId:string,sessionId:string)=>api<AdminSession>(`/api/admin/activities/${activityId}/sessions/${sessionId}/offline`,{method:'POST'})
export const deleteAdminSession=(activityId:string,sessionId:string)=>api<{code:string}>(`/api/admin/activities/${activityId}/sessions/${sessionId}`,{method:'DELETE'})
export type AdminVenue={id:string;activityId:string|null;name:string;address:string;capacity:number}
export const adminVenuePage=(params:{keyword?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminVenue>>(`/api/admin/venues?${q}`)}
export const adminVenues=(activityId?:string)=>api<{items:AdminVenue[]}>(`/api/admin/venues?activityId=${encodeURIComponent(activityId||'')}`)
export const createAdminVenue=(name:string,address:string)=>api<AdminVenue>('/api/admin/venues',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,address})})
export const updateAdminVenue=(id:string,name:string,address:string)=>api<AdminVenue>(`/api/admin/venues/${id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,address})})
export const deleteAdminVenue=(id:string)=>api<{code:string}>(`/api/admin/venues/${id}`,{method:'DELETE'})
export type AdminVenueSeat={id:string;venueId:string;areaLabel:string;rowLabel:string;seatNumber:number;displayName:string;x:number;y:number;seatType:string;enabled:boolean;status:string}
export const adminVenueSeats=(venueId:string)=>api<{items:AdminVenueSeat[]}>(`/api/admin/venues/${venueId}/seats`)
export const createAdminVenueSeat=(venueId:string,body:{areaLabel?:string;rowLabel:string;seatNumber:number;displayName?:string;x?:number;y?:number;seatType?:string;enabled?:boolean})=>api<AdminVenueSeat>(`/api/admin/venues/${venueId}/seats`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const updateAdminVenueSeat=(venueId:string,seatId:string,body:{areaLabel?:string;rowLabel:string;seatNumber:number;displayName?:string;x?:number;y?:number;seatType?:string;enabled?:boolean;status?:string})=>api<AdminVenueSeat>(`/api/admin/venues/${venueId}/seats/${seatId}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const deleteAdminVenueSeat=(venueId:string,seatId:string)=>api<{code:string}>(`/api/admin/venues/${venueId}/seats/${seatId}`,{method:'DELETE'})
export type VenueLayoutRow={rowLabel:string;seatCount:number;startSeatNumber?:number}
export const generateAdminVenueLayout=(venueId:string,body:{mode:'GRID'|'ROWS';areaLabel?:string;rowCount?:number;seatsPerRow?:number;rowLabelType?:'LETTER'|'NUMBER';startSeatNumber?:number;rows?:VenueLayoutRow[]})=>api<AdminVenueSeat[]>(`/api/admin/venues/${venueId}/layout`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const toLocalIso=(value:string)=>new Date(value).toISOString()
export const assertTimeOrder=(startsAt:string,endsAt:string):boolean=>Boolean(startsAt&&endsAt&&new Date(startsAt).getTime()<new Date(endsAt).getTime())
export type AdminOrder={id:string;userId:string;sessionId:string;seatIds:string;status:string;quantity?:number;ticketNumbers?:string;amountMinor?:number}
export const adminOrders=(params:{status?:string;page?:number;size?:number}={})=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>v!==undefined&&q.set(k,String(v)));return api<Page<AdminOrder>>(`/api/orders/admin?${q}`)}
export type UserOrder={id:string;userId:string;sessionId:string;seatIds:string;quantity?:number;ticketNumbers?:string;status:string;amountMinor:number;createdAt:string;updatedAt:string}
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
