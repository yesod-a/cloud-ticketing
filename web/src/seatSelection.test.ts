import { describe, expect, it } from 'vitest'
import { toggleSelection } from './seatSelection'
describe('toggleSelection', () => {
  it('adds and removes a seat', () => { expect(toggleSelection([], 'A-1')).toEqual(['A-1']); expect(toggleSelection(['A-1'], 'A-1')).toEqual([]) })
  it('does not add a seventh seat', () => { const six = ['1', '2', '3', '4', '5', '6']; expect(toggleSelection(six, '7')).toEqual(six) })
})
