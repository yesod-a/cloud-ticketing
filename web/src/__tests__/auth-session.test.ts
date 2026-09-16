import { describe, expect, it } from 'vitest'
import { createSession } from '../auth/session'
import { login, session } from '../auth/authApi'
import { vi } from 'vitest'
describe('session', () => { it('clears a session when refresh fails', async () => { const session=createSession(async()=>false); session.set('access'); await session.refresh(); expect(session.token()).toBeNull() }) })

it('stores the refresh token returned by login', async () => {
  session.clear()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { accessToken: 'a1', refreshToken: 'r1' } }), { status: 200 })))
  await login('u@example.com', 'StrongPass1')
  expect(session.token()).toBe('a1')
  expect(session.refreshToken()).toBe('r1')
  vi.unstubAllGlobals()
})
