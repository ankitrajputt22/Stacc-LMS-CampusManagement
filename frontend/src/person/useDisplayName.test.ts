import { describe, expect, it } from 'vitest'
import { initialsOf } from './useDisplayName'

describe('initialsOf', () => {
  it('takes the first letters of the first and last names', () => {
    expect(initialsOf('Aarav Sharma')).toBe('AS')
    expect(initialsOf('meera  krishnan iyer')).toBe('MI')
    expect(initialsOf('अनन्या सिंह')).toBe('अस')
  })

  it('takes two letters of a single word, such as a login ID', () => {
    expect(initialsOf('student1')).toBe('ST')
    expect(initialsOf('Aarav')).toBe('AA')
    expect(initialsOf('2408400100011')).toBe('24')
  })
})
