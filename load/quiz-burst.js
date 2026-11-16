import http from 'k6/http'
import { check } from 'k6'
import exec from 'k6/execution'
import { Counter } from 'k6/metrics'
import { annotate, BASE, signIn } from './common.js'

// Many students take one quiz at once, several requests from each, so starting, saving, and handing in race on the
// same attempt. Setup builds a graded quiz with one attempt each from the reviewed objective questions of the course's
// bank (as faculty1 would) and publishes it; each request then starts or resumes the student's attempt, saves the
// correct answers, and hands it in. Afterwards every student must hold exactly one handed-in attempt with full marks.
// Run it on a throwaway stack with the demo data.
const FACULTY = __ENV.FACULTY || 'faculty1'
const COURSE_CODE = __ENV.COURSE_CODE || 'CS301'
const STUDENTS = (__ENV.STUDENTS || 'student1,student2,studentfaculty1').split(',').map((id) => id.trim())
const REQUESTS_PER_STUDENT = Number(__ENV.REQUESTS_PER_STUDENT || 10)
const TOTAL = STUDENTS.length * REQUESTS_PER_STUDENT
const OBJECTIVE = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE']

const unexpectedAnswers = new Counter('unexpected_answers')
const brokenRules = new Counter('broken_rules')

export const options = {
  scenarios: {
    burst: { executor: 'shared-iterations', vus: TOTAL, iterations: TOTAL, maxDuration: '2m' },
  },
  thresholds: {
    unexpected_answers: ['count==0'],
    broken_rules: ['count==0'],
    http_req_duration: ['p(95)<3000'],
  },
}

function json(response, what) {
  if (response.status !== 200 && response.status !== 201) {
    throw new Error(`${what} answered ${response.status}`)
  }
  return response.json()
}

export function setup() {
  const faculty = signIn(FACULTY)
  const course = json(http.get(`${BASE}/lms/faculty/managed-courses`, faculty), 'The teaching list').find(
    (entry) => entry.courseCode === COURSE_CODE && entry.lmsCourseId !== null,
  )
  if (!course) {
    throw new Error(`${FACULTY} has no LMS course for ${COURSE_CODE}`)
  }
  const bank = json(http.get(`${BASE}/lms/faculty/courses/${course.lmsCourseId}/questions`, faculty), 'The question bank').filter(
    (question) => question.status === 'ACTIVE' && question.reviewStatus === 'REVIEWED' && OBJECTIVE.includes(question.type),
  )
  if (bank.length === 0) {
    throw new Error(`The ${COURSE_CODE} bank has no reviewed objective questions`)
  }
  const quiz = json(
    http.post(
      `${BASE}/lms/faculty/courses/${course.lmsCourseId}/quizzes`,
      JSON.stringify({
        title: `Load test ${Date.now()}`,
        instructionsFormat: 'PLAIN',
        type: 'GRADED',
        maxAttempts: 1,
        shuffleQuestions: true,
        shuffleOptions: true,
        negativeMarkPercent: 0,
        showAnswers: false,
      }),
      faculty,
    ),
    'Creating the quiz',
  )
  for (const question of bank) {
    json(http.post(`${BASE}/lms/faculty/quizzes/${quiz.id}/questions`, JSON.stringify({ questionId: question.id }), faculty), 'Adding a question')
  }
  json(http.post(`${BASE}/lms/faculty/quizzes/${quiz.id}/publish`, null, faculty), 'Publishing the quiz')

  // The correct answers by question text, so each attempt can answer whatever order it shows.
  const answers = {}
  for (const question of bank) {
    answers[question.text] = {
      trueFalseAnswer: question.type === 'TRUE_FALSE' ? question.trueFalseAnswer : null,
      correctTexts: question.options.filter((option) => option.correct).map((option) => option.text),
    }
  }
  return {
    lmsCourseId: course.lmsCourseId,
    quizId: quiz.id,
    answers,
    faculty,
    sessions: STUDENTS.map((loginId) => ({ loginId, params: signIn(loginId) })),
  }
}

function expected(response, what, allowed) {
  const ok = check(response, { [`${what}: ${allowed.join(' or ')}`]: (r) => allowed.includes(r.status) })
  if (!ok) {
    unexpectedAnswers.add(1, { step: what })
  }
  return ok
}

export default function (data) {
  const { params } = data.sessions[exec.scenario.iterationInTest % data.sessions.length]
  // 200 starts or resumes the one attempt; 409 once it is handed in and no attempts are left.
  const started = http.post(`${BASE}/lms/courses/${data.lmsCourseId}/quizzes/${data.quizId}/attempts`, null, params)
  if (!expected(started, 'start', [200, 409]) || started.status !== 200) {
    return
  }
  const attempt = started.json()
  for (const question of attempt.questions) {
    const key = data.answers[question.text]
    const body = question.type === 'TRUE_FALSE'
      ? { trueFalseAnswer: key.trueFalseAnswer }
      : { selectedOptions: question.options.filter((choice) => key.correctTexts.includes(choice.text)).map((choice) => choice.position) }
    // 409 once another request of the same student has handed the attempt in.
    const saved = http.put(`${BASE}/lms/quiz-attempts/${attempt.id}/answers/${question.quizQuestionId}`, JSON.stringify(body), params)
    expected(saved, 'save', [200, 409])
  }
  const submitted = http.post(`${BASE}/lms/quiz-attempts/${attempt.id}/submit`, null, params)
  expected(submitted, 'hand in', [200, 409])
}

export function teardown(data) {
  const results = json(http.get(`${BASE}/lms/faculty/quizzes/${data.quizId}/attempts`, data.faculty), 'The quiz results')
  for (const loginId of STUDENTS) {
    const attempts = results.attempts.filter((row) => row.studentLoginId === loginId)
    const kept = check(attempts, {
      [`${loginId} holds exactly one attempt`]: (rows) => rows.length === 1,
      [`${loginId}'s attempt is handed in`]: (rows) => rows.length === 1 && rows[0].state === 'SUBMITTED',
      [`${loginId}'s attempt has full marks`]: (rows) =>
        rows.length === 1 && rows[0].needsMarking === 0 && Number(rows[0].score) === Number(results.totalMarks),
    })
    if (!kept) {
      brokenRules.add(1, { student: loginId })
    }
  }
}

export const handleSummary = annotate('Quiz load test')
