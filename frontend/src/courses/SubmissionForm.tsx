import { type ChangeEvent, type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import { extensionOf, formatFileSize, MATERIAL_ACCEPT, MATERIAL_EXTENSIONS, MATERIAL_TYPES_TEXT, MAX_MATERIAL_BYTES } from './materials'

// The backend's defaults. It checks everything again and has the final say.
const MAX_FILES = 3
const MAX_TEXT_LENGTH = 20_000

/** What happened to a submitted attempt: saved, or refused with messages to show. */
export type SubmitOutcome =
  | { ok: true }
  | { ok: false; message: string | null; fieldErrors: Record<string, string> }

type SubmissionFormProps = {
  /** The student has handed in before, so this makes another attempt. */
  isResubmission: boolean
  /** The deadline has passed. Submitting is still allowed; the attempt will be marked late. */
  isPastDue: boolean
  onSubmit: (textContent: string, files: File[]) => Promise<SubmitOutcome>
  onCancel?: () => void
}

type Errors = { textContent?: string; files?: string }

function checkedErrors(textContent: string, files: File[]): { errors: Errors; formError: string | null } {
  const errors: Errors = {}
  if (textContent.length > MAX_TEXT_LENGTH) {
    errors.textContent = `Use at most ${MAX_TEXT_LENGTH.toLocaleString('en')} characters.`
  }
  if (files.length > MAX_FILES) {
    errors.files = `Attach at most ${MAX_FILES} files. Remove ${files.length - MAX_FILES}.`
  } else {
    for (const file of files) {
      if (!MATERIAL_EXTENSIONS.includes(extensionOf(file.name))) {
        errors.files = `“${file.name}” is not an allowed type. Use ${MATERIAL_TYPES_TEXT}.`
      } else if (file.size === 0) {
        errors.files = `“${file.name}” is empty.`
      } else if (file.size > MAX_MATERIAL_BYTES) {
        errors.files = `“${file.name}” is larger than ${formatFileSize(MAX_MATERIAL_BYTES)}.`
      }
      if (errors.files) {
        break
      }
    }
  }
  const formError = textContent.trim() === '' && files.length === 0 ? 'Add a text response or at least one file.' : null
  return { errors, formError }
}

/**
 * The form for one new attempt: optional plain text and optional files, but at least one of them. Chosen
 * files can be removed before sending; nothing is sent until Submit. While it is sending, the button says
 * so and a second press does nothing. If it fails, what was entered stays for another try.
 */
export function SubmissionForm({ isResubmission, isPastDue, onSubmit, onCancel }: SubmissionFormProps) {
  const id = useId()
  const textRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [textContent, setTextContent] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // A form opened for another attempt starts at its first field.
  useEffect(() => {
    if (isResubmission) {
      textRef.current?.focus()
    }
  }, [isResubmission])

  function chooseFiles(event: ChangeEvent<HTMLInputElement>) {
    const chosen = Array.from(event.target.files ?? [])
    // Cleared, so the same file can be chosen again after it was removed.
    event.target.value = ''
    if (chosen.length === 0) {
      return
    }
    const next = [...files, ...chosen]
    setFiles(next)
    setFormError(null)
    setErrors((current) => ({ ...current, files: checkedErrors(textContent, next).errors.files }))
  }

  function removeFile(index: number) {
    const next = files.filter((_, position) => position !== index)
    setFiles(next)
    setErrors((current) => ({ ...current, files: checkedErrors(textContent, next).errors.files }))
    fileRef.current?.focus()
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const checked = checkedErrors(textContent, files)
    setErrors(checked.errors)
    setFormError(checked.formError)
    if (checked.errors.textContent || (checked.formError && !checked.errors.files)) {
      textRef.current?.focus()
      return
    }
    if (checked.errors.files) {
      fileRef.current?.focus()
      return
    }

    setSubmitting(true)
    const outcome = await onSubmit(textContent, files)
    setSubmitting(false)
    if (outcome.ok) {
      setTextContent('')
      setFiles([])
      setErrors({})
      setFormError(null)
      return
    }
    setErrors({ textContent: outcome.fieldErrors.textContent, files: outcome.fieldErrors.files })
    setFormError(outcome.message)
  }

  const textError = errors.textContent
  const filesError = errors.files

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold text-ink">
        {isResubmission ? 'New attempt' : 'Submit your work'}
      </p>
      {isResubmission && (
        <p className="mt-1 text-sm leading-6 text-ink-muted">
          A new attempt will be created. Previous attempts remain in your submission history.
        </p>
      )}
      {isPastDue && (
        <p className="mt-2 rounded-control border border-line-strong bg-surface-muted px-3 py-2 text-sm leading-6 text-ink">
          The deadline has passed. You can still submit, but this attempt will be marked late.
        </p>
      )}

      <div className="mt-3">
        <label htmlFor={`${id}-text`} className="block text-sm font-medium text-ink">
          Text response (optional)
        </label>
        <p id={`${id}-text-hint`} className="mt-0.5 text-xs text-ink-muted">
          Plain text. Line breaks are kept.
        </p>
        <textarea
          ref={textRef}
          id={`${id}-text`}
          value={textContent}
          rows={6}
          onChange={(event) => {
            setTextContent(event.target.value)
            setErrors((current) => ({ ...current, textContent: undefined }))
            setFormError(null)
          }}
          aria-invalid={textError ? true : undefined}
          aria-describedby={textError ? `${id}-text-hint ${id}-text-error` : `${id}-text-hint`}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(textError))}`}
        />
        {textError && (
          <p id={`${id}-text-error`} className="mt-1.5 text-sm text-danger">
            {textError}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-files`} className="block text-sm font-medium text-ink">
          Files (optional)
        </label>
        <p id={`${id}-files-hint`} className="mt-0.5 text-xs leading-5 text-ink-muted">
          Up to {MAX_FILES} files, {formatFileSize(MAX_MATERIAL_BYTES)} each: {MATERIAL_TYPES_TEXT}.
        </p>
        <input
          ref={fileRef}
          id={`${id}-files`}
          type="file"
          multiple
          accept={MATERIAL_ACCEPT}
          onChange={chooseFiles}
          aria-invalid={filesError ? true : undefined}
          aria-describedby={filesError ? `${id}-files-hint ${id}-files-error` : `${id}-files-hint`}
          // The chosen files are listed below. The input itself is emptied after each choice, so its own
          // "No file chosen" text is hidden rather than shown next to that list.
          className="mt-1.5 block w-full max-w-full text-sm text-transparent file:mr-3 file:min-h-11 file:rounded-control file:border file:border-line-strong file:bg-surface file:px-3 file:text-sm file:font-medium file:text-ink hover:file:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:file:min-h-9"
        />
        {filesError && (
          <p id={`${id}-files-error`} className="mt-1.5 text-sm text-danger">
            {filesError}
          </p>
        )}
        {files.length > 0 && (
          <ul aria-label="Chosen files" className="mt-2 divide-y divide-line rounded-control border border-line">
            {files.map((file, index) => (
              <li key={`${file.name}-${index}`} className="flex items-center justify-between gap-3 px-3 py-2">
                <p className="min-w-0 text-sm text-ink">
                  <span className="break-all">{file.name}</span>
                  <span className="whitespace-nowrap text-ink-muted">
                    <span aria-hidden="true"> · </span>
                    <span className="sr-only">, </span>
                    {formatFileSize(file.size)}
                  </span>
                </p>
                <ActionButton className="shrink-0" onClick={() => removeFile(index)}>
                  Remove<span className="sr-only"> {file.name}</span>
                </ActionButton>
              </li>
            ))}
          </ul>
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
          {submitting ? 'Submitting…' : isResubmission ? 'Submit new attempt' : 'Submit assignment'}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
