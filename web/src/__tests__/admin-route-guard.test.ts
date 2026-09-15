import { describe, expect, it } from 'vitest'
import { canEnterAdmin } from '../router'
describe('admin guard',()=>{it('rejects an account without required permission',()=>expect(canEnterAdmin([], 'activity:publish')).toBe(false))})
