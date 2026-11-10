import { describe, expect, it } from 'vitest'
import { canRate, mealLabel } from './messText'

describe('mess text', () => {
  it('names meals and allows rating only today and the last seven days', () => {
    expect(mealLabel('SNACKS')).toBe('Snacks')
    expect(canRate('2026-11-12', '2026-11-12')).toBe(true)
    expect(canRate('2026-11-05', '2026-11-12')).toBe(true)
    expect(canRate('2026-11-04', '2026-11-12')).toBe(false)
    expect(canRate('2026-11-13', '2026-11-12')).toBe(false)
  })
})
