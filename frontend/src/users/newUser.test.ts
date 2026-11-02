import { describe, expect, it } from 'vitest'
import { checkNewUser, EMPTY } from './newUser'

const filled = { ...EMPTY, loginId: '25CSE104', fullName: 'Riya Menon', temporaryPassword: 'Bright-Lantern-2047' }

describe('checkNewUser', () => {
  it('accepts an admin account with the basics', () => {
    expect(checkNewUser(filled, ['ADMIN'])).toEqual({})
  })

  it('needs a role, a long enough password, and a valid email when given', () => {
    const errors = checkNewUser({ ...filled, temporaryPassword: 'short', email: 'not-an-email' }, [])
    expect(Object.keys(errors).sort()).toEqual(['email', 'roles', 'temporaryPassword'])
  })

  it('asks for the profile details each role needs', () => {
    expect(Object.keys(checkNewUser(filled, ['STUDENT', 'FACULTY'])).sort()).toEqual([
      'admissionSessionId',
      'departmentId',
      'programId',
    ])
    expect(checkNewUser({ ...filled, programId: '4', admissionSessionId: '2' }, ['STUDENT'])).toEqual({})
  })
})
