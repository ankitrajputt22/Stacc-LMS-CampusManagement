import { describe, expect, it } from 'vitest'
import { localValue, moment, registrationInOrder } from './registrationPeriod'

const period = (registrationOpensAt: string, registrationClosesAt: string, dropDeadline = '') => ({
  registrationOpensAt,
  registrationClosesAt,
  dropDeadline,
})

describe('registration period rules', () => {
  it('accepts a full period, an empty one, and a period without a drop deadline', () => {
    expect(registrationInOrder(period('2026-07-01T09:00', '2026-07-15T17:00', '2026-07-31T17:00'))).toEqual({})
    expect(registrationInOrder(period('', ''))).toEqual({})
    expect(registrationInOrder(period('2026-07-01T09:00', '2026-07-15T17:00'))).toEqual({})
  })

  it('needs both ends of the period', () => {
    expect(registrationInOrder(period('2026-07-01T09:00', ''))).toHaveProperty('registrationClosesAt')
    expect(registrationInOrder(period('', '2026-07-15T17:00'))).toHaveProperty('registrationOpensAt')
  })

  it('needs registration to close after it opens', () => {
    expect(registrationInOrder(period('2026-07-15T17:00', '2026-07-01T09:00'))).toEqual({
      registrationClosesAt: 'Registration must close after it opens.',
    })
    expect(registrationInOrder(period('2026-07-01T09:00', '2026-07-01T09:00'))).toHaveProperty('registrationClosesAt')
  })

  it('keeps the drop deadline inside a period', () => {
    expect(registrationInOrder(period('', '', '2026-07-31T17:00'))).toEqual({
      dropDeadline: 'A drop deadline needs a registration period.',
    })
    expect(registrationInOrder(period('2026-07-01T09:00', '2026-07-15T17:00', '2026-06-30T09:00'))).toEqual({
      dropDeadline: 'The drop deadline cannot be before registration opens.',
    })
  })

  it('sends local times as moments and shows moments as local times', () => {
    expect(moment('')).toBeNull()
    const sent = moment('2026-07-01T09:00')
    expect(sent).toBe(new Date(2026, 6, 1, 9, 0).toISOString())
    expect(localValue(sent)).toBe('2026-07-01T09:00')
    expect(localValue(null)).toBe('')
  })
})
