import type { TokenClaims } from './tokenClaims'

export type NavigationItem = {
  id: string
  label: string
  permission?: string
}

export const userMenuItems: NavigationItem[] = [
  { id: 'home', label: '活动首页' },
  { id: 'orders', label: '我的订单' },
  { id: 'profile', label: '个人中心' }
]

export const adminMenuItems: NavigationItem[] = [
  { id: 'activities', label: '活动管理', permission: 'activity:read' },
  { id: 'venues', label: '场馆与库存', permission: 'venue:read' },
  { id: 'orders', label: '订单管理', permission: 'order:read' },
  { id: 'refunds', label: '退款审核', permission: 'order:refund' },
  { id: 'inventory', label: '库存管理', permission: 'inventory:read' },
  { id: 'users', label: '用户与角色', permission: 'user:read' },
  { id: 'scopes', label: '资源 Scope', permission: 'scope:manage' },
  { id: 'audit', label: '审计日志', permission: 'audit:read' }
  ,{ id: 'promotions', label: '优惠券管理', permission: 'promotion:read' }
  ,{ id: 'comments', label: '评论审核', permission: 'comment:moderate' }
]

const MENU_PERMISSION_ALIASES: Record<string, string[]> = {
  activities: ['activity:read', 'activity:write', 'activity:publish', 'session:write'],
  venues: ['venue:read', 'venue:write', 'seat-layout:read', 'seat-layout:write'],
  orders: ['order:read', 'order:cancel', 'order:refund', 'order:export'],
  refunds: ['order:refund'],
  inventory: ['inventory:read', 'inventory:adjust', 'inventory:lock-release'],
  users: ['user:read', 'user:manage', 'role:manage'],
  scopes: ['scope:manage'],
  audit: ['audit:read']
  ,promotions: ['promotion:read','promotion:write','promotion:publish']
  ,comments: ['comment:moderate']
}

const ADMIN_PERMISSIONS = new Set(adminMenuItems.map(item => item.permission).filter(Boolean) as string[])
ADMIN_PERMISSIONS.add('activity:write')
ADMIN_PERMISSIONS.add('activity:publish')
ADMIN_PERMISSIONS.add('session:write')
ADMIN_PERMISSIONS.add('venue:write')
ADMIN_PERMISSIONS.add('seat-layout:read')
ADMIN_PERMISSIONS.add('seat-layout:write')
ADMIN_PERMISSIONS.add('order:cancel')
ADMIN_PERMISSIONS.add('order:refund')
ADMIN_PERMISSIONS.add('order:export')
ADMIN_PERMISSIONS.add('inventory:adjust')
ADMIN_PERMISSIONS.add('inventory:lock-release')
ADMIN_PERMISSIONS.add('role:manage')

export function hasPermission(permissions: string[], required: string): boolean {
  return permissions.includes('system:config') || permissions.includes(required)
}

export function canAccessAdmin(claims: TokenClaims): boolean {
  if (claims.roles?.includes('SUPER_ADMIN')) return true
  return (claims.permissions ?? []).some(permission => ADMIN_PERMISSIONS.has(permission))
}

export function visibleAdminMenu(permissions: string[]): NavigationItem[] {
  return adminMenuItems.filter(item => !item.permission || (MENU_PERMISSION_ALIASES[item.id] || [item.permission]).some(permission => hasPermission(permissions, permission)))
}
