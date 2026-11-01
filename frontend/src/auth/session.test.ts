import { describe, expect, it } from 'vitest'
import { isSessionExpired, sessionFrom } from './session'
import type { SessionResponse } from './types'

const answer: SessionResponse = {
  accountId: 7,
  loginId: 'student1',
  roles: ['STUDENT'],
  expiresAt: '2026-11-01T22:00:00Z',
}

describe('sessionFrom', () => {
  it('keeps who is signed in and when the session ends, and nothing secret', () => {
    expect(sessionFrom(answer)).toEqual({
      accountId: 7,
      loginId: 'student1',
      roles: ['STUDENT'],
      expiresAt: Date.parse('2026-11-01T22:00:00Z'),
    })
  })

  it('refuses an answer that does not describe a session', () => {
    expect(() => sessionFrom({ ...answer, loginId: '' })).toThrow()
    expect(() => sessionFrom({ ...answer, expiresAt: 'not a time' })).toThrow()
  })
})

describe('isSessionExpired', () => {
  it('is expired from the end time on', () => {
    const session = sessionFrom(answer)
    expect(isSessionExpired(session, session.expiresAt - 1)).toBe(false)
    expect(isSessionExpired(session, session.expiresAt)).toBe(true)
  })
})
