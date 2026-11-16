import http from 'k6/http'
import { check } from 'k6'
import { BASE, signIn } from './common.js'

// Pass EVENT_ID of a published event with a small capacity. Every request must answer 200 or 409, never 5xx.
export const options = { vus: 20, iterations: 20 }

export function setup() {
  return ['student1', 'student2', 'studentfaculty1'].map((id) => signIn(id))
}

export default function (sessions) {
  const params = sessions[__VU % sessions.length]
  const response = http.post(`${BASE}/engagement/events/${__ENV.EVENT_ID}/registration`, null, params)
  check(response, { 'no server error': (r) => r.status < 500 })
}
