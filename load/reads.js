import http from 'k6/http'
import { check, sleep } from 'k6'
import { BASE, signIn } from './common.js'

export const options = {
  vus: 50,
  duration: '2m',
  thresholds: { http_req_duration: ['p(95)<800'], http_req_failed: ['rate<0.01'] },
}

const STUDENTS = ['student1', 'student2', 'studentfaculty1']

export function setup() {
  return STUDENTS.map((id) => signIn(id))
}

export default function (sessions) {
  const params = sessions[__VU % sessions.length]
  const today = new Date().toISOString().slice(0, 10)
  for (const path of ['/dashboard/student', '/lms/my-courses', '/me/todo', `/calendar/me?from=${today}&to=${today}&timeZone=UTC`, '/notifications/unread-count']) {
    const response = http.get(`${BASE}${path}`, params)
    check(response, { [`${path} ok`]: (r) => r.status === 200 })
  }
  sleep(1)
}
