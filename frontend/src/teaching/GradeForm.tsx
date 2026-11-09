import { type FormEvent, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { gradeSubmissionAttempt } from '../api/lmsGradingApi'
import { Button } from '../components/Button'
import { formatMarks } from '../courses/marks'
import type { LmsFacultySubmissionAttempt, LmsSubmissionGrade } from '../types/lms'
import { border, fieldClasses } from './formStyles'

type Field = 'marksAwarded' | 'feedback'

// The longest feedback the backend keeps, and the marks its column holds: six digits and two decimals.
const FEEDBACK_MAX = 10_000
const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

const KEPT = 'Your marks and feedback are still here.'

type GradeFormProps = {
  attempt: LmsFacultySubmissionAttempt
  /** What this attempt is graded out of: its existing grade's maximum, or the assignment's for a first grade. */
  outOf: number
  onSaved: (grade: LmsSubmissionGrade) => void
}

function checkedMarks(marks: string, outOf: number): string | null {
  if (marks === '') {
    return 'Enter the marks awarded.'
  }
  if (marks.startsWith('-')) {
    return 'Marks cannot be negative.'
  }
  if (!MARKS_PATTERN.test(marks)) {
    return 'Enter a number such as 18 or 12.5, with at most 2 decimal places.'
  }
  if (Number(marks) > outOf) {
    return `Marks cannot exceed ${formatMarks(outOf)}.`
  }
  return null
}

function refusal(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
      return apiMessage ?? `Couldn’t save the grade. ${KEPT}`
    case 403:
      return 'You don’t have permission to grade this assignment. If your access changed recently, sign out and sign in again.'
    case 404:
      return 'This attempt is no longer available for grading.'
    default:
      return `Couldn’t save the grade. Check your connection and try again. ${KEPT}`
  }
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} className="mt-1.5 text-sm text-danger">
      {message}
    </p>
  ) : null
}

/**
 * Marks and optional plain-text feedback for one attempt. Saving makes the grade visible to the student at
 * once; saving again replaces it, and only the latest grade is kept. Obvious mistakes are caught before
 * sending, and the backend checks access, the permission, and the marks again. After a failure what was
 * typed stays, and a 403 never signs the user out.
 */
export function GradeForm({ attempt, outOf, onSaved }: GradeFormProps) {
  const id = useId()
  const marksRef = useRef<HTMLInputElement>(null)
  const feedbackRef = useRef<HTMLTextAreaElement>(null)
  const submittingRef = useRef(false)
  const [marks, setMarks] = useState(attempt.grade ? String(attempt.grade.marksAwarded) : '')
  const [feedback, setFeedback] = useState(attempt.grade?.feedback ?? '')
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const isRegrade = attempt.grade !== null

  function clearError(field: Field) {
    setErrors((current) => {
      if (!current[field]) {
        return current
      }
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  function focusFirst(found: Partial<Record<string, string>>) {
    if (found.marksAwarded) {
      marksRef.current?.focus()
    } else if (found.feedback) {
      feedbackRef.current?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }

    const value = marks.trim()
    const found: Partial<Record<Field, string>> = {}
    const marksError = checkedMarks(value, outOf)
    if (marksError) {
      found.marksAwarded = marksError
    }
    if (feedback.length > FEEDBACK_MAX) {
      found.feedback = `Use at most ${FEEDBACK_MAX.toLocaleString('en')} characters.`
    }
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0) {
      focusFirst(found)
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    try {
      const grade = await gradeSubmissionAttempt(attempt.attemptId, {
        marksAwarded: Number(value),
        feedback: feedback.trim() === '' ? null : feedback,
      })
      onSaved(grade)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        // A 401 is left to the shared client, which ends the session.
        const apiError = getApiError(failure)
        const fieldErrors: Partial<Record<Field, string>> = Object.fromEntries(
          (apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]),
        )
        const hasFieldErrors = status === 400 && Object.keys(fieldErrors).length > 0
        setErrors(hasFieldErrors ? fieldErrors : {})
        setFormError(hasFieldErrors ? `Check the highlighted field. ${KEPT}` : refusal(status, apiError?.message ?? null))
        focusFirst(fieldErrors)
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  function describedBy(field: Field, hint: string): string {
    return errors[field] ? `${hint} ${id}-${field}-error` : hint
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      className="mt-3 rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold text-ink">
        {isRegrade ? 'Change the grade' : 'Grade this attempt'}
        <span className="sr-only">, attempt {attempt.attemptNumber}</span>
      </p>
      <p className="mt-1 text-sm leading-6 text-ink-muted">Saving this grade makes it visible to the student.</p>

      <div className="mt-3">
        <label htmlFor={`${id}-marks`} className="block text-sm font-medium text-ink">
          Marks awarded
        </label>
        <p id={`${id}-marks-hint`} className="mt-0.5 text-xs text-ink-muted">
          Out of {formatMarks(outOf)}, such as 18 or 12.5.
        </p>
        <div className="mt-1.5 flex items-center gap-2">
          <input
            ref={marksRef}
            id={`${id}-marks`}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={marks}
            maxLength={9}
            onChange={(event) => {
              setMarks(event.target.value)
              clearError('marksAwarded')
            }}
            aria-invalid={errors.marksAwarded ? true : undefined}
            aria-describedby={describedBy('marksAwarded', `${id}-marks-hint`)}
            className={`h-11 w-28 rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:text-sm ${border(Boolean(errors.marksAwarded))}`}
          />
          <span aria-hidden="true" className="text-sm whitespace-nowrap text-ink">
            / {formatMarks(outOf)}
          </span>
        </div>
        <FieldError id={`${id}-marksAwarded-error`} message={errors.marksAwarded} />
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-feedback`} className="block text-sm font-medium text-ink">
          Feedback
        </label>
        <p id={`${id}-feedback-hint`} className="mt-0.5 text-xs text-ink-muted">
          Optional. Plain text; line breaks are kept.
        </p>
        <textarea
          ref={feedbackRef}
          id={`${id}-feedback`}
          value={feedback}
          rows={5}
          maxLength={FEEDBACK_MAX}
          onChange={(event) => {
            setFeedback(event.target.value)
            clearError('feedback')
          }}
          aria-invalid={errors.feedback ? true : undefined}
          aria-describedby={describedBy('feedback', `${id}-feedback-hint`)}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(errors.feedback))}`}
        />
        <FieldError id={`${id}-feedback-error`} message={errors.feedback} />
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {formError}
          </p>
        )}
      </div>

      <div className="mt-4">
        <Button type="submit" aria-disabled={submitting || undefined}>
          {submitting ? 'Saving…' : isRegrade ? 'Update grade' : 'Save grade'}
        </Button>
      </div>
    </form>
  )
}
