import http from 'k6/http'
import { check } from 'k6'
import exec from 'k6/execution'
import { Counter } from 'k6/metrics'
import { BASE, signIn } from './common.js'

// Many registrations for one course offering at once, several from each student, so the capacity lock and the
// "not taken before" rule are raced. The demo data's third-semester students and CS305 (open to the whole semester)
// are the defaults. Run it on a fresh throwaway stack: a student who already took the course cannot register again.
const STUDENTS = (__ENV.STUDENTS || 'student1,student2,studentfaculty1').split(',').map((id) => id.trim())
const COURSE_CODE = __ENV.COURSE_CODE || 'CS305'
const REQUESTS_PER_STUDENT = Number(__ENV.REQUESTS_PER_STUDENT || 10)
const TOTAL = STUDENTS.length * REQUESTS_PER_STUDENT

const registered = new Counter('registered')
const unexpectedAnswers = new Counter('unexpected_answers')
const brokenRules = new Counter('broken_rules')

const perStudent = {}
for (const loginId of STUDENTS) {
  perStudent[`registered{student:${loginId}}`] = ['count<=1']
}

export const options = {
  scenarios: {
    burst: { executor: 'shared-iterations', vus: TOTAL, iterations: TOTAL, maxDuration: '2m' },
  },
  thresholds: {
    unexpected_answers: ['count==0'],
    broken_rules: ['count==0'],
    http_req_duration: ['p(95)<3000'],
    ...perStudent,
  },
}

const OVERVIEW = `${BASE}/students/me/course-registration`

/** The student's term holding the offering, and what it says about the offering and their own enrollments in it. */
function look(params, offeringId) {
  const response = http.get(OVERVIEW, params)
  if (response.status !== 200) {
    throw new Error(`The registration overview answered ${response.status}`)
  }
  for (const term of response.json('terms')) {
    const offering = term.offerings.find((o) => o.courseOfferingId === offeringId || o.courseCode === COURSE_CODE)
    const rows = term.registered.filter((c) => c.courseOfferingId === offeringId || c.courseCode === COURSE_CODE)
    if (offering || rows.length > 0) {
      return {
        offeringId: offering ? offering.courseOfferingId : rows[0].courseOfferingId,
        canRegister: Boolean(offering && offering.canRegister),
        availableSeats: offering ? offering.availableSeats : null,
        enrolled: rows.filter((c) => c.status === 'ENROLLED').length,
      }
    }
  }
  return null
}

export function setup() {
  const sessions = STUDENTS.map((loginId) => ({ loginId, params: signIn(loginId) }))
  const before = sessions.map((session) => look(session.params, null))
  const found = before.find((state) => state !== null)
  if (!found) {
    throw new Error(`None of the students can see ${COURSE_CODE}`)
  }
  const eligible = before.filter((state) => state && state.canRegister).length
  if (eligible === 0) {
    throw new Error(`No student can register for ${COURSE_CODE} now; use a fresh stack or another COURSE_CODE`)
  }
  const seats = before.map((state) => (state ? state.availableSeats : null)).find((value) => value !== null)
  return { sessions, offeringId: found.offeringId, before, eligible, seats: seats === undefined ? null : seats }
}

export default function (data) {
  const { loginId, params } = data.sessions[exec.scenario.iterationInTest % data.sessions.length]
  const response = http.post(OVERVIEW, JSON.stringify({ courseOfferingIds: [data.offeringId] }), {
    ...params,
    tags: { student: loginId },
    responseCallback: http.expectedStatuses(201, 409),
  })
  const answered = check(response, { 'registered (201) or refused (409)': (r) => r.status === 201 || r.status === 409 })
  if (!answered) {
    unexpectedAnswers.add(1, { student: loginId })
  }
  if (response.status === 201) {
    registered.add(1, { student: loginId })
  }
}

export function teardown(data) {
  // The eligible students who got in: every one of them, unless the free places ran out first.
  const expected = data.seats === null ? data.eligible : Math.min(data.eligible, data.seats)
  let gained = 0
  let seatsAfter = null
  data.sessions.forEach((session, index) => {
    const after = look(session.params, data.offeringId)
    const before = data.before[index]
    const kept = check(after, {
      [`${session.loginId} holds at most one enrollment in ${COURSE_CODE}`]: (state) => state !== null && state.enrolled <= 1,
    })
    if (!kept) {
      brokenRules.add(1)
      return
    }
    gained += after.enrolled - (before ? before.enrolled : 0)
    if (after.availableSeats !== null) {
      seatsAfter = after.availableSeats
    }
  })
  const counted = check(null, {
    [`every eligible student got a place, up to the free places (${expected})`]: () => gained === expected,
    'free places never below zero': () => seatsAfter === null || seatsAfter >= 0,
    'free places went down by exactly the new enrollments': () =>
      data.seats === null || seatsAfter === null || seatsAfter === data.seats - gained,
  })
  if (!counted) {
    brokenRules.add(1)
  }
}
