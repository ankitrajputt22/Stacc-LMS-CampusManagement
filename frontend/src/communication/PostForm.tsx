import { type FormEvent, useState } from 'react'
import { refusalText } from './refusal'
import { Alert, Button, TextAreaField, TextField } from '../ui'

/**
 * A plain-text post: an optional title (for a new thread or conversation) and the text. Checks that both are filled
 * before sending; the backend checks again and decides.
 */
export function PostForm({
  titleLabel,
  bodyLabel,
  submitLabel,
  allowFiles = false,
  onSubmit,
  onCancel,
}: {
  titleLabel?: string
  bodyLabel: string
  submitLabel: string
  /** Offers up to 5 files, checked again by the backend. */
  allowFiles?: boolean
  onSubmit: (title: string, body: string, files: File[]) => Promise<void>
  onCancel?: () => void
}) {
  const [files, setFiles] = useState<File[]>([])
  const [fileInputKey, setFileInputKey] = useState(0)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if ((titleLabel && title.trim() === '') || body.trim() === '') {
      setError(titleLabel ? `Enter the ${titleLabel.toLowerCase()} and the text.` : 'Enter some text first.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      await onSubmit(title.trim(), body, files)
      setTitle('')
      setBody('')
      setFiles([])
      setFileInputKey((key) => key + 1)
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t send. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate>
      {titleLabel && <TextField label={titleLabel} value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />}
      <TextAreaField label={bodyLabel} rows={4} value={body} maxLength={10_000} onChange={(event) => setBody(event.target.value)} />
      {allowFiles && (
        <div>
          <label htmlFor={`post-files-${fileInputKey}`} className="block text-sm font-medium text-ink">
            Files (optional, up to 5)
          </label>
          <input
            key={fileInputKey}
            id={`post-files-${fileInputKey}`}
            type="file"
            multiple
            className="mt-1 block text-sm text-ink"
            onChange={(event) => setFiles(Array.from(event.target.files ?? []).slice(0, 5))}
          />
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Sending…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}

/** A post's text, shown as plain text with its line breaks; never as HTML. */
export function PostText({ text }: { text: string }) {
  return <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">{text}</p>
}
