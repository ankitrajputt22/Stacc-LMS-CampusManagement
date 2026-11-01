import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { publishCourseResult, saveCourseResult } from '../api/resultsApi'
import { formatDateTime } from '../courses/dates'
import type { CourseResultRosterStudent, FacultyResult } from '../types/results'
import { Alert, Badge, Button, TextAreaField, TextField } from '../ui'
import { ENROLLMENT_WORDS } from './resultText'

type Field = 'marksObtained' | 'maxMarks' | 'gradeCode' | 'remarks'

// The limits the backend enforces: six digits and two decimals for marks, and the longest grade and remarks it keeps.
const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/
const GRADE_MAX = 20
const REMARKS_MAX = 2000
const FIELD_ORDER: readonly Field[] = ['marksObtained', 'maxMarks', 'gradeCode', 'remarks']

type Values = Record<Field, string>

function valuesOf(result: FacultyResult | null): Values {
  return {
    marksObtained: result ? String(result.marksObtained) : '',
    maxMarks: result ? String(result.maxMarks) : '',
    gradeCode: result?.gradeCode ?? '',
    remarks: result?.remarks ?? '',
  }
}

function checked(values: Values): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {}
  const marks = values.marksObtained.trim()
  const max = values.maxMarks.trim()
  if (marks === '') {
    errors.marksObtained = 'Enter the marks obtained.'
  } else if (marks.startsWith('-')) {
    errors.marksObtained = 'Marks obtained cannot be negative.'
  } else if (!MARKS_PATTERN.test(marks)) {
    errors.marksObtained = 'Enter a number such as 82 or 12.5, with at most 2 decimal places.'
  }
  if (max === '') {
    errors.maxMarks = 'Enter the maximum marks.'
  } else if (!MARKS_PATTERN.test(max) || Number(max) <= 0) {
    errors.maxMarks = 'Enter a number greater than zero, with at most 2 decimal places.'
  }
  if (!errors.marksObtained && !errors.maxMarks && Number(marks) > Number(max)) {
    errors.marksObtained = 'Marks obtained cannot exceed maximum marks.'
  }
  if (values.gradeCode.trim().length > GRADE_MAX) {
    errors.gradeCode = `Use at most ${GRADE_MAX} characters.`
  }
  if (values.remarks.trim().length > REMARKS_MAX) {
    errors.remarks = `Use at most ${REMARKS_MAX.toLocaleString('en')} characters.`
  }
  return errors
}

function refusal(status: number | null, apiMessage: string | null, action: 'save' | 'publish'): string {
  switch (status) {
    case 400:
    case 409:
      return apiMessage ?? (action === 'save' ? 'Couldn’t save the result.' : 'Couldn’t publish the result.')
    case 403:
      return 'You don’t have access to results. If your access changed recently, sign out and sign in again.'
    case 404:
      return apiMessage ?? 'This student’s result is no longer available.'
    default:
      return action === 'save'
        ? 'Couldn’t save the result. Check your connection and try again. What you entered is still here.'
        : 'Couldn’t publish the result. Check your connection and try again.'
  }
}

function ResultStatus({ result }: { result: FacultyResult | null }) {
  if (result === null) {
    return <Badge tone="neutral">Not recorded</Badge>
  }
  return (
    <Badge tone={result.status === 'PUBLISHED' ? 'success' : 'warning'}>
      <span className="sr-only">Result status: </span>
      {result.status === 'PUBLISHED' ? 'Published' : 'Draft'}
    </Badge>
  )
}

type ResultRowProps = {
  courseOfferingId: number
  student: CourseResultRosterStudent
}

/**
 * One student's official result: marks obtained out of the official maximum, an optional grade code exactly as
 * entered, and optional plain-text remarks. Saving keeps it a draft; publishing, after a short confirmation, shows it
 * to the student; a published result can still be corrected and stays published. Each row keeps its own values, so
 * saving one never touches what was typed in another. The backend checks access and every rule again.
 */
