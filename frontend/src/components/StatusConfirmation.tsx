import { useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { Alert, Button } from '../ui'

type StatusConfirmationProps = {
  question: string
  detail: string
  confirmLabel: string
  busyLabel?: string
  dismissLabel?: string
  onConfirm: () => Promise<void>
  onCancel: () => void
  describeRefusal: (status: number | null, apiMessage: string | null) => string
}

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
      className="mt-3 rounded-panel border border-amber-400 bg-amber-50/70 p-4 sm:p-5"
    >
      <p id={`${id}-question`} className="text-xl font-bold text-ink">
        {question}
      </p>
      <p id={`${id}-detail`} className="mt-1 text-[0.95rem] leading-6 text-ink">
        {detail}
      </p>
      {error && (
        <Alert tone="error" className="mt-2">
          {error}
        </Alert>
      )}
      <div className="mt-4 flex flex-wrap gap-3">
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
