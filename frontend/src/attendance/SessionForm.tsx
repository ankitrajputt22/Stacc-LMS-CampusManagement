import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { Button } from '../components/Button'
import { fromLocalInputValue, localTimeZoneName, toLocalInputValue } from '../courses/dates'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AttendanceSessionInput } from '../types/attendance'

type Field = 'heldAt' | 'topic'

// The years the backend accepts, and the longest topic it keeps.
const EARLIEST_YEAR = 2000
const LATEST_YEAR = 9999
export const TOPIC_MAX = 500

type SessionFormProps = {
  heading: string
  /** The session's current values when editing; a new session starts at the current minute. */
  initial?: { heldAt: string; topic: string | null }
  submitLabel: string
  onSubmit: (input: AttendanceSessionInput) => Promise<void>
  onCancel: () => void
}

function refusal(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 403:
      return 'You don’t have access to attendance. If your access changed recently, sign out and sign in again.'
    case 404:
      return 'This course or session is no longer available.'
    case 409:
      return apiMessage ?? 'This session is finalized and can no longer be changed.'
    default:
      return 'Couldn’t save the session. Check your connection and try again.'
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
 * When a class was held and an optional plain-text topic. The time is entered in the browser's own time zone
 * and sent as an absolute moment. Obvious mistakes are caught before sending, and the backend checks again.
 * A refusal keeps what was entered; a 403 never signs the user out.
 */
export function SessionForm({ heading, initial, submitLabel, onSubmit, onCancel }: SessionFormProps) {
  const id = useId()
  const heldAtRef = useRef<HTMLInputElement>(null)
  const topicRef = useRef<HTMLInputElement>(null)
  const submittingRef = useRef(false)
  const [heldAt, setHeldAt] = useState(() => toLocalInputValue(initial?.heldAt ?? new Date().toISOString()))
  const [topic, setTopic] = useState(initial?.topic ?? '')
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    heldAtRef.current?.focus()
  }, [])

  function focusFirst(found: Partial<Record<string, string>>) {
    if (found.heldAt) {
      heldAtRef.current?.focus()
    } else if (found.topic) {
      topicRef.current?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found: Partial<Record<Field, string>> = {}
    const moment = fromLocalInputValue(heldAt)
    if (heldAt === '' || moment === null) {
      found.heldAt = 'Choose the date and time of the class.'
    } else if (moment.getFullYear() < EARLIEST_YEAR || moment.getFullYear() > LATEST_YEAR) {
      found.heldAt = `Choose a date between the years ${EARLIEST_YEAR} and ${LATEST_YEAR}.`
    }
    if (topic.trim().length > TOPIC_MAX) {
      found.topic = `Use at most ${TOPIC_MAX} characters.`
    }
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0 || moment === null) {
      focusFirst(found)
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    try {
      await onSubmit({ heldAt: moment.toISOString(), topic: topic.trim() === '' ? null : topic.trim() })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        // A 401 is left to the shared client, which ends the session.
        const apiError = getApiError(failure)
        const fieldErrors: Partial<Record<Field, string>> = Object.fromEntries(
          (apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]),
        )
        const hasFieldErrors = status === 400 && Object.keys(fieldErrors).length > 0
        setErrors(fieldErrors)
        setFormError(hasFieldErrors ? 'Check the highlighted field.' : refusal(status, apiError?.message ?? null))
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
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold text-ink">
        {heading}
      </p>

      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={`${id}-heldAt`} className="block text-sm font-medium text-ink">
            Date and time
          </label>
          <p id={`${id}-heldAt-hint`} className="mt-0.5 text-xs text-ink-muted">
            When the class was held, in your local time ({localTimeZoneName()}).
          </p>
          <input
            ref={heldAtRef}
            id={`${id}-heldAt`}
            type="datetime-local"
            value={heldAt}
            min="2000-01-01T00:00"
            max="9999-12-31T23:59"
            onChange={(event) => {
              setHeldAt(event.target.value)
              setErrors((current) => ({ ...current, heldAt: undefined }))
            }}
            aria-invalid={errors.heldAt ? true : undefined}
            aria-describedby={describedBy('heldAt', `${id}-heldAt-hint`)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.heldAt))}`}
          />
          <FieldError id={`${id}-heldAt-error`} message={errors.heldAt} />
        </div>

        <div>
          <label htmlFor={`${id}-topic`} className="block text-sm font-medium text-ink">
            Topic (optional)
          </label>
          <p id={`${id}-topic-hint`} className="mt-0.5 text-xs text-ink-muted">
            Plain text, such as “Arrays introduction”.
          </p>
          <input
            ref={topicRef}
            id={`${id}-topic`}
            type="text"
            value={topic}
            maxLength={TOPIC_MAX}
            onChange={(event) => {
              setTopic(event.target.value)
              setErrors((current) => ({ ...current, topic: undefined }))
            }}
            aria-invalid={errors.topic ? true : undefined}
            aria-describedby={describedBy('topic', `${id}-topic-hint`)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.topic))}`}
          />
          <FieldError id={`${id}-topic-error`} message={errors.topic} />
        </div>
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {formError}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={submitting || undefined}>
          {submitting ? 'Saving…' : submitLabel}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