export function ResultRow({ courseOfferingId, student }: ResultRowProps) {
  const id = useId()
  const busyRef = useRef(false)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const marksRef = useRef<HTMLInputElement>(null)
  const maxRef = useRef<HTMLInputElement>(null)
  const gradeRef = useRef<HTMLInputElement>(null)
  const remarksRef = useRef<HTMLTextAreaElement>(null)
  const [result, setResult] = useState<FacultyResult | null>(student.result)
  const [values, setValues] = useState<Values>(() => valuesOf(student.result))
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState<'save' | 'publish' | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const headingId = `${id}-student`
  const withdrawn = student.courseEnrollmentStatus === 'WITHDRAWN'
  const saved = valuesOf(result)
  const dirty = FIELD_ORDER.some((field) => values[field].trim() !== saved[field].trim())

  useEffect(() => {
    if (confirming) {
      confirmRef.current?.focus()
    }
  }, [confirming])

  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
    setAnnouncement('')
  }

  function focusFirst(found: Partial<Record<string, string>>) {
    const first = FIELD_ORDER.find((field) => found[field])
    const target = { marksObtained: marksRef, maxMarks: maxRef, gradeCode: gradeRef, remarks: remarksRef }
    if (first) {
      target[first].current?.focus()
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) {
      return
    }
    const found = checked(values)
    setFormError(null)
    setErrors(found)
    setConfirming(false)
    if (Object.keys(found).length > 0) {
      focusFirst(found)
      return
    }
    busyRef.current = true
    setBusy('save')
    try {
      const savedResult = await saveCourseResult(courseOfferingId, student.courseEnrollmentId, {
        marksObtained: Number(values.marksObtained.trim()),
        maxMarks: Number(values.maxMarks.trim()),
        gradeCode: values.gradeCode.trim() === '' ? null : values.gradeCode.trim(),
        remarks: values.remarks.trim() === '' ? null : values.remarks.trim(),
      })
      setResult(savedResult)
      setValues(valuesOf(savedResult))
      setAnnouncement(
        savedResult.status === 'PUBLISHED'
          ? `Correction saved for ${student.studentLoginId}. The student can see it now.`
          : `Draft saved for ${student.studentLoginId}. The student cannot see it until it is published.`,
      )
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
        setFormError(hasFieldErrors ? 'Check the highlighted field.' : refusal(status, apiError?.message ?? null, 'save'))
        focusFirst(fieldErrors)
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  async function publish() {
    if (busyRef.current) {
      return
    }
    setConfirming(false)
    setFormError(null)
    busyRef.current = true
    setBusy('publish')
    try {
      const published = await publishCourseResult(courseOfferingId, student.courseEnrollmentId)
      setResult(published)
      setAnnouncement(`Result published for ${student.studentLoginId}. The student can see it now.`)
      // The confirmation that had focus is gone, so focus moves to this student.
      headingRef.current?.focus()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setFormError(refusal(status, getApiError(failure)?.message ?? null, 'publish'))
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  const header = (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <h3
        ref={headingRef}
        id={headingId}
        tabIndex={-1}
        className="text-sm font-semibold break-all text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
      >
        <span className="sr-only">Student ID </span>
        {student.studentLoginId}
      </h3>
      <Badge tone={student.courseEnrollmentStatus === 'ENROLLED' ? 'info' : 'neutral'}>
        <span className="sr-only">Enrollment: </span>
        {ENROLLMENT_WORDS[student.courseEnrollmentStatus]}
      </Badge>
      <ResultStatus result={result} />
      {result?.publishedAt && (
        <span className="text-sm text-ink-muted">
          Published <time dateTime={result.publishedAt}>{formatDateTime(result.publishedAt)}</time>
        </span>
      )}
    </div>
  )

  // A withdrawn enrollment without a result cannot get a new one; an existing result stays editable below.
  if (withdrawn && result === null) {
    return (
      <article aria-labelledby={headingId} className="px-4 py-4 sm:px-5">
        {header}
        <p className="mt-2 text-sm text-ink-muted">This enrollment is withdrawn, so a new result cannot be recorded.</p>
      </article>
    )
  }

  const saveLabel = result?.status === 'PUBLISHED' ? 'Save correction' : 'Save draft'

  return (
    <article aria-labelledby={headingId} className="px-4 py-4 sm:px-5">
      {header}
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <form onSubmit={save} noValidate aria-labelledby={headingId} className="mt-3">
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            ref={marksRef}
            id={`${id}-marksObtained`}
            label="Marks obtained"
            hint="Out of the maximum marks."
            error={errors.marksObtained}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            maxLength={9}
            value={values.marksObtained}
            onChange={(event) => change('marksObtained', event.target.value)}
          />
          <TextField
            ref={maxRef}
            id={`${id}-maxMarks`}
            label="Maximum marks"
            error={errors.maxMarks}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            maxLength={9}
            value={values.maxMarks}
            onChange={(event) => change('maxMarks', event.target.value)}
          />
          <TextField
            ref={gradeRef}
            id={`${id}-gradeCode`}
            label="Grade (optional)"
            hint="The official grade, kept exactly as entered."
            error={errors.gradeCode}
            type="text"
            autoComplete="off"
            maxLength={GRADE_MAX}
            value={values.gradeCode}
            onChange={(event) => change('gradeCode', event.target.value)}
          />
        </div>
        <TextAreaField
          ref={remarksRef}
          id={`${id}-remarks`}
          className="mt-4"
          label="Remarks (optional)"
          error={errors.remarks}
          rows={2}
          maxLength={REMARKS_MAX}
          value={values.remarks}
          onChange={(event) => change('remarks', event.target.value)}
        />

        {result?.status === 'PUBLISHED' && (
          <p className="mt-3 text-sm text-ink-muted">Corrections are visible to the student after saving.</p>
        )}

        {formError && (
          <Alert tone="error" className="mt-3">
            {formError}
          </Alert>
        )}

        {confirming ? (
          <div role="group" aria-labelledby={`${id}-confirm`} className="mt-3 rounded-panel border border-amber-300 bg-amber-50 p-3 sm:p-4">
            <p id={`${id}-confirm`} className="text-sm font-semibold text-ink">
              Publish result?
            </p>
            <p className="mt-1 text-sm leading-6 text-ink">
              The student will be able to view this result after publication. It cannot be unpublished, but it can still
              be corrected.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button ref={confirmRef} size="sm" onClick={() => void publish()}>
                Publish
                <span className="sr-only"> the result of {student.studentLoginId}</span>
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setConfirming(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            <Button type="submit" aria-disabled={busy !== null || undefined}>
              {busy === 'save' ? 'Saving…' : saveLabel}
              <span className="sr-only"> for {student.studentLoginId}</span>
            </Button>
            {result?.status === 'DRAFT' && !dirty && (
              <Button variant="secondary" aria-disabled={busy !== null || undefined} onClick={() => busy === null && setConfirming(true)}>
                {busy === 'publish' ? 'Publishing…' : 'Publish'}
                <span className="sr-only"> the result of {student.studentLoginId}</span>
              </Button>
            )}
            {result?.status === 'DRAFT' && dirty && (
              <p className="text-sm text-ink-muted">Save your changes before publishing.</p>
            )}
          </div>
        )}
      </form>
    </article>
  )
}
