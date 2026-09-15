import { describe, expect, it } from 'vitest'
import { createSession } from '../auth/session'
describe('session', () => { it('clears a session when refresh fails', async () => { const session=createSession(async()=>false); session.set('access'); await session.refresh(); expect(session.token()).toBeNull() }) })
