import http from 'k6/http'
import { check } from 'k6'

export const BASE = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1`
const PASSWORD = __ENV.STACC_TEST_PASSWORD

/** Signs in through the one login and returns headers carrying the session token for API clients. */
export function signIn(loginId) {
  const response = http.post(`${BASE}/auth/login`, JSON.stringify({ loginId, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  })
  check(response, { 'signed in': (r) => r.status === 200 })
  const token = response.cookies.STACC_SESSION && response.cookies.STACC_SESSION[0] ? response.cookies.STACC_SESSION[0].value : null
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
}
