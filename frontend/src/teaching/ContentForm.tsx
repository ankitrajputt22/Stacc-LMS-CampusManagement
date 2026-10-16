import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '../components/Button'
import { border, fieldClasses } from './formStyles'

/** What happened to a submitted form: done, or refused with messages to show. */
export type FormOutcome =
  | { ok: true }
  | { ok: false; message: string | null; fieldErrors: Record<string, string> }

type ContentFormProps = {
  heading: string
  titleLabel: string
  bodyLabel: string
  /** The backend's name for the second field, so its validation messages land in the right place. */
  bodyField: 'description' | 'content' | 'message'
  /** When set, the second field is required and this is said when it is left empty. */
  bodyMissing?: string
  /** A short neutral note shown above the fields. */
  note?: string
  bodyRows: number
  titleMaxLength: number
  bodyMaxLength: number
  initialTitle?: string
  initialBody?: string | null
  submitLabel: string
  submittingLabel: string
  onSubmit: (title: string, body: string | null) => Promise<FormOutcome>
  onCancel: () => void
}

/**
 * A small contained form for a title and a plain-text body, used to add or edit a module or topic.
 * The body is a normal text area: what is typed is stored and shown as plain text.
 */
export function ContentForm({
  heading,
  titleLabel,
  bodyLabel,
  bodyField,
  bodyMissing,
  note,
  bodyRows,
  titleMaxLength,
  bodyMaxLength,
  initialTitle = '',
  initialBody = '',
  submitLabel,
  submittingLabel,
  onSubmit,
  onCancel,
}: ContentFormProps) {
  const id = useId()
  const titleRef = useRef<HTMLInputElement>(null)
  const bodyRef = useRef<HTMLTextAreaElement>(null)
  const [title, setTitle] = useState(initialTitle)
  const [body, setBody] = useState(initialBody ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    titleRef.current?.focus()
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }

    const found: Record<string, string> = {}
    if (title.trim() === '') {
      found.title = 'Enter a title.'
    } else if (title.trim().length > titleMaxLength) {
      found.title = `Use at most ${titleMaxLength} characters.`
    }
    if (bodyMissing && body.trim() === '') {
      found[bodyField] = bodyMissing
    } else if (body.trim().length > bodyMaxLength) {
      found[bodyField] = `Use at most ${bodyMaxLength.toLocaleString('en')} characters.`
    }
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0) {
      ;(found.title ? titleRef : bodyRef).current?.focus()
      return
    }

    setSubmitting(true)
    const outcome = await onSubmit(title, body.trim() === '' ? null : body)
    // On success the editor closes this form, so nothing more is updated here.
    if (!outcome.ok) {
      setSubmitting(false)
      setErrors(outcome.fieldErrors)
      setFormError(outcome.message)
      if (outcome.fieldErrors.title) {
        titleRef.current?.focus()
      } else if (outcome.fieldErrors[bodyField]) {
        bodyRef.current?.focus()
      }
    }
  }

  const titleError = errors.title
  const bodyError = errors[bodyField]

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
          {titleLabel}
        </label>
        <input
          ref={titleRef}
          id={`${id}-title`}
          type="text"
          value={title}
          maxLength={titleMaxLength}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={titleError ? true : undefined}
          aria-describedby={titleError ? `${id}-title-error` : undefined}
          className={`h-11 ${fieldClasses} ${border(Boolean(titleError))}`}
        />
        {titleError && (
          <p id={`${id}-title-error`} className="mt-1.5 text-sm text-danger">
            {titleError}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-body`} className="block text-sm font-medium text-ink">
          {bodyLabel}
        </label>
        <p id={`${id}-body-hint`} className="mt-0.5 text-xs text-ink-muted">
          Plain text. Line breaks are kept.
        </p>
        <textarea
          ref={bodyRef}
          id={`${id}-body`}
          value={body}
          rows={bodyRows}
          maxLength={bodyMaxLength}
          onChange={(event) => setBody(event.target.value)}
          aria-invalid={bodyError ? true : undefined}
          aria-describedby={bodyError ? `${id}-body-hint ${id}-body-error` : `${id}-body-hint`}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(bodyError))}`}
        />
        {bodyError && (
          <p id={`${id}-body-error`} className="mt-1.5 text-sm text-danger">
            {bodyError}
          </p>
        )}
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
