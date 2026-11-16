import { describe, expect, it } from 'vitest'
import { landingFor } from './landing'

describe('landingFor', () => {
  it('opens the Overview by default, whatever the roles', () => {
    expect(landingFor(null, ['STUDENT'])).toBe('/')
    expect(landingFor('erp', ['FACULTY'])).toBe('/')
  })

  it('keeps old LMS links useful for single-role accounts and never picks a main role', () => {
    expect(landingFor('lms', ['STUDENT'])).toBe('/courses')
    expect(landingFor('lms', ['FACULTY'])).toBe('/teaching')
    expect(landingFor('lms', ['STUDENT', 'FACULTY'])).toBe('/')
    expect(landingFor('lms', [])).toBe('/')
  })
})
