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
import { CourseGrades } from '../courses/CourseGrades'
import { QuizTaking } from '../courses/QuizTaking'
import { attemptResultText, attemptStateLabel, attemptsText, quizStatusText } from '../courses/quizText'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { LmsCourseDetail, QuizAttemptSummary, StudentQuiz } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, DataTable } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { NavIcon } from '../components/navigation/navIcons'
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
    <div className="space-y-4">
      <p className="text-[0.95rem] text-ink-muted">Take graded quizzes and practice quizzes for this course.</p>
      {quizzes.status !== 'loaded' ? (
        <Card>
          <SectionState request={quizzes} what="quizzes" />
        </Card>
      ) : quizzes.data.length === 0 ? (
        <Card>
          <EmptyState title="No quizzes yet." description="Quizzes appear here once your teacher publishes them." />
        </Card>
      ) : (
        <ul className="space-y-4">
          {quizzes.data.map((quiz) => {
            const latest = quiz.attempts.reduce<QuizAttemptSummary | null>(
              (found, attempt) => (found === null || attempt.attemptNumber > found.attemptNumber ? attempt : found),
              null,
            )
            const open = quiz.startable || quiz.openAttemptId !== null
            return (
              <li
                key={quiz.id}
                className={`group relative grid gap-4 rounded-panel border bg-surface px-5 py-4 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary md:grid-cols-[minmax(0,1fr)_11rem_10rem] md:items-center ${
                  open ? 'border-portal-cta/60 bg-orange-50/40' : 'border-line'
                }`}
              >
                <div className="flex min-w-0 gap-3">
                  <NavIcon name="documents" className="mt-0.5 size-6 shrink-0 text-ink-muted" />
                  <div className="min-w-0 space-y-1.5">
                    <h3 className="text-lg font-bold break-words text-portal-navy">
                      <Link
                        to={`/courses/${course.lmsCourseId}/quizzes/${quiz.id}`}
                        className="after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:underline group-hover:underline-offset-4"
                      >
                        {quiz.title}
                      </Link>
                    </h3>
                    <p className="flex flex-wrap gap-x-3 gap-y-1 text-[0.95rem] text-ink">
                      <span>{quizTypeWord(quiz)}</span>
                      <span aria-hidden="true" className="text-line-strong">|</span>
                      <span>{quiz.totalMarks} marks</span>
                      <span aria-hidden="true" className="text-line-strong">|</span>
                      <span>{`${quiz.questionCount} ${quiz.questionCount === 1 ? 'question' : 'questions'}`}</span>
                      <span aria-hidden="true" className="text-line-strong">|</span>
                      <span>{quiz.timeLimitMinutes === null ? 'No time limit' : `${quiz.timeLimitMinutes} minutes`}</span>
                    </p>
                    {quiz.dueAt && <p className="text-sm text-ink-muted">Due {formatDateTime(quiz.dueAt)}</p>}
                  </div>
                </div>
                <div className="space-y-1 text-sm md:border-l md:border-line md:pl-5 md:text-center">
                  {latest !== null && latest.resultStatus === 'RELEASED' ? (
                    <>
                      <p className="text-ink-muted">Latest result</p>
                      <p className="inline-block rounded-control bg-success-soft px-3 py-1 text-lg font-bold text-success tabular-nums">
                        {attemptResultText(latest, quiz.totalMarks)}
                      </p>
                    </>
                  ) : (
                    <>
                      {quiz.openAttemptId !== null && <Badge tone="info">In progress</Badge>}
                      <p className="text-ink">{quizStatusText(quiz)}</p>
                    </>
                  )}
                  <p className="text-ink-muted">{attemptsText(quiz)}</p>
                </div>
                <span
                  aria-hidden="true"
                  className={`inline-flex h-10 items-center justify-center gap-1 justify-self-start rounded-control px-4 text-sm font-medium md:justify-self-end ${
                    open ? 'bg-portal-cta text-white group-hover:bg-portal-cta-hover' : 'bg-sky-100 text-portal-navy group-hover:bg-sky-200'
                  }`}
                >
                  {quiz.openAttemptId !== null ? 'Continue' : open ? 'Open quiz' : 'View quiz'} ›
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

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
                  {`Attempt ${row.attemptNumber}`}
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
            { key: 'result', header: 'Result', cell: (row) => attemptResultText(row, data.totalMarks) },
          ]}
        />
      </Card>
    </div>
  )
}

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
  return <QuizTaking key={attempt.data.id} attempt={attempt.data} lmsCourseId={course.lmsCourseId} />
}

export function QuizAttemptPage() {
  return (
    <StudentCourseSection title="Quiz attempt" parent={QUIZZES}>
      {(course) => <Attempt course={course} />}
    </StudentCourseSection>
  )
}

export function CourseGradesPage() {
  return <StudentCourseSection title="Grades">{(course) => <CourseGrades lmsCourseId={course.lmsCourseId} />}</StudentCourseSection>
}
