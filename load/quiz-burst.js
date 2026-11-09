import http from 'k6/http'
import { check } from 'k6'
import { BASE, signIn } from './common.js'

// Pass LMS_COURSE_ID and QUIZ_ID of a published practice quiz the demo students can take.
export const options = { vus: 20, iterations: 40 }

export function setup() {
  return ['student1', 'student2', 'studentfaculty1'].map((id) => signIn(id))
}

export default function (sessions) {
  const params = sessions[__VU % sessions.length]
  const response = http.post(`${BASE}/lms/courses/${__ENV.LMS_COURSE_ID}/quizzes/${__ENV.QUIZ_ID}/attempts`, null, params)
  check(response, { 'no server error': (r) => r.status < 500 })
}
