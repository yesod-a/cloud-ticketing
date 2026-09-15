import { describe, expect, it } from 'vitest'
import { activityTitle } from '../views/activityList'
describe('activity list',()=>{it('uses API title instead of a demo title',()=>expect(activityTitle({title:'海岸线音乐节'})).toBe('海岸线音乐节'))})
