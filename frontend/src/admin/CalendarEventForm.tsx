import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { CALENDAR_DENIED, CATEGORIES, categoryLabel } from '../calendar/calendarText'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AdminAcademicSession, AdminSemester } from '../types/admin'
import type { CalendarEventCategory, CalendarEventRequest } from '../types/calendar'
import { semesterLabel } from './enrollmentText'

export type CalendarEventValues = {
  academicSessionId: string
  /** Empty for an event across the whole session. */
  semesterId: string
  title: string
  category: string
  startDate: string
  endDate: string
  description: string
}

type Field = keyof CalendarEventValues
type Errors = Partial<Record<Field, string>>

const TITLE_MAX = 200
const DESCRIPTION_MAX = 5000
const FIELDS: Field[] = ['academicSessionId', 'semesterId', 'title', 'category', 'startDate', 'endDate', 'description']

type CalendarEventFormProps = {
  heading: string
  initial: CalendarEventValues
  sessions: AdminAcademicSession[]
  semesters: AdminSemester[]
  /** A published event keeps its session and semester; only its details can be corrected. */
  scopeLocked: boolean
  submitLabel: string
  /** Sends the event. A refusal is thrown and shown here; what was entered stays. */
  onSubmit: (request: CalendarEventRequest) => Promise<void>
  onCancel: () => void
}

// The backend's own rules, checked before sending; the backend checks everything again.
function checked(values: CalendarEventValues): Errors {
  const errors: Errors = {}
  if (values.academicSessionId === '') {
    errors.academicSessionId = 'Choose an academic session.'
  }
  if (values.title.trim() === '') {
    errors.title = 'Enter a title.'
  } else if (values.title.trim().length > TITLE_MAX) {
    errors.title = `Use at most ${TITLE_MAX} characters.`
  }
  if (values.category === '') {
    errors.category = 'Choose a category.'
  }
  if (values.startDate === '') {
    errors.startDate = 'Enter the start date.'
  }
  if (values.endDate === '') {
    errors.endDate = 'Enter the end date.'
  } else if (values.startDate !== '' && values.endDate < values.startDate) {
    errors.endDate = 'The end date must not be before the start date.'
  }
  if (values.description.trim().length > DESCRIPTION_MAX) {
    errors.description = `Use at most ${DESCRIPTION_MAX} characters.`
  }
  return errors
}

function refusal(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? 'Couldn’t save. Check the values and try again.'
    case 403:
      return `${CALENDAR_DENIED} If your access changed recently, sign out and sign in again.`
    default:
      return 'Couldn’t save. Check your connection and try again. What you entered is still here.'
  }
}

/**
 * The form for one official calendar event. The semester list follows the chosen session, and "All semesters" makes
 * the event session-wide. A published event's session and semester are shown but cannot change. Every field has a
 * real label; mistakes are announced next to the field, and a refusal keeps what was entered. A 403 never signs the
 * user out.
 */
