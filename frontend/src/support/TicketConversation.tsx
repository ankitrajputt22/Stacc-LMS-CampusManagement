import { type FormEvent, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addHelpdeskMessage } from '../api/helpdeskApi'
import { Button } from '../components/Button'
import { formatDateTime } from '../courses/dates'
import { border, fieldClasses } from '../teaching/formStyles'
import type { HelpdeskTicketDetail } from '../types/helpdesk'
import { MESSAGE_MAX, refusalText } from './helpdeskText'

type TicketConversationProps = {
  ticket: HelpdeskTicketDetail
  /** Loads the ticket again after a reply. */
  onChanged: () => void
  /** Shows why the reply was refused because the ticket changed elsewhere, and loads it again. */
  onStale: (message: string) => void
}

/**
 * The original issue and the conversation, oldest first, as full-width plain text that keeps its line breaks. Below it
 * is the reply form, unless the ticket is closed. A reply never changes the status; the backend decides who may reply.
 */
export function TicketConversation({ ticket, onChanged, onStale }: TicketConversationProps) {
  const closed = ticket.status === 'CLOSED'
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const sendingRef = useRef(false)
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState('')

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sendingRef.current) {
      return
    }
    const text = message.trim()
    if (text === '') {
      setError('Write a reply first.')
      textareaRef.current?.focus()
      return
    }
    if (text.length > MESSAGE_MAX) {
      setError(`Use at most ${MESSAGE_MAX.toLocaleString()} characters.`)
      textareaRef.current?.focus()
      return
    }
    sendingRef.current = true
    setSending(true)
    setError(null)
    setSent('')
    try {
      // Only the text is sent: the author is the account in the access token.
      await addHelpdeskMessage(ticket.ticketId, { message: text })
      setMessage('')
      setSent('Reply sent.')
      onChanged()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const text = refusalText(status, getApiError(failure)?.message ?? null, 'send the reply')
        if (status === 409 || status === 404) {
          // The ticket changed meanwhile, for example it was closed: the page says so and shows it as it is now.
          onStale(text)
        } else {
          setError(text)
        }
      }
    } finally {
      sendingRef.current = false
      setSending(false)
    }
  }

  return (
    <>
      <section aria-labelledby="ticket-issue" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
        <h2 id="ticket-issue" className="text-base font-semibold text-ink">
          Original issue
        </h2>
        <p className="mt-1 text-xs text-ink-muted">
          {ticket.requesterLoginId} · <time dateTime={ticket.createdAt}>{formatDateTime(ticket.createdAt)}</time>
        </p>
        {/* Plain text: React shows it as text, never as HTML. */}
        <p className="mt-3 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{ticket.description}</p>
      </section>

      <section aria-labelledby="ticket-conversation" className="mt-6">
        <h2 id="ticket-conversation" className="text-base font-semibold text-ink">
          Conversation
        </h2>
        {ticket.messages.length === 0 ? (
          <p className="mt-2 text-sm text-ink-muted">No replies yet.</p>
        ) : (
          <ol className="mt-3 divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
            {ticket.messages.map((entry) => (
              <li key={entry.messageId} className="px-4 py-3 sm:px-5">
                <p className="text-sm">
                  <span className="font-semibold text-ink">{entry.authorLoginId}</span>
                  {entry.authorLoginId === ticket.requesterLoginId && <span className="text-ink-muted"> (requester)</span>}
                  <span className="text-ink-muted">
                    {' · '}
                    <time dateTime={entry.createdAt}>{formatDateTime(entry.createdAt)}</time>
                  </span>
                </p>
                <p className="mt-1 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{entry.message}</p>
              </li>
            ))}
          </ol>
        )}
      </section>

      {closed ? (
        <p className="mt-6 rounded-control border border-line-strong bg-surface-muted px-3 py-2.5 text-sm text-ink">
          This ticket is closed and is read-only.
        </p>
      ) : (
        <form onSubmit={send} noValidate aria-labelledby="ticket-reply-label" className="mt-6">
          <label id="ticket-reply-label" htmlFor="ticket-reply" className="block text-base font-semibold text-ink">
            Reply
          </label>
          {ticket.status === 'RESOLVED' && (
            <p id="ticket-reply-resolved" className="mt-1 text-sm text-ink-muted">
              This ticket is resolved. You can still reply; replying does not change its status.
            </p>
          )}
          <textarea
            ref={textareaRef}
            id="ticket-reply"
            rows={5}
            maxLength={MESSAGE_MAX}
            value={message}
            aria-invalid={error ? true : undefined}
            aria-describedby={[ticket.status === 'RESOLVED' ? 'ticket-reply-resolved' : '', error ? 'ticket-reply-error' : '']
              .filter(Boolean)
              .join(' ') || undefined}
            onChange={(event) => {
              setMessage(event.target.value)
              setError(null)
            }}
            className={`py-2 ${fieldClasses} ${border(Boolean(error))}`}
          />
          <div role="alert">
            {error && (
              <p id="ticket-reply-error" className="mt-2 text-sm text-danger">
                {error}
              </p>
            )}
          </div>
          <div className="mt-3">
            <Button type="submit" aria-disabled={sending || undefined}>
              {sending ? 'Sending…' : 'Send reply'}
            </Button>
          </div>
          <p role="status" className="mt-2 text-sm text-ink-muted empty:hidden">
            {sent}
          </p>
        </form>
      )}
    </>
  )
}
