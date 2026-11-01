import { describe, expect, it } from 'vitest'
import { isSessionExpired, sessionFromLogin } from './session'
import type { LoginResponse } from './types'

const login: LoginResponse = {
  accountId: 7,
  loginId: 'student1',
  roles: ['STUDENT'],
  accessToken: 'token-value',
  tokenType: 'Bearer',
  expiresIn: 900,
}

describe('sessionFromLogin', () => {
  it('works out when the token ends from how long it lasts', () => {
    expect(sessionFromLogin(login, 1_000)).toEqual({
      accountId: 7,
      loginId: 'student1',
      roles: ['STUDENT'],
      accessToken: 'token-value',
      expiresAt: 1_000 + 900_000,
    })
  })

  it('refuses an answer without a usable token', () => {
    expect(() => sessionFromLogin({ ...login, accessToken: '' })).toThrow()
    expect(() => sessionFromLogin({ ...login, expiresIn: 0 })).toThrow()
    expect(() => sessionFromLogin({ ...login, expiresIn: Number.NaN })).toThrow()
  })
})

describe('isSessionExpired', () => {
  it('is expired from the end time on', () => {
    const session = sessionFromLogin(login, 0)
    expect(isSessionExpired(session, 899_999)).toBe(false)
    expect(isSessionExpired(session, 900_000)).toBe(true)
  })
})
