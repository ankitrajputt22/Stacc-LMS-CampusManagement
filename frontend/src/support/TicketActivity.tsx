import { formatDateTime } from '../courses/dates'
import type { HelpdeskTicketHistory } from '../types/helpdesk'
import { historyText } from './helpdeskText'

/** The ticket's history, oldest first, each entry as a sentence with who made the change and when. */
export function TicketActivity({ history }: { history: HelpdeskTicketHistory[] }) {
  return (
    <section aria-labelledby="ticket-activity" className="rounded-panel border border-line bg-surface p-4">
      <h2 id="ticket-activity" className="text-base font-semibold text-ink">
        Ticket activity
      </h2>
      {history.length === 0 ? (
        <p className="mt-2 text-sm text-ink-muted">No activity yet.</p>
      ) : (
        <ol className="mt-2 space-y-2">
          {history.map((entry, index) => (
            <li key={index} className="text-sm leading-5">
              <p className="break-words text-ink">{historyText(entry)}</p>
              <p className="text-xs text-ink-muted">
                {entry.actorLoginId} · <time dateTime={entry.createdAt}>{formatDateTime(entry.createdAt)}</time>
              </p>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
