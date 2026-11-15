import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { archiveTeacherDocument, createTeacherDocument, getTeacherDocuments, updateTeacherDocument } from '../api/aiApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { formatDateTime } from '../courses/dates'
import type { AiTeacherDocument } from '../types/ai'
import { Alert, Button, TextAreaField, TextField } from '../ui'
import { hintClasses, panelClasses } from './aiStyles'

const MAX_TITLE = 200
const MAX_CONTENT = 20_000

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return 'You don’t have permission to change teacher documents.'
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

type Draft = { id: number | null; title: string; content: string }

function DocumentForm({ draft, onSaved, onCancel, lmsCourseId }: {
  draft: Draft
  onSaved: (message: string) => Promise<void>
  onCancel: () => void
  lmsCourseId: number
}) {
  const [title, setTitle] = useState(draft.title)
  const [content, setContent] = useState(draft.content)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) {
      return
    }
    if (!title.trim() || !content.trim()) {
      setError('Enter a title and the text.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (draft.id === null) {
        await createTeacherDocument(lmsCourseId, title.trim(), content)
        await onSaved(`Added ${title.trim()}.`)
      } else {
        await updateTeacherDocument(draft.id, title.trim(), content)
        await onSaved(`Saved ${title.trim()}.`)
      }
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mt-3 grid gap-3 rounded-control border border-line p-3" noValidate>
      <TextField label="Title" value={title} maxLength={MAX_TITLE} required onChange={(event) => setTitle(event.target.value)} />
      <TextAreaField
        label="Text"
        hint="Plain text. Markdown headings (# Heading) split it into sections."
        rows={8}
        value={content}
        maxLength={MAX_CONTENT}
        required
        onChange={(event) => setContent(event.target.value)}
      />
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : draft.id === null ? 'Add document' : 'Save document'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/**
 * Teacher-only documents (v0.13.5): plain-text notes and drafts that only AI Tools read when drafting assessments.
 * Students never see them. Changes reach AI Tools after the next Refresh AI knowledge; documents are archived (confirmed
 * first), never deleted. The backend checks that the faculty member teaches the course on every request.
 */
export function TeacherDocumentsPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const queryClient = useQueryClient()
  const key = queryKeys.lms.teacherDocuments(lmsCourseId)
  const documents = useApiQuery(key, (signal) => getTeacherDocuments(lmsCourseId, signal))
  const [editing, setEditing] = useState<Draft | null>(null)
  const [archiving, setArchiving] = useState<AiTeacherDocument | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function changed(message: string) {
    setEditing(null)
    setAnnouncement(`${message} Refresh AI knowledge to include the change.`)
    await queryClient.invalidateQueries({ queryKey: key })
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Teacher-only documents
      </h2>
      <p className={hintClasses}>
        Notes and drafts that only AI Tools read when drafting assessments. Students never see them, and the AI Study
        Assistant never uses them. Refresh AI knowledge after a change.
      </p>
      <p role="status" className="mt-2 text-sm text-ink-muted empty:hidden">
        {announcement}
      </p>

      {documents.status === 'loading' && <p className="mt-3 text-sm text-ink-muted">Loading teacher documents…</p>}
      {documents.status === 'failed' && (
        <Alert tone="error" className="mt-3">
          Couldn’t load the teacher documents.{' '}
          <button type="button" className="underline" onClick={documents.retry}>
            Retry
          </button>
        </Alert>
      )}
      {(documents.status === 'denied' || documents.status === 'notFound') && (
        <p className="mt-3 text-sm text-ink-muted">Teacher documents are not available for this course.</p>
      )}
      {documents.status === 'loaded' && (
        <>
          {documents.data.length === 0 ? (
            <p className="mt-3 text-sm text-ink-muted">No teacher documents yet.</p>
          ) : (
            <ul className="mt-3 divide-y divide-line">
              {documents.data.map((document) => (
                <li key={document.id} className="flex flex-wrap items-start justify-between gap-2 py-2">
                  <div className="min-w-0">
                    <p className="font-medium break-words text-ink">{document.title}</p>
                    <p className="text-sm text-ink-muted">
                      Updated <time dateTime={document.updatedAt}>{formatDateTime(document.updatedAt)}</time>
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="secondary" onClick={() => setEditing({ id: document.id, title: document.title, content: document.content })}>
                      Edit<span className="sr-only"> {document.title}</span>
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setArchiving(document)}>
                      Archive<span className="sr-only"> {document.title}</span>
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {archiving && (
            <div className="mt-3">
              <StatusConfirmation
                question={`Archive ${archiving.title}?`}
                detail="AI Tools stop using it after the next refresh. It cannot be restored."
                confirmLabel="Archive document"
                busyLabel="Archiving…"
                onCancel={() => setArchiving(null)}
                describeRefusal={refusal}
                onConfirm={async () => {
                  await archiveTeacherDocument(archiving.id)
                  setArchiving(null)
                  await changed(`Archived ${archiving.title}.`)
                }}
              />
            </div>
          )}
          {editing ? (
            <DocumentForm
              key={editing.id ?? 'new'}
              draft={editing}
              lmsCourseId={lmsCourseId}
              onSaved={changed}
              onCancel={() => setEditing(null)}
            />
          ) : (
            <div className="mt-3">
              <Button variant="secondary" onClick={() => setEditing({ id: null, title: '', content: '' })}>
                Add teacher document
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
