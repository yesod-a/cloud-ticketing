import { describe, expect, it } from 'vitest'
import { adminMenuItems, canAccessAdmin, hasPermission, userMenuItems } from '../auth/navigationPermissions'

describe('navigation permissions', () => {
  it('keeps the customer menu separate from the admin menu', () => {
    expect(userMenuItems.map(item => item.id)).toEqual(['home', 'orders', 'profile'])
    expect(adminMenuItems.map(item => item.id)).toEqual([
      'activities', 'venues', 'orders', 'refunds', 'inventory', 'users', 'scopes', 'audit', 'promotions', 'comments'
    ])
  })

  it('does not treat an arbitrary user permission as admin access', () => {
    expect(canAccessAdmin({ roles: ['USER'], permissions: ['profile:read'] })).toBe(false)
    expect(canAccessAdmin({ roles: ['OPERATOR'], permissions: ['activity:read'] })).toBe(true)
    expect(canAccessAdmin({ roles: ['SUPER_ADMIN'], permissions: [] })).toBe(true)
  })

  it('matches the backend permission contract and system override', () => {
    expect(hasPermission(['activity:read'], 'activity:read')).toBe(true)
    expect(hasPermission(['activity:read'], 'activity:write')).toBe(false)
    expect(hasPermission(['system:config'], 'user:manage')).toBe(true)
  })
})
