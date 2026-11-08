import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { saveQuizAnswer, submitQuizAttempt } from '../api/lmsQuizAttemptsApi'
import { queryKeys } from '../api/queryKeys'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { FormattedText } from '../formatting/FormattedText'
import type { QuizAttempt, QuizAttemptQuestion } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, TextAreaField, TextField } from '../ui'
import { formatDateTime } from './dates'
import {
  type AnswerDraft,
  attemptStateLabel,
  clockText,
  draftOfQuestion,
  inputOfDraft,
  isAnswered,
  outcomeLabel,
  resultWaitText,
} from './quizText'

type SaveState = 'saving' | 'saved' | 'failed'

/** How long typing must pause before a written answer is saved. Choices are saved at once. */
const TEXT_SAVE_DELAY_MS = 1200
const SHORT_ANSWER_MAX = 500
const WRITTEN_ANSWER_MAX = 20_000

function marksText(marks: number): string {
  return `${marks} ${marks === 1 ? 'mark' : 'marks'}`
}

function saveStateText(state: SaveState | undefined): string {
  switch (state) {
    case 'saving':
      return 'Saving…'
    case 'saved':
      return 'Saved'
    case 'failed':
      return 'Not saved. Check your connection, then change the answer to try again.'
    default:
      return ''
  }
}

type QuestionResult = NonNullable<QuizAttempt['result']>['questions'][number]

/** A released result for one question: the outcome and marks, the teacher's feedback, and, if shown, the answer. */
function QuestionResultPanel({ question, result }: { question: QuizAttemptQuestion; result: QuestionResult }) {
  const correctChoices = (result.correctOptions ?? [])
    .map((position) => question.options.find((option) => option.position === position)?.text)
    .filter((text): text is string => Boolean(text))
  return (
    <div className="mt-3 space-y-1.5 rounded-panel border border-line bg-surface p-3 text-sm">
      <p className="flex flex-wrap items-center gap-2">
        <Badge tone={result.outcome === 'CORRECT' ? 'success' : result.outcome === 'INCORRECT' ? 'danger' : 'neutral'}>
          {outcomeLabel(result.outcome)}
        </Badge>
        <span className="text-ink">
          {result.marksAwarded} of {marksText(question.marks)}
        </span>
      </p>
      {correctChoices.length > 0 && <p className="text-ink">Correct answer: {correctChoices.join(' · ')}</p>}
      {result.acceptedAnswers && <p className="text-ink">Accepted answers: {result.acceptedAnswers.join(' · ')}</p>}
      {result.trueFalseAnswer !== null && question.type === 'TRUE_FALSE' && (
        <p className="text-ink">Correct answer: {result.trueFalseAnswer ? 'True' : 'False'}</p>
      )}
      {result.feedback && (
        <p className="break-words whitespace-pre-line text-ink">
          <span className="font-medium">Feedback:</span> {result.feedback}
        </p>
      )}
      {result.explanation && (
        <div className="text-ink-muted">
          <p className="font-medium">Explanation</p>
          <FormattedText text={result.explanation} format={question.textFormat} className="leading-6" headingLevel={4} />
        </div>
      )}
    </div>
  )
}

type QuestionProps = {
  question: QuizAttemptQuestion
  result?: QuestionResult
  draft: AnswerDraft
  open: boolean
  saveState: SaveState | undefined
  onChoose: (draft: AnswerDraft) => void
  onType: (draft: AnswerDraft) => void
  onLeaveText: () => void
}

