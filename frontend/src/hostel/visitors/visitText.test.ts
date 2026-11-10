import { describe, expect, it } from 'vitest'
import { shortTime, visitStatusLabel } from './visitText'

describe('visit text', () => {
  it('names visit states and shortens times', () => {
    expect(visitStatusLabel('ARRIVED')).toBe('Inside')
    expect(visitStatusLabel('EXPECTED')).toBe('Expected')
    expect(shortTime('16:00:00')).toBe('16:00')
  })
})
