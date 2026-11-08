import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { closeQuizAttempt, getQuizAttemptReview, markQuizAnswer } from '../api/lmsQuizzesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { attemptStateLabel, outcomeLabel } from '../courses/quizText'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { QuizAttemptReview } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, TextAreaField, TextField } from '../ui'
import { questionTypeLabel } from './questionText'

type ReviewQuestion = QuizAttemptReview['questions'][number]

const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/
const FORBIDDEN = 'You don’t have access to mark this course’s quizzes. If your access changed recently, sign out and sign in again.'

function refusalText(failure: unknown, fallback: string): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return FORBIDDEN
  }
  const message = getApiError(failure)?.message
  return message && status !== null && status < 500 ? message : fallback
}

/** The student's answer as they gave it. Plain text, never HTML. */
function StudentAnswer({ question }: { question: ReviewQuestion }) {
  const answer = question.answer
  const empty = <p className="text-sm text-ink-muted">Not answered.</p>
  if (!answer) {
    return empty
  }
  if (question.type === 'SINGLE_CHOICE' || question.type === 'MULTIPLE_CHOICE') {
    return answer.selectedOptions.length === 0 ? (
      empty
    ) : (
      <p className="text-sm text-ink">
        Answered: {answer.selectedOptions.map((position) => question.options[position - 1]?.text ?? `Option ${position}`).join(' · ')}
      </p>
    )
  }
  if (question.type === 'TRUE_FALSE') {
    return answer.trueFalseAnswer === null ? empty : <p className="text-sm text-ink">Answered: {answer.trueFalseAnswer ? 'True' : 'False'}</p>
  }
  return answer.text ? <p className="rounded-panel border border-line bg-surface p-3 text-sm break-words whitespace-pre-line text-ink">{answer.text}</p> : empty
}

/** The question's own answers, as faculty read them. */
function AnswerKey({ question }: { question: ReviewQuestion }) {
  switch (question.type) {
    case 'TRUE_FALSE':
      return <p className="text-sm text-ink-muted">Correct answer: {question.trueFalseAnswer ? 'True' : 'False'}</p>
    case 'SHORT_ANSWER':
      return <p className="text-sm text-ink-muted">Accepted answers: {question.options.map((option) => option.text).join(' · ')}</p>
    case 'DESCRIPTIVE':
      return question.answerGuidance ? (
        <p className="text-sm break-words whitespace-pre-line text-ink-muted">Guidance: {question.answerGuidance}</p>
      ) : null
    default:
      return (
        <p className="text-sm text-ink-muted">
          Correct: {question.options.filter((option) => option.correct).map((option) => option.text).join(' · ')}
        </p>
      )
  }
}

