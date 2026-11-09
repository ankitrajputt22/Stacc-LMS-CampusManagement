import { useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { Alert, Button } from '../ui'

type StatusConfirmationProps = {
  /** Such as "Complete semester enrollment?" */
  question: string
  /** What happens, such as "Semester 3 · 2026-27 will be marked completed. This cannot be undone." */
  detail: string
  confirmLabel: string
  /** What the confirm button says while the change is sent, such as "Publishing…". */
  busyLabel?: string
  /** What the button that closes without changing anything says, when "Cancel" would be unclear. */
  dismissLabel?: string
  /** Sends the change. A refusal is thrown and shown here in safe words. */
  onConfirm: () => Promise<void>
  onCancel: () => void
  /** Turns a refused change into safe words: the HTTP status, if any, and the backend's message, if any. */
  describeRefusal: (status: number | null, apiMessage: string | null) => string
}

/**
 * A small inline confirmation for a change that cannot be undone, such as ending an enrollment or archiving a
 * course. Focus moves to the confirm button, Escape cancels, and a refusal such as a 409 is shown in the words
 * `describeRefusal` gives. A 403 never signs the user out; a 401 is left to the shared client.
 */
export function StatusConfirmation({
  question,
  detail,
  confirmLabel,
  busyLabel = 'Saving…',
  dismissLabel = 'Cancel',
  onConfirm,
  onCancel,
  describeRefusal,
}: StatusConfirmationProps) {
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
        setError(describeRefusal(status, getApiError(failure)?.message ?? null))
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
      className="mt-3 rounded-panel border border-amber-300 bg-amber-50 p-3 sm:p-4"
    >
      <p id={`${id}-question`} className="text-sm font-semibold text-ink">
        {question}
      </p>
      <p id={`${id}-detail`} className="mt-1 text-sm leading-6 text-ink">
        {detail}
      </p>
      {error && (
        <Alert tone="error" className="mt-2">
          {error}
        </Alert>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button ref={confirmRef} size="sm" aria-disabled={busy || undefined} onClick={confirm}>
          {busy ? busyLabel : confirmLabel}
        </Button>
        <Button variant="secondary" size="sm" onClick={onCancel}>
          {dismissLabel}
        </Button>
      </div>
    </div>
  )
}