/** One question with the inputs its type takes, disabled once the attempt no longer takes answers. */
function QuizQuestion({ question, result, draft, open, saveState, onChoose, onType, onLeaveText }: QuestionProps) {
  const id = useId()
  const name = `${id}-answer`
  const label = `Answer to question ${question.number}`
  return (
    <li className="px-4 py-5 sm:px-5">
      <fieldset className="space-y-3" disabled={!open}>
        <legend className="flex flex-wrap items-center gap-2 text-sm font-semibold text-ink">
          Question {question.number}
          <span className="font-normal text-ink-muted">({marksText(question.marks)})</span>
          {question.type === 'MULTIPLE_CHOICE' && <span className="font-normal text-ink-muted">Choose every correct answer.</span>}
        </legend>
        <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={3} />
        {question.type === 'SINGLE_CHOICE' &&
          question.options.map((option) => (
            <label key={option.position} className="flex items-start gap-2 text-sm text-ink">
              <input
                type="radio"
                name={name}
                className="mt-1"
                checked={draft.selected.includes(option.position)}
                onChange={() => onChoose({ ...draft, selected: [option.position] })}
              />
              <span className="break-words">{option.text}</span>
            </label>
          ))}
        {question.type === 'MULTIPLE_CHOICE' &&
          question.options.map((option) => (
            <label key={option.position} className="flex items-start gap-2 text-sm text-ink">
              <input
                type="checkbox"
                className="mt-1"
                checked={draft.selected.includes(option.position)}
                onChange={(event) =>
                  onChoose({
                    ...draft,
                    selected: event.target.checked
                      ? [...draft.selected, option.position]
                      : draft.selected.filter((position) => position !== option.position),
                  })
                }
              />
              <span className="break-words">{option.text}</span>
            </label>
          ))}
        {question.type === 'TRUE_FALSE' &&
          [true, false].map((value) => (
            <label key={String(value)} className="flex items-center gap-2 text-sm text-ink">
              <input type="radio" name={name} checked={draft.trueFalse === value} onChange={() => onChoose({ ...draft, trueFalse: value })} />
              {value ? 'True' : 'False'}
            </label>
          ))}
        {question.type === 'SHORT_ANSWER' && (
          <TextField
            label={label}
            value={draft.text}
            maxLength={SHORT_ANSWER_MAX}
            onChange={(event) => onType({ ...draft, text: event.target.value })}
            onBlur={onLeaveText}
          />
        )}
        {question.type === 'DESCRIPTIVE' && (
          <TextAreaField
            label={label}
            rows={6}
            value={draft.text}
            maxLength={WRITTEN_ANSWER_MAX}
            hint="Plain text. Line breaks are kept."
            onChange={(event) => onType({ ...draft, text: event.target.value })}
            onBlur={onLeaveText}
          />
        )}
        {open && (question.type === 'SINGLE_CHOICE' || question.type === 'TRUE_FALSE') && isAnswered(question, draft) && (
          <Button variant="secondary" size="sm" onClick={() => onChoose({ selected: [], trueFalse: null, text: '' })}>
            Clear answer<span className="sr-only"> to question {question.number}</span>
          </Button>
        )}
      </fieldset>
      {result && <QuestionResultPanel question={question} result={result} />}
      {open && saveState && (
        <p className={`mt-2 text-sm ${saveState === 'failed' ? 'text-danger' : 'text-ink-muted'}`}>{saveStateText(saveState)}</p>
      )}
    </li>
  )
}

/**
 * Taking one attempt: every question with its answer, saved as the student goes (choices at once, writing after a
 * short pause or on leaving the field), a timer from the server's deadline, and Submit quiz, confirmed first. When the
 * timer runs out, the saved answers are handed in. The server decides every deadline; the timer only shows it. Once
 * handed in, the answers are shown read-only.
 */