/** Marks and feedback for one answered question: needed for a written answer, a regrade for any other. */
function MarkForm({ review, question, onSaved }: { review: QuizAttemptReview; question: ReviewQuestion; onSaved: (message: string) => void }) {
  const [marks, setMarks] = useState(question.mark ? String(question.mark.marks) : '')
  const [feedback, setFeedback] = useState(question.mark?.feedback ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const typed = marks.trim()
    if (!MARKS_PATTERN.test(typed) || Number(typed) > question.marks) {
      setError(`Enter marks from 0 to ${question.marks}, with at most 2 decimal places.`)
      return
    }
    setError(null)
    setBusy(true)
    try {
      await markQuizAnswer(review.attemptId, question.quizQuestionId, Number(typed), feedback.trim() === '' ? null : feedback)
      onSaved(`Saved the marks for question ${question.number}.`)
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t save the marks. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="grid gap-3 sm:grid-cols-[10rem_1fr_auto] sm:items-end" onSubmit={(event) => void save(event)} noValidate>
      <TextField
        label={`Marks for question ${question.number}`}
        inputMode="decimal"
        value={marks}
        error={error}
        hint={`Out of ${question.marks}`}
        onChange={(event) => setMarks(event.target.value)}
      />
      <TextAreaField
        label={`Feedback for question ${question.number} (optional)`}
        rows={2}
        value={feedback}
        maxLength={10_000}
        onChange={(event) => setFeedback(event.target.value)}
      />
      <Button type="submit" variant="secondary" disabled={busy}>
        {busy ? 'Saving…' : question.mark ? 'Update marks' : 'Save marks'}
        <span className="sr-only">{` for question ${question.number}`}</span>
      </Button>
    </form>
  )
}

/**
 * One attempt for marking: each question with its answers, the student's answer, the automatic marks, and the
 * teacher's marks and feedback. Written answers need marks; any other can be regraded. Saving makes the result count;
 * students see it under the quiz's result settings. An attempt left open past its time can be closed first.
 */
export function QuizAttemptMarking({ lmsCourseId, attemptId }: { lmsCourseId: number; attemptId: number }) {
  const queryClient = useQueryClient()
  const review = useApiQuery(queryKeys.lms.quizAttemptReview(attemptId), (signal) => getQuizAttemptReview(attemptId, signal))
  const [announcement, setAnnouncement] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (review.status !== 'loaded') {
    return (
      <Card title="Quiz attempt">
        {review.status === 'notFound' ? (
          <EmptyState title="Attempt unavailable" description="This attempt could not be found or is not in a course you teach." />
        ) : (
          <SectionState request={review} what="the attempt" />
        )}
      </Card>
    )
  }
  const data = review.data
  const handedIn = data.state === 'SUBMITTED' || data.state === 'EXPIRED'

  async function saved(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  async function close() {
    setError(null)
    try {
      await closeQuizAttempt(data.attemptId)
      await saved('Closed the attempt. Its saved answers were handed in.')
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t close the attempt. Try again.'))
    }
  }

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card
        title={`${data.quizTitle}: ${data.studentLoginId}, attempt ${data.attemptNumber}`}
        action={
          <ButtonLink to={`/teaching/${lmsCourseId}/quizzes/${data.quizId}/results`} variant="secondary" size="sm">
            All results
          </ButtonLink>
        }
      >
        <div className="space-y-2 text-sm">
          <p className="flex flex-wrap items-center gap-2">
            <Badge tone={handedIn ? 'success' : 'info'}>{attemptStateLabel(data.state)}</Badge>
            <span className="text-ink-muted">
              Started {formatDateTime(data.startedAt)}
              {data.submittedAt ? ` · handed in ${formatDateTime(data.submittedAt)}${data.late ? ' (late)' : ''}` : ''}
            </span>
          </p>
          <p className="text-ink">
            Automatic {data.automaticMarks} · Teacher {data.teacherMarks} · Score {data.score === null ? 'not yet complete' : `${data.score} / ${data.totalMarks}`}
          </p>
          {Number(data.negativeMarkPercent) > 0 && (
            <p className="text-ink-muted">Wrong objective answers lose {data.negativeMarkPercent}% of their marks; the score never goes below zero.</p>
          )}
          {data.state === 'TIME_UP' && data.quizPublished && (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-ink">The student’s time is up but the attempt was not handed in.</p>
              <Button variant="secondary" size="sm" onClick={() => void close()}>
                Close attempt
              </Button>
            </div>
          )}
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      </Card>

      <Card title="Answers" padding="none">
        <ol className="divide-y divide-line">
          {data.questions.map((question) => (
            <li key={question.quizQuestionId} className="space-y-3 px-4 py-5 sm:px-5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-ink">Question {question.number}</span>
                <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
                <Badge tone={question.outcome === 'CORRECT' ? 'success' : question.outcome === 'NEEDS_MARKING' ? 'warning' : question.outcome === 'INCORRECT' ? 'danger' : 'neutral'}>
                  {outcomeLabel(question.outcome)}
                </Badge>
                <span className="text-sm text-ink-muted">
                  {question.mark ? `${question.mark.marks} (teacher)` : question.automaticMarks === null ? '—' : `${question.automaticMarks} (automatic)`} of{' '}
                  {question.marks}
                </span>
              </div>
              <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={3} />
              <StudentAnswer question={question} />
              <AnswerKey question={question} />
              {question.mark?.feedback && (
                <p className="text-sm break-words whitespace-pre-line text-ink">
                  <span className="font-medium">Feedback:</span> {question.mark.feedback}
                </p>
              )}
              {handedIn && data.quizPublished && question.outcome !== 'UNANSWERED' && (
                <MarkForm key={`${question.quizQuestionId}-${question.mark?.markedAt ?? ''}`} review={data} question={question} onSaved={(message) => void saved(message)} />
              )}
            </li>
          ))}
        </ol>
      </Card>
    </div>
  )
}
