import { type FormEvent, type RefObject, useEffect, useId, useRef, useState } from 'react'
import { Button } from '../components/Button'
import { fromLocalInputValue, localTimeZoneName, toLocalInputValue } from '../courses/dates'
import type { LmsAssignmentInput } from '../types/lms'
import type { FormOutcome } from './ContentForm'
import { ASSIGNMENT_INSTRUCTIONS_MAX, TITLE_MAX } from './editorState'
import { border, fieldClasses } from './formStyles'

type Field = 'title' | 'instructions' | 'dueAt' | 'maxMarks'

type AssignmentFormProps = {
  heading: string
  /** A short neutral note shown above the fields. */
  note?: string
  initial?: { title: string; instructions: string; dueAt: string; maxMarks: number }
  submitLabel: string
  submittingLabel: string
  onSubmit: (input: LmsAssignmentInput) => Promise<FormOutcome>
  onCancel: () => void
}

// The years the backend accepts, and the most marks its column holds: six digits and two decimals.
const EARLIEST_YEAR = 2000
const LATEST_YEAR = 9999
const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

const FIELD_ORDER: readonly Field[] = ['title', 'instructions', 'dueAt', 'maxMarks']

function checked(title: string, instructions: string, dueAt: string, maxMarks: string) {
  const errors: Partial<Record<Field, string>> = {}
  if (title.trim() === '') {
    errors.title = 'Enter a title.'
  } else if (title.trim().length > TITLE_MAX) {
    errors.title = `Use at most ${TITLE_MAX} characters.`
  }
  if (instructions.trim() === '') {
    errors.instructions = 'Enter the instructions.'
  } else if (instructions.trim().length > ASSIGNMENT_INSTRUCTIONS_MAX) {
    errors.instructions = `Use at most ${ASSIGNMENT_INSTRUCTIONS_MAX.toLocaleString('en')} characters.`
  }
  const due = fromLocalInputValue(dueAt)
  if (dueAt === '' || due === null) {
    errors.dueAt = 'Choose a due date and time.'
  } else if (due.getFullYear() < EARLIEST_YEAR || due.getFullYear() > LATEST_YEAR) {
    errors.dueAt = `Choose a due date between the years ${EARLIEST_YEAR} and ${LATEST_YEAR}.`
  }
  const marks = maxMarks.trim()
  if (marks === '') {
    errors.maxMarks = 'Enter the maximum marks.'
  } else if (!MARKS_PATTERN.test(marks) || Number(marks) <= 0) {
    errors.maxMarks = 'Enter a number greater than zero, with at most 2 decimal places, below 1,000,000.'
  }
  return { errors, due, marks }
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} className="mt-1.5 text-sm text-danger">
      {message}
    </p>
  ) : null
}

/**
 * A contained form for an assignment's definition. The deadline is entered in the browser's own time zone
 * and sent as an absolute moment; the instructions are plain text. Obvious mistakes are caught before
 * sending, and the backend checks everything again.
 */
