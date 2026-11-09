import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '../components/Button'
import {
  extensionOf,
  formatFileSize,
  MATERIAL_ACCEPT,
  MATERIAL_EXTENSIONS,
  MATERIAL_TYPES_TEXT,
  MAX_MATERIAL_BYTES,
} from '../courses/materials'
import type { FormOutcome } from './ContentForm'
import { MATERIAL_DESCRIPTION_MAX, TITLE_MAX } from './editorState'

const fieldClasses =
  'mt-1.5 block w-full rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:text-sm'

function border(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}

const LIMIT_TEXT = `${MATERIAL_TYPES_TEXT}, up to ${formatFileSize(MAX_MATERIAL_BYTES)}.`

/** A page a file can be uploaded to, for the Files page's page choice. */
export type UploadPage = { id: number; title: string; moduleTitle: string }

type MaterialUploadFormProps = {
  /** The page the file goes on, when it is already decided (a page's own materials). */
  topicTitle?: string
  /** The pages to choose from when it is not (the course Files page). */
  pages?: UploadPage[]
  onSubmit: (title: string, description: string | null, file: File, pageId: number | null) => Promise<FormOutcome>
  onCancel: () => void
}

/**
 * The form for a new study material: a title, an optional description, and the file. The checks here
 * only help early; the backend checks the file again and decides. The file is never shown or opened here.
 */
export function MaterialUploadForm({ topicTitle, pages, onSubmit, onCancel }: MaterialUploadFormProps) {
  const id = useId()
  const titleRef = useRef<HTMLInputElement>(null)
  const descriptionRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [pageId, setPageId] = useState('')
  const pageRef = useRef<HTMLSelectElement>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    titleRef.current?.focus()
  }, [])

  function chooseFile(chosen: File | null) {
    setFile(chosen)
    // A sensible title to start from, which can be changed.
    if (chosen && title.trim() === '') {
      const dot = chosen.name.lastIndexOf('.')
      setTitle((dot > 0 ? chosen.name.slice(0, dot) : chosen.name).slice(0, TITLE_MAX))
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (uploading) {
      return
    }

    const found: Record<string, string> = {}
    if (pages && pageId === '') {
      found.page = 'Choose the page the file goes on.'
    }
    if (title.trim() === '') {
      found.title = 'Enter a title.'
    }
    if (description.trim().length > MATERIAL_DESCRIPTION_MAX) {
      found.description = `Use at most ${MATERIAL_DESCRIPTION_MAX.toLocaleString('en')} characters.`
    }
    if (!file) {
      found.file = 'Choose a file.'
    } else if (!MATERIAL_EXTENSIONS.includes(extensionOf(file.name))) {
      found.file = `This type of file is not allowed. Choose a ${MATERIAL_TYPES_TEXT} file.`
    } else if (file.size === 0) {
      found.file = 'The file is empty.'
    } else if (file.size > MAX_MATERIAL_BYTES) {
      found.file = `The file is larger than ${formatFileSize(MAX_MATERIAL_BYTES)}.`
    }
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0 || !file) {
      if (found.page) {
        pageRef.current?.focus()
      } else {
        ;(found.title ? titleRef : found.description ? descriptionRef : fileRef).current?.focus()
      }
      return
    }

    setUploading(true)
    const outcome = await onSubmit(title, description.trim() === '' ? null : description, file, pageId === '' ? null : Number(pageId))
    // On success the editor closes this form, so nothing more is updated here.
    if (!outcome.ok) {
      setUploading(false)
      setErrors(outcome.fieldErrors)
      setFormError(outcome.message)
      ;(outcome.fieldErrors.title ? titleRef : outcome.fieldErrors.description ? descriptionRef : fileRef).current?.focus()
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      aria-busy={uploading}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        {topicTitle ? `New study material in page “${topicTitle}”` : 'Upload a file'}
      </p>

      {pages && (
        <div className="mt-3">
          <label htmlFor={`${id}-page`} className="block text-sm font-medium text-ink">
            Page
          </label>
          <p id={`${id}-page-hint`} className="mt-0.5 text-xs text-ink-muted">
            Every file is on a page. It starts as a draft; students see it once it, its page, and its module are published.
          </p>
          <select
            ref={pageRef}
            id={`${id}-page`}
            value={pageId}
            onChange={(event) => setPageId(event.target.value)}
            aria-invalid={errors.page ? true : undefined}
            aria-describedby={errors.page ? `${id}-page-hint ${id}-page-error` : `${id}-page-hint`}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.page))}`}
          >
            <option value="">Choose a page</option>
            {pages.map((page) => (
              <option key={page.id} value={page.id}>
                {page.moduleTitle} · {page.title}
              </option>
            ))}
          </select>
          {errors.page && (
            <p id={`${id}-page-error`} className="mt-1.5 text-sm text-danger">
              {errors.page}
            </p>
          )}
        </div>
      )}

      <div className="mt-3">
        <label htmlFor={`${id}-file`} className="block text-sm font-medium text-ink">
          File
        </label>
        <p id={`${id}-file-hint`} className="mt-0.5 text-xs text-ink-muted">
          {LIMIT_TEXT} It is kept private and shared only through Stacc.
        </p>
        <input
          ref={fileRef}
          id={`${id}-file`}
          type="file"
          accept={MATERIAL_ACCEPT}
          onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
          aria-invalid={errors.file ? true : undefined}
          aria-describedby={errors.file ? `${id}-file-hint ${id}-file-error` : `${id}-file-hint`}
          className={`py-2 file:mr-3 file:rounded-control file:border file:border-line-strong file:bg-surface-muted file:px-3 file:py-1 file:text-sm file:font-medium file:text-ink ${fieldClasses} ${border(Boolean(errors.file))}`}
        />
        {errors.file && (
          <p id={`${id}-file-error`} className="mt-1.5 text-sm text-danger">
            {errors.file}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-title`} className="block text-sm font-medium text-ink">
          Title
        </label>
        <input
          ref={titleRef}
          id={`${id}-title`}
          type="text"
          value={title}
          maxLength={TITLE_MAX}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={errors.title ? `${id}-title-error` : undefined}
          className={`h-11 ${fieldClasses} ${border(Boolean(errors.title))}`}
        />
        {errors.title && (
          <p id={`${id}-title-error`} className="mt-1.5 text-sm text-danger">
            {errors.title}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-description`} className="block text-sm font-medium text-ink">
          Description (optional)
        </label>
        <textarea
          ref={descriptionRef}
          id={`${id}-description`}
          value={description}
          rows={2}
          maxLength={MATERIAL_DESCRIPTION_MAX}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={errors.description ? `${id}-description-error` : undefined}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(errors.description))}`}
        />
        {errors.description && (
          <p id={`${id}-description-error`} className="mt-1.5 text-sm text-danger">
            {errors.description}
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
        <Button type="submit" aria-disabled={uploading || undefined}>
          {uploading ? 'Uploading…' : 'Upload material'}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
