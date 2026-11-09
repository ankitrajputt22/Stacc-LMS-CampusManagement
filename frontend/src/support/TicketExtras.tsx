import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addHelpdeskNote, rateHelpdeskTicket, reopenHelpdeskTicket } from '../api/helpdeskApi'
import type { HelpdeskTicketDetail } from '../types/helpdesk'
import { Alert, Button, Card, SelectField, TextAreaField } from '../ui'
import { MESSAGE_MAX, refusalText } from './helpdeskText'

function failureText(failure: unknown, what: string): string | null {
  const status = getHttpStatus(failure)
  return status === 401 ? null : refusalText(status, getApiError(failure)?.message ?? null, what)
}

/** An internal note for the support side: never shown to the requester and sends no notification. */
export function TicketNoteForm({ ticket, onChanged }: { ticket: HelpdeskTicketDetail; onChanged: () => void }) {
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (note.trim() === '') {
      setError('Write the note first.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await addHelpdeskNote(ticket.ticketId, note)
      setNote('')
      onChanged()
    } catch (failure) {
      setError(failureText(failure, 'add the note'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Internal note" description="Only the support team sees notes. The requester is not told.">
      <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate>
        <TextAreaField label="Note" rows={3} value={note} maxLength={MESSAGE_MAX} onChange={(event) => setNote(event.target.value)} />
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" variant="secondary" disabled={busy}>
          {busy ? 'Adding…' : 'Add note'}
        </Button>
      </form>
    </Card>
  )
}

/** The requester's own actions on a finished ticket: reopen within 7 days of resolution, and rate it once. */
export function TicketRequesterActions({
  ticket,
  onUpdated,
}: {
  ticket: HelpdeskTicketDetail
  onUpdated: (next: HelpdeskTicketDetail) => void
}) {
  const [rating, setRating] = useState('5')
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!ticket.canReopen && !ticket.canRate && ticket.rating === null) {
    return null
  }

  async function run(action: () => Promise<HelpdeskTicketDetail>, what: string) {
    setBusy(true)
    setError(null)
    try {
      onUpdated(await action())
    } catch (failure) {
      setError(failureText(failure, what))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Your feedback">
      <div className="space-y-4 text-sm">
        {ticket.canReopen && (
          <div className="space-y-2">
            <p className="text-ink">Still not working? You can reopen this ticket for 7 days after it was resolved.</p>
            <Button variant="secondary" size="sm" disabled={busy} onClick={() => void run(() => reopenHelpdeskTicket(ticket.ticketId), 'reopen the ticket')}>
              Reopen ticket
            </Button>
          </div>
        )}
        {ticket.rating !== null && (
          <p className="text-ink">
            You rated this ticket {ticket.rating} out of 5.
            {ticket.ratingComment && <span className="mt-1 block break-words whitespace-pre-line text-ink-muted">{ticket.ratingComment}</span>}
          </p>
        )}
        {ticket.canRate && (
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault()
              void run(() => rateHelpdeskTicket(ticket.ticketId, Number(rating), comment.trim() === '' ? null : comment), 'save your rating')
            }}
          >
            <SelectField label="How did we do?" value={rating} onChange={(event) => setRating(event.target.value)}>
              {['5', '4', '3', '2', '1'].map((value) => (
                <option key={value} value={value}>
                  {value} out of 5
                </option>
              ))}
            </SelectField>
            <TextAreaField label="Comment (optional)" rows={2} value={comment} maxLength={1000} onChange={(event) => setComment(event.target.value)} />
            <Button type="submit" size="sm" disabled={busy}>
              {busy ? 'Saving…' : 'Send rating'}
            </Button>
          </form>
        )}
        {error && <Alert tone="error">{error}</Alert>}
      </div>
    </Card>
  )
}
