import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getQuizAttempt, getStudentQuiz, getStudentQuizzes, startQuizAttempt } from '../api/lmsQuizAttemptsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { parseCourseId } from '../courses/courseRoute'
import { formatDateTime } from '../courses/dates'
import { QuizTaking } from '../courses/QuizTaking'
import { attemptStateLabel, attemptsText, quizStatusText } from '../courses/quizText'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { LmsCourseDetail, QuizAttemptSummary, StudentQuiz } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, DataTable } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { StudentCourseSection } from './CourseSectionPages'

const QUIZZES = { path: 'quizzes', label: 'Quizzes' }

function quizTypeWord(quiz: Pick<StudentQuiz, 'type'>): string {
  return quiz.type === 'PRACTICE' ? 'Practice' : 'Graded'
}

function QuizList({ course }: { course: LmsCourseDetail }) {
  const quizzes = useApiQuery(queryKeys.lms.studentQuizzes(course.lmsCourseId), (signal) =>
    getStudentQuizzes(course.lmsCourseId, signal),
  )
  return (
    <Card title="Quizzes" padding="none">
      {quizzes.status !== 'loaded' ? (
        <SectionState request={quizzes} what="quizzes" />
      ) : quizzes.data.length === 0 ? (
        <EmptyState title="No quizzes yet." description="Quizzes appear here once your teacher publishes them." />
      ) : (
        <ul className="divide-y divide-line">
          {quizzes.data.map((quiz) => (
            <li key={quiz.id} className="space-y-1.5 px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-center gap-2">
                <Link to={`/courses/${course.lmsCourseId}/quizzes/${quiz.id}`} className={`font-semibold ${textLinkClasses}`}>
                  {quiz.title}
                </Link>
                <Badge tone="brand">{quizTypeWord(quiz)}</Badge>
                {quiz.openAttemptId !== null && <Badge tone="info">In progress</Badge>}
              </div>
              <p className="text-sm text-ink-muted">
                {[
                  `${quiz.questionCount} ${quiz.questionCount === 1 ? 'question' : 'questions'}`,
                  quiz.timeLimitMinutes === null ? 'No time limit' : `${quiz.timeLimitMinutes} minutes`,
                  quiz.dueAt && `Due ${formatDateTime(quiz.dueAt)}`,
                  attemptsText(quiz),
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
              <p className="text-sm text-ink">{quizStatusText(quiz)}</p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** /courses/:lmsCourseId/quizzes: the course's published quizzes with the student's own attempts. */
export function CourseQuizzesPage() {
  return <StudentCourseSection title="Quizzes">{(course) => <QuizList course={course} />}</StudentCourseSection>
}

function Rules({ quiz }: { quiz: StudentQuiz }) {
  const rows: [string, string][] = [
    ['Type', quizTypeWord(quiz)],
    ['Questions', `${quiz.questionCount} (${quiz.totalMarks} ${quiz.totalMarks === 1 ? 'mark' : 'marks'})`],
    ['Time limit', quiz.timeLimitMinutes === null ? 'None' : `${quiz.timeLimitMinutes} minutes from the start`],
    ['Attempts', quiz.maxAttempts === null ? 'Unlimited' : `${quiz.maxAttempts} (${quiz.attemptsLeft} left)`],
    ['Opens', quiz.availableFrom ? formatDateTime(quiz.availableFrom) : 'Now'],
    ['Due', quiz.dueAt ? formatDateTime(quiz.dueAt) : 'No due date'],
    ['Closes', quiz.availableUntil ? formatDateTime(quiz.availableUntil) : 'Never'],
    [
      'Wrong answers',
      Number(quiz.negativeMarkPercent) === 0
        ? 'Lose no marks'
        : `Lose ${quiz.negativeMarkPercent}% of the question’s marks (unanswered questions lose nothing)`,
    ],
  ]
  return (
    <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt className="text-ink-muted">{label}</dt>
          <dd className="font-medium text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function QuizDetail({ course }: { course: LmsCourseDetail }) {
  const quizId = parseCourseId(useParams().quizId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const quiz = useApiQuery(quizId === null ? null : queryKeys.lms.studentQuiz(course.lmsCourseId, quizId), (signal) =>
    getStudentQuiz(course.lmsCourseId, quizId ?? 0, signal),
  )
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (quiz.status !== 'loaded') {
    return (
      <Card title="Quiz">
        {quiz.status === 'notFound' ? (
          <EmptyState title="Quiz unavailable" description="This quiz could not be found or is not available to your account." />
        ) : (
          <SectionState request={quiz} what="the quiz" />
        )}
      </Card>
    )
  }
  const data = quiz.data
  const attemptPath = (attemptId: number) => `/courses/${course.lmsCourseId}/quizzes/${data.id}/attempts/${attemptId}`

  async function start() {
    try {
      const attempt = await startQuizAttempt(course.lmsCourseId, data.id)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
      void navigate(attemptPath(attempt.id))
    } catch (failure) {
      setConfirming(false)
      const status = getHttpStatus(failure)
      if (status === 401) {
        return
      }
      const message = getApiError(failure)?.message
      setError(status === 403 ? 'You don’t have access to take quizzes.' : message && status !== null && status < 500 ? message : 'Couldn’t start the quiz. Try again.')
      void queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    }
  }

  return (
    <div className="space-y-6">
      <Card title={data.title}>
        <div className="space-y-4">
          {data.instructions && (
            <FormattedText text={data.instructions} format={data.instructionsFormat} className="text-sm leading-6 text-ink" headingLevel={3} />
          )}
          <Rules quiz={data} />
          <p className="text-sm text-ink">{quizStatusText(data)}</p>
          {error && <Alert tone="error">{error}</Alert>}
          {data.openAttemptId !== null ? (
            <ButtonLink to={attemptPath(data.openAttemptId)}>Resume quiz</ButtonLink>
          ) : (
            data.startable && <Button onClick={() => setConfirming(true)}>Start quiz</Button>
          )}
          {confirming && (
            <StatusConfirmation
              question={`Start “${data.title}”?`}
              detail={
                data.timeLimitMinutes === null
                  ? 'Your answers are saved as you go. Submit when you are done.'
                  : `You have ${data.timeLimitMinutes} minutes from now. The time keeps running if you leave the page, and your saved answers are handed in when it runs out.`
              }
              confirmLabel="Start"
              busyLabel="Starting…"
              describeRefusal={() => 'Couldn’t start the quiz. Try again.'}
              onCancel={() => setConfirming(false)}
              onConfirm={start}
            />
          )}
        </div>
      </Card>

      <Card title="Your attempts" padding="none">
        <DataTable<QuizAttemptSummary>
          caption="Your attempts"
          rows={data.attempts}
          rowKey={(row) => row.id}
          empty={<EmptyState title="No attempts yet." />}
          columns={[
            {
              key: 'attempt',
              header: 'Attempt',
              primary: true,
              cell: (row) => (
                <Link to={attemptPath(row.id)} className={textLinkClasses}>
                  Attempt {row.attemptNumber}
                </Link>
              ),
            },
            { key: 'started', header: 'Started', cell: (row) => formatDateTime(row.startedAt) },
            {
              key: 'handedIn',
              header: 'Handed in',
              cell: (row) => (row.submittedAt ? `${formatDateTime(row.submittedAt)}${row.late ? ' (late)' : ''}` : '—'),
            },
            { key: 'state', header: 'Status', cell: (row) => attemptStateLabel(row.state) },
          ]}
        />
      </Card>
    </div>
  )
}

/** /courses/:lmsCourseId/quizzes/:quizId: one quiz's rules and the student's attempts, with Start or Resume. */
export function CourseQuizPage() {
  return (
    <StudentCourseSection title="Quiz" parent={QUIZZES}>
      {(course) => <QuizDetail course={course} />}
    </StudentCourseSection>
  )
}

function Attempt({ course }: { course: LmsCourseDetail }) {
  const attemptId = parseCourseId(useParams().attemptId)
  const attempt = useApiQuery(attemptId === null ? null : queryKeys.lms.quizAttempt(attemptId), (signal) =>
    getQuizAttempt(attemptId ?? 0, signal),
  )
  if (attempt.status !== 'loaded') {
    return (
      <Card title="Quiz attempt">
        {attempt.status === 'notFound' ? (
          <EmptyState title="Attempt unavailable" description="This attempt could not be found or is not available to your account." />
        ) : (
          <SectionState request={attempt} what="the attempt" />
        )}
      </Card>
    )
  }
  // Keyed by the attempt, so its answers are read once from the server and then kept on the page.
  return <QuizTaking key={attempt.data.id} attempt={attempt.data} lmsCourseId={course.lmsCourseId} />
}

/** /courses/:lmsCourseId/quizzes/:quizId/attempts/:attemptId: taking, or reading back, one of the student's attempts. */
export function QuizAttemptPage() {
  return (
    <StudentCourseSection title="Quiz attempt" parent={QUIZZES}>
      {(course) => <Attempt course={course} />}
    </StudentCourseSection>
  )
}