export function QuizTaking({ attempt, lmsCourseId }: { attempt: QuizAttempt; lmsCourseId: number }) {
  const queryClient = useQueryClient()
  const [current, setCurrent] = useState(attempt)
  const open = current.state === 'IN_PROGRESS'
  const [drafts, setDrafts] = useState<Record<number, AnswerDraft>>(() =>
    Object.fromEntries(attempt.questions.map((question) => [question.quizQuestionId, draftOfQuestion(question)])),
  )
  const [saveStates, setSaveStates] = useState<Record<number, SaveState>>({})
  const [notice, setNotice] = useState<{ tone: 'success' | 'error' | 'info'; text: string } | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const chains = useRef(new Map<number, Promise<void>>())
  const pendingText = useRef(new Map<number, { timer: ReturnType<typeof setTimeout>; draft: AnswerDraft }>())
  const handingIn = useRef(false)

  // The device's clock may be off; the timer counts from the server's time.
  const [clock, setClock] = useState(() => ({
    offset: Date.parse(attempt.serverTime) - Date.now(),
    now: Date.parse(attempt.serverTime),
  }))
  const now = clock.now
  const deadline = current.deadlineAt ? Date.parse(current.deadlineAt) : null
  const remaining = deadline === null ? null : (deadline - now) / 1000

  const questionsById = new Map(current.questions.map((question) => [question.quizQuestionId, question]))
  const answered = current.questions.filter((question) => isAnswered(question, drafts[question.quizQuestionId])).length

  function queueSave(quizQuestionId: number, draft: AnswerDraft): Promise<void> {
    const question = questionsById.get(quizQuestionId)
    if (!question) {
      return Promise.resolve()
    }
    setSaveStates((states) => ({ ...states, [quizQuestionId]: 'saving' }))
    // One save at a time per question, in order, so an older answer never lands after a newer one.
    const next = (chains.current.get(quizQuestionId) ?? Promise.resolve()).then(async () => {
      try {
        await saveQuizAnswer(current.id, quizQuestionId, inputOfDraft(question, draft))
        setSaveStates((states) => ({ ...states, [quizQuestionId]: 'saved' }))
      } catch (failure) {
        setSaveStates((states) => ({ ...states, [quizQuestionId]: 'failed' }))
        if (getHttpStatus(failure) === 409) {
          // Time is up or the attempt was handed in elsewhere: hand in what was saved and show the result.
          void handInRef.current(true)
        }
      }
    })
    chains.current.set(quizQuestionId, next)
    return next
  }

  function choose(quizQuestionId: number, draft: AnswerDraft) {
    setDrafts((all) => ({ ...all, [quizQuestionId]: draft }))
    void queueSave(quizQuestionId, draft)
  }

  function type(quizQuestionId: number, draft: AnswerDraft) {
    setDrafts((all) => ({ ...all, [quizQuestionId]: draft }))
    const waiting = pendingText.current.get(quizQuestionId)
    if (waiting) {
      clearTimeout(waiting.timer)
    }
    const timer = setTimeout(() => flushText(quizQuestionId), TEXT_SAVE_DELAY_MS)
    pendingText.current.set(quizQuestionId, { timer, draft })
  }

  function flushText(quizQuestionId: number): Promise<void> {
    const waiting = pendingText.current.get(quizQuestionId)
    if (!waiting) {
      return chains.current.get(quizQuestionId) ?? Promise.resolve()
    }
    clearTimeout(waiting.timer)
    pendingText.current.delete(quizQuestionId)
    return queueSave(quizQuestionId, waiting.draft)
  }

  async function handIn(automatic: boolean) {
    if (handingIn.current) {
      return
    }
    handingIn.current = true
    setConfirming(false)
    await Promise.all([...pendingText.current.keys()].map((quizQuestionId) => flushText(quizQuestionId)))
    await Promise.all(chains.current.values())
    try {
      const done = await submitQuizAttempt(current.id)
      setCurrent(done)
      setNotice(
        done.state === 'SUBMITTED'
          ? { tone: 'success', text: automatic ? 'Time is up. Your saved answers were submitted.' : 'Quiz submitted successfully.' }
          : { tone: 'info', text: 'Time is up. The answers saved before the deadline were handed in.' },
      )
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    } catch (failure) {
      handingIn.current = false
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const message = getApiError(failure)?.message
        setNotice({
          tone: 'error',
          text: message && status !== null && status < 500 ? message : 'Couldn’t submit the quiz. Check your connection and try again.',
        })
      }
    }
  }

  const handInRef = useRef(handIn)
  useEffect(() => {
    handInRef.current = handIn
  })

  useEffect(() => {
    if (!open || deadline === null) {
      return undefined
    }
    const tick = setInterval(() => setClock((current) => ({ ...current, now: Date.now() + current.offset })), 1000)
    return () => clearInterval(tick)
  }, [open, deadline])

  // Hand in once the server's deadline has passed on the timer, and say so at five minutes and one minute left.
  const warned = useRef<number | null>(null)
  useEffect(() => {
    if (!open || remaining === null) {
      return
    }
    if (remaining <= 0) {
      void handInRef.current(true)
    } else if (remaining <= 60 && warned.current !== 60) {
      warned.current = 60
      setAnnouncement('One minute left.')
    } else if (remaining <= 300 && remaining > 60 && warned.current === null) {
      warned.current = 300
      setAnnouncement('Five minutes left.')
    }
  }, [open, remaining])

  // Unsaved writing is saved when the page is left inside the app.
  const flushTextRef = useRef(flushText)
  useEffect(() => {
    flushTextRef.current = flushText
  })
  useEffect(() => {
    const pending = pendingText.current
    return () => {
      for (const quizQuestionId of [...pending.keys()]) {
        void flushTextRef.current(quizQuestionId)
      }
    }
  }, [])

  const quizPath = `/courses/${lmsCourseId}/quizzes/${current.quizId}`
  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card
        title={current.quizTitle}
        description={`Attempt ${current.attemptNumber} · ${current.questions.length} ${current.questions.length === 1 ? 'question' : 'questions'} · ${marksText(current.totalMarks)}`}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone={open ? 'info' : current.state === 'SUBMITTED' ? 'success' : 'neutral'}>{attemptStateLabel(current.state)}</Badge>
          {open && remaining !== null && (
            <p className={`text-lg font-semibold tabular-nums ${remaining <= 60 ? 'text-danger' : 'text-ink'}`}>
              <span className="sr-only">Time left: </span>
              <span role="timer" aria-hidden="true">
                Time left {clockText(remaining)}
              </span>
            </p>
          )}
          {open && remaining === null && <p className="text-sm text-ink-muted">No time limit.</p>}
          {current.submittedAt && (
            <p className="text-sm text-ink-muted">
              Handed in {formatDateTime(current.submittedAt)}
              {current.late ? ' (after the due date)' : ''}
            </p>
          )}
        </div>
        {open && deadline !== null && (
          <p className="mt-2 text-sm text-ink-muted">
            Answers are saved as you go. At {formatDateTime(current.deadlineAt!)} the saved answers are handed in.
          </p>
        )}
        {notice && (
          <Alert tone={notice.tone} className="mt-4">
            {notice.text}
          </Alert>
        )}
        {current.result && (
          <div className="mt-4 rounded-panel border border-line p-4">
            <p className="text-2xl font-bold text-portal-navy">
              {current.result.score} / {current.result.totalMarks}
            </p>
            <p className="text-sm text-ink-muted">
              {current.result.correct} correct · {current.result.incorrect} incorrect · {current.result.unanswered} not answered
            </p>
          </div>
        )}
        {!open && resultWaitText(current.resultStatus) && (
          <p className="mt-4 text-sm text-ink-muted">{resultWaitText(current.resultStatus)}</p>
        )}
        {!open && !notice && (
          <Alert tone="info" className="mt-4">
            {current.state === 'TIME_UP'
              ? 'Time is up for this attempt. The answers saved before the deadline count.'
              : 'This attempt has been handed in. Your answers are shown below.'}
          </Alert>
        )}
      </Card>

      <Card title="Questions" padding="none">
        <ol className="divide-y divide-line">
          {current.questions.map((question) => (
            <QuizQuestion
              key={question.quizQuestionId}
              question={question}
              result={current.result?.questions.find((entry) => entry.quizQuestionId === question.quizQuestionId)}
              draft={drafts[question.quizQuestionId]}
              open={open}
              saveState={saveStates[question.quizQuestionId]}
              onChoose={(draft) => choose(question.quizQuestionId, draft)}
              onType={(draft) => type(question.quizQuestionId, draft)}
              onLeaveText={() => void flushText(question.quizQuestionId)}
            />
          ))}
        </ol>
      </Card>

      <Card>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-ink">
            {answered} of {current.questions.length} {current.questions.length === 1 ? 'question' : 'questions'} answered.
          </p>
          {open || current.state === 'TIME_UP' ? (
            <Button onClick={() => setConfirming(true)}>Submit quiz</Button>
          ) : (
            <ButtonLink to={quizPath} variant="secondary">
              Back to the quiz
            </ButtonLink>
          )}
        </div>
        {confirming && (
          <StatusConfirmation
            question="Submit your answers?"
            detail={`${answered} of ${current.questions.length} questions are answered. After submitting, the answers can no longer change.`}
            confirmLabel="Submit"
            busyLabel="Submitting…"
            describeRefusal={() => 'Couldn’t submit the quiz. Try again.'}
            onCancel={() => setConfirming(false)}
            onConfirm={() => handIn(false)}
          />
        )}
      </Card>
    </div>
  )
}
