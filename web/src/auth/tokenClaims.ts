export type TokenClaims = { roles?: string[]; permissions?: string[]; scopes?: string[] }

import { canAccessAdmin as canAccessAdminForNavigation } from './navigationPermissions'

export function tokenClaims(token: string): TokenClaims {
  try {
    const encoded = token.split('.')[1]
    if (!encoded) return {}
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))) as TokenClaims
    return { roles: Array.isArray(payload.roles) ? payload.roles : [], permissions: Array.isArray(payload.permissions) ? payload.permissions : [], scopes: Array.isArray(payload.scopes) ? payload.scopes : [] }
  } catch { return {} }
}

export function canAccessAdmin(claims: TokenClaims): boolean {
  return canAccessAdminForNavigation(claims)
}
