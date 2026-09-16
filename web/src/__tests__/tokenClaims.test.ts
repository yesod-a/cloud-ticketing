import { describe, expect, it } from 'vitest'
import { canAccessAdmin, tokenClaims } from '../auth/tokenClaims'

const token = (payload: object) => `x.${btoa(JSON.stringify(payload)).replace(/=/g, '')}.x`
describe('token claims', () => {
  it('reads roles, permissions, and scopes from JWT payload', () => expect(tokenClaims(token({ roles: ['SUPER_ADMIN'], permissions: ['user:manage'], scopes: ['ACTIVITY:a1'] }))).toEqual({ roles: ['SUPER_ADMIN'], permissions: ['user:manage'], scopes: ['ACTIVITY:a1'] }))
  it('allows administrators but rejects regular users', () => {
    expect(canAccessAdmin(tokenClaims(token({ roles: ['SUPER_ADMIN'], permissions: [] })))).toBe(true)
    expect(canAccessAdmin(tokenClaims(token({ roles: ['USER'], permissions: [] })))).toBe(false)
  })
})
