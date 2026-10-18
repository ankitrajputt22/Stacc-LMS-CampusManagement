import { useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { ActionButton } from '../components/ActionButton'
import { refusalText } from './enrollmentText'

type StatusConfirmationProps = {
  /** Such as "Complete semester enrollment?" */
  question: string
  /** What happens, such as "Semester 3 · 2026-27 will be marked completed. This cannot be undone." */
  detail: string
  confirmLabel: string
  /** Sends the change. A refusal is thrown and shown here in safe words. */
  onConfirm: () => Promise<void>
  onCancel: () => void
}

/**
 * A small inline confirmation for ending an official enrollment, which cannot be undone. Focus moves to the confirm
 * button, Escape cancels, and a refusal such as a 409 is shown with the backend's safe message. A 403 never signs
 * the user out; a 401 is left to the shared client.
 */
export function StatusConfirmation({ question, detail, confirmLabel, onConfirm, onCancel }: StatusConfirmationProps) {
  const id = useId()
  const confirmRef = useRef<HTMLButtonElement>(null)
  const busyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    confirmRef.current?.focus()
  }, [])

  async function confirm() {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusalText(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <div
      role="group"
      aria-labelledby={`${id}-question`}
      aria-describedby={`${id}-detail`}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.stopPropagation()
          onCancel()
        }
      }}
      className="mt-3 rounded-control border border-line-strong bg-surface-muted p-3"
    >
      <p id={`${id}-question`} className="text-sm font-semibold text-ink">
        {question}
      </p>
      <p id={`${id}-detail`} className="mt-1 text-sm leading-6 text-ink">
        {detail}
      </p>
      <div role="alert">
        {error && (
          <p className="mt-2 rounded-control border border-danger/40 bg-danger/5 px-3 py-2 text-sm leading-5 text-danger">{error}</p>
        )}
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        <button
          ref={confirmRef}
          type="button"
          aria-disabled={busy || undefined}
          onClick={confirm}
          className="inline-flex min-h-11 items-center rounded-control border border-transparent bg-stacc-primary px-3 text-sm font-medium text-white hover:bg-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8"
        >
          {busy ? 'Saving…' : confirmLabel}
        </button>
        <ActionButton onClick={onCancel}>Cancel</ActionButton>
      </div>
    </div>
  )
}
