import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { CALENDAR_DENIED, CATEGORIES, categoryLabel } from '../calendar/calendarText'
import type { AdminAcademicSession, AdminSemester } from '../types/admin'
import type { CalendarEventCategory, CalendarEventRequest } from '../types/calendar'
import { Alert, Button, Card, SelectField, TextAreaField, TextField } from '../ui'
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
const SCOPE_LOCKED = 'A published event cannot move to another session or semester. To move it, cancel it and add a new event.'
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

  function common(field: Field) {
    return {
      id: `${id}-${field}`,
      value: values[field],
      error: errors[field],
    }
  }

  return (
    <Card title={heading} description={scopeLocked ? SCOPE_LOCKED : undefined}>
      <form ref={formRef} onSubmit={handleSubmit} noValidate aria-label={heading}>
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            {...common('academicSessionId')}
            label="Academic session"
            hint={scopeLocked ? 'Fixed once the event has been published.' : undefined}
            disabled={scopeLocked}
            onChange={(event) => change('academicSessionId', event.target.value)}
          >
            <option value="">Choose an academic session</option>
            {sessions.map((session) => (
              <option key={session.id} value={String(session.id)}>
                {session.code}
                {session.status === 'CLOSED' ? ' (closed)' : ''}
              </option>
            ))}
          </SelectField>
          <SelectField
            {...common('semesterId')}
            label="Semester"
            hint="Optional. Only semesters of the chosen session are listed."
            disabled={scopeLocked || values.academicSessionId === ''}
            onChange={(event) => change('semesterId', event.target.value)}
          >
            <option value="">All semesters</option>
            {sessionSemesters.map((semester) => (
              <option key={semester.id} value={String(semester.id)}>
                {semesterLabel(semester)}
              </option>
            ))}
          </SelectField>
          <TextField
            {...common('title')}
            className="sm:col-span-2"
            label="Title"
            type="text"
            autoComplete="off"
            maxLength={TITLE_MAX}
            onChange={(event) => change('title', event.target.value)}
          />
          <SelectField {...common('category')} label="Category" onChange={(event) => change('category', event.target.value)}>
            <option value="">Choose a category</option>
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </SelectField>
          <div className="hidden sm:block" aria-hidden="true" />
          <TextField
            {...common('startDate')}
            label="Start date"
            type="date"
            min="2000-01-01"
            max="9999-12-31"
            onChange={(event) => change('startDate', event.target.value)}
          />
          <TextField
            {...common('endDate')}
            label="End date"
            hint="The last day, included. The same as the start date for a one-day event."
            type="date"
            min={values.startDate || '2000-01-01'}
            max="9999-12-31"
            onChange={(event) => change('endDate', event.target.value)}
          />
          <TextAreaField
            {...common('description')}
            className="sm:col-span-2"
            label="Description"
            hint={`Optional plain text, up to ${DESCRIPTION_MAX} characters.`}
            rows={4}
            maxLength={DESCRIPTION_MAX}
            onChange={(event) => change('description', event.target.value)}
          />
        </div>

        {formError && (
          <Alert tone="error" className="mt-4">
            {formError}
          </Alert>
        )}

        <div className="mt-4 flex flex-col gap-2 sm:flex-row">
          <Button type="submit" aria-disabled={submitting || undefined}>
            {submitting ? 'Saving…' : submitLabel}
          </Button>
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  )
}