export function CalendarEventForm({
  heading,
  initial,
  sessions,
  semesters,
  scopeLocked,
  submitLabel,
  onSubmit,
  onCancel,
}: CalendarEventFormProps) {
  const id = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const submittingRef = useRef(false)
  const [values, setValues] = useState<CalendarEventValues>(initial)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const sessionSemesters = semesters.filter((semester) => String(semester.academicSessionId) === values.academicSessionId)

  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled])')?.focus()
  }, [])

  function change(field: Field, value: string) {
    setValues((current) => ({
      ...current,
      [field]: value,
      // Another session has other semesters: the event becomes session-wide until one of them is chosen.
      ...(field === 'academicSessionId' ? { semesterId: '' } : {}),
    }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function focusFirst(found: Errors) {
    const first = FIELDS.find((field) => found[field])
    if (first) {
      document.getElementById(`${id}-${first}`)?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found = checked(values)
    setFormError(null)
    setErrors(found)
    if (Object.values(found).some(Boolean)) {
      focusFirst(found)
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    try {
      await onSubmit({
        academicSessionId: Number(values.academicSessionId),
        semesterId: values.semesterId === '' ? null : Number(values.semesterId),
        title: values.title.trim(),
        description: values.description.trim() === '' ? null : values.description,
        category: values.category as CalendarEventCategory,
        startDate: values.startDate,
        endDate: values.endDate,
      })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        // A 401 is left to the shared client, which ends the session.
        const apiError = getApiError(failure)
        const fieldErrors = Object.fromEntries(
          (apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]),
        ) as Errors
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

  function describedBy(field: Field, hint?: boolean) {
    return [hint ? `${id}-${field}-hint` : null, errors[field] ? `${id}-${field}-error` : null].filter(Boolean).join(' ') || undefined
  }

  function fieldError(field: Field) {
    return (
      errors[field] && (
        <p id={`${id}-${field}-error`} className="mt-1.5 text-sm text-danger">
          {errors[field]}
        </p>
      )
    )
  }

  function common(field: Field, hint?: boolean) {
    return {
      id: `${id}-${field}`,
      value: values[field],
      'aria-invalid': errors[field] ? true : undefined,
      'aria-describedby': describedBy(field, hint),
    }
  }

  const label = (field: Field, text: string) => (
    <label htmlFor={`${id}-${field}`} className="block text-sm font-medium text-ink">
      {text}
    </label>
  )

  return (
    <form
      ref={formRef}
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        {heading}
      </p>
      {scopeLocked && (
        <p id={`${id}-scope-note`} className="mt-1 text-sm leading-6 text-ink-muted">
          A published event cannot move to another session or semester. To move it, cancel it and add a new event.
        </p>
      )}

      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        <div>
          {label('academicSessionId', 'Academic session')}
          <select
            {...common('academicSessionId')}
            disabled={scopeLocked}
            aria-describedby={[describedBy('academicSessionId'), scopeLocked ? `${id}-scope-note` : null].filter(Boolean).join(' ') || undefined}
            onChange={(event) => change('academicSessionId', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.academicSessionId))} disabled:bg-surface-muted disabled:text-ink-muted`}
          >
            <option value="">Choose an academic session</option>
            {sessions.map((session) => (
              <option key={session.id} value={String(session.id)}>
                {session.code}
                {session.status === 'CLOSED' ? ' (closed)' : ''}
              </option>
            ))}
          </select>
          {fieldError('academicSessionId')}
        </div>
        <div>
          {label('semesterId', 'Semester')}
          <p id={`${id}-semesterId-hint`} className="mt-0.5 text-xs text-ink-muted">
            Optional. Only semesters of the chosen session are listed.
          </p>
          <select
            {...common('semesterId', true)}
            disabled={scopeLocked || values.academicSessionId === ''}
            onChange={(event) => change('semesterId', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.semesterId))} disabled:bg-surface-muted disabled:text-ink-muted`}
          >
            <option value="">All semesters</option>
            {sessionSemesters.map((semester) => (
              <option key={semester.id} value={String(semester.id)}>
                {semesterLabel(semester)}
              </option>
            ))}
          </select>
          {fieldError('semesterId')}
        </div>
        <div className="sm:col-span-2">
          {label('title', 'Title')}
          <input
            {...common('title')}
            type="text"
            autoComplete="off"
            maxLength={TITLE_MAX}
            onChange={(event) => change('title', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.title))}`}
          />
          {fieldError('title')}
        </div>
        <div>
          {label('category', 'Category')}
          <select
            {...common('category')}
            onChange={(event) => change('category', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.category))}`}
          >
            <option value="">Choose a category</option>
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </select>
          {fieldError('category')}
        </div>
        <div className="hidden sm:block" aria-hidden="true" />
        <div>
          {label('startDate', 'Start date')}
          <input
            {...common('startDate')}
            type="date"
            min="2000-01-01"
            max="9999-12-31"
            onChange={(event) => change('startDate', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.startDate))}`}
          />
          {fieldError('startDate')}
        </div>
        <div>
          {label('endDate', 'End date')}
          <p id={`${id}-endDate-hint`} className="mt-0.5 text-xs text-ink-muted">
            The last day, included. The same as the start date for a one-day event.
          </p>
          <input
            {...common('endDate', true)}
            type="date"
            min={values.startDate || '2000-01-01'}
            max="9999-12-31"
            onChange={(event) => change('endDate', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.endDate))}`}
          />
          {fieldError('endDate')}
        </div>
        <div className="sm:col-span-2">
          {label('description', 'Description')}
          <p id={`${id}-description-hint`} className="mt-0.5 text-xs text-ink-muted">
            Optional plain text, up to {DESCRIPTION_MAX} characters.
          </p>
          <textarea
            {...common('description', true)}
            rows={4}
            maxLength={DESCRIPTION_MAX}
            onChange={(event) => change('description', event.target.value)}
            className={`py-2 ${fieldClasses} ${border(Boolean(errors.description))}`}
          />
          {fieldError('description')}
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
