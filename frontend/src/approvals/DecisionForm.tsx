import { type FormEvent, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { approveTask, rejectTask } from '../api/approvalsApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import { COMMENT_MAX, refusalText } from './approvalText'

type DecisionFormProps = {
  stepId: number
  onDecided: (message: string, tone?: 'success' | 'error') => void
  idPrefix: string
  approvedMessage: string
  rejectedMessage: string
  audienceHint: string
}

export function DecisionForm({ stepId, onDecided, idPrefix, approvedMessage, rejectedMessage, audienceHint }: DecisionFormProps) {
  const commentId = `${idPrefix}-decision-comment`
  const busyRef = useRef(false)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function decide(kind: 'approve' | 'reject') {
    if (busyRef.current) {
      return
    }
    if (kind === 'reject' && comment.trim() === '') {
      setError('A comment is required to reject.')
      document.getElementById(commentId)?.focus()
      return
    }
    busyRef.current = true
    setBusy(kind)
    setError(null)
    try {
      if (kind === 'approve') {
        await approveTask(stepId, comment)
      } else {
        await rejectTask(stepId, comment)
      }
      onDecided(kind === 'approve' ? approvedMessage : rejectedMessage)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const text = refusalText(status, getApiError(failure)?.message ?? null, 'record the decision')
        if (status === 409 || status === 404) {
          onDecided(text, 'error')
        } else {
          setError(text)
        }
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  return (
    <form onSubmit={(event: FormEvent) => event.preventDefault()} aria-label="Decision" className="mt-4 rounded-control border border-line-strong p-3">
      <label htmlFor={commentId} className="block text-sm font-medium text-ink">
        Comment
      </label>
      <p id={`${commentId}-hint`} className="mt-0.5 text-xs text-ink-muted">
        Optional when approving, required when rejecting. {audienceHint}
      </p>
      <textarea
        id={commentId}
        rows={3}
        maxLength={COMMENT_MAX}
        value={comment}
        aria-describedby={`${commentId}-hint`}
        onChange={(event) => {
          setComment(event.target.value)
          setError(null)
        }}
        className={`py-2 ${fieldClasses} ${border(Boolean(error))}`}
      />
      <div role="alert">
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </div>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <Button aria-disabled={busy !== null || undefined} onClick={() => decide('approve')}>
          {busy === 'approve' ? 'Approving…' : 'Approve'}
        </Button>
        <Button variant="secondary" aria-disabled={busy !== null || undefined} onClick={() => decide('reject')}>
          {busy === 'reject' ? 'Rejecting…' : 'Reject'}
        </Button>
      </div>
    </form>
  )
}