export function AssignmentForm({
  heading,
  note,
  initial,
  submitLabel,
  submittingLabel,
  onSubmit,
  onCancel,
}: AssignmentFormProps) {
  const id = useId()
  const titleRef = useRef<HTMLInputElement>(null)
  const instructionsRef = useRef<HTMLTextAreaElement>(null)
  const dueAtRef = useRef<HTMLInputElement>(null)
  const maxMarksRef = useRef<HTMLInputElement>(null)
  const refs: Record<Field, RefObject<HTMLElement | null>> = {
    title: titleRef,
    instructions: instructionsRef,
    dueAt: dueAtRef,
    maxMarks: maxMarksRef,
  }
  const [title, setTitle] = useState(initial?.title ?? '')
  const [instructions, setInstructions] = useState(initial?.instructions ?? '')
  const [dueAt, setDueAt] = useState(initial ? toLocalInputValue(initial.dueAt) : '')
  const [maxMarks, setMaxMarks] = useState(initial ? String(initial.maxMarks) : '')
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    titleRef.current?.focus()
  }, [])

  // A message stays until its field is changed, so a corrected field no longer says it is wrong.
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
    const first = FIELD_ORDER.find((field) => found[field])
    if (first) {
      refs[first].current?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }

    const { errors: found, due, marks } = checked(title, instructions, dueAt, maxMarks)
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0 || due === null) {
      focusFirst(found)
      return
    }

    setSubmitting(true)
    const outcome = await onSubmit({ title, instructions, dueAt: due.toISOString(), maxMarks: Number(marks) })
    // On success the editor closes this form, so nothing more is updated here.
    if (!outcome.ok) {
      setSubmitting(false)
      setErrors(outcome.fieldErrors)
      setFormError(outcome.message)
      focusFirst(outcome.fieldErrors)
    }
  }

  function describedBy(field: Field, hint?: string): string | undefined {
    const ids = [hint, errors[field] ? `${id}-${field}-error` : undefined].filter(Boolean)
    return ids.length > 0 ? ids.join(' ') : undefined
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

      {note && <p className="mt-1 text-sm leading-6 text-ink-muted">{note}</p>}

      <div className="mt-3">
        <label htmlFor={`${id}-title`} className="block text-sm font-medium text-ink">
          Title
        </label>
        <input
          ref={titleRef}
          id={`${id}-title`}
          type="text"
          value={title}
          maxLength={TITLE_MAX}
          onChange={(event) => {
            setTitle(event.target.value)
            clearError('title')
          }}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={describedBy('title')}
          className={`h-11 ${fieldClasses} ${border(Boolean(errors.title))}`}
        />
        <FieldError id={`${id}-title-error`} message={errors.title} />
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-instructions`} className="block text-sm font-medium text-ink">
          Instructions
        </label>
        <p id={`${id}-instructions-hint`} className="mt-0.5 text-xs text-ink-muted">
          Plain text. Line breaks are kept.
        </p>
        <textarea
          ref={instructionsRef}
          id={`${id}-instructions`}
          value={instructions}
          rows={8}
          maxLength={ASSIGNMENT_INSTRUCTIONS_MAX}
          onChange={(event) => {
            setInstructions(event.target.value)
            clearError('instructions')
          }}
          aria-invalid={errors.instructions ? true : undefined}
          aria-describedby={describedBy('instructions', `${id}-instructions-hint`)}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(errors.instructions))}`}
        />
        <FieldError id={`${id}-instructions-error`} message={errors.instructions} />
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={`${id}-dueAt`} className="block text-sm font-medium text-ink">
            Due date and time
          </label>
          <p id={`${id}-dueAt-hint`} className="mt-0.5 text-xs text-ink-muted">
            In your local time ({localTimeZoneName()}). Students see it in theirs.
          </p>
          <input
            ref={dueAtRef}
            id={`${id}-dueAt`}
            type="datetime-local"
            value={dueAt}
            min="2000-01-01T00:00"
            max="9999-12-31T23:59"
            onChange={(event) => {
              setDueAt(event.target.value)
              clearError('dueAt')
            }}
            aria-invalid={errors.dueAt ? true : undefined}
            aria-describedby={describedBy('dueAt', `${id}-dueAt-hint`)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.dueAt))}`}
          />
          <FieldError id={`${id}-dueAt-error`} message={errors.dueAt} />
        </div>

        <div>
          <label htmlFor={`${id}-maxMarks`} className="block text-sm font-medium text-ink">
            Maximum marks
          </label>
          <p id={`${id}-maxMarks-hint`} className="mt-0.5 text-xs text-ink-muted">
            Greater than zero, such as 20 or 12.5.
          </p>
          <input
            ref={maxMarksRef}
            id={`${id}-maxMarks`}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={maxMarks}
            maxLength={9}
            onChange={(event) => {
              setMaxMarks(event.target.value)
              clearError('maxMarks')
            }}
            aria-invalid={errors.maxMarks ? true : undefined}
            aria-describedby={describedBy('maxMarks', `${id}-maxMarks-hint`)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.maxMarks))}`}
          />
          <FieldError id={`${id}-maxMarks-error`} message={errors.maxMarks} />
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
          {submitting ? submittingLabel : submitLabel}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
