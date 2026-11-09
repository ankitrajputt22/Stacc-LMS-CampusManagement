import type { HelpdeskTicketHistory } from '../types/helpdesk'
import { Timeline } from '../ui'
import { historyText } from './helpdeskText'

function toneOf(entry: HelpdeskTicketHistory) {
  if (entry.action === 'STATUS_CHANGED' && (entry.toValue === 'RESOLVED' || entry.toValue === 'CLOSED')) {
    return 'done' as const
  }
  return entry.action === 'REOPENED' ? ('current' as const) : ('neutral' as const)
}

/** The ticket's history, oldest first, on the shared timeline: each entry as a sentence with who and when. */
export function TicketActivity({ history }: { history: HelpdeskTicketHistory[] }) {
  return (
    <section aria-labelledby="ticket-activity" className="rounded-panel border border-line bg-surface p-4">
      <h2 id="ticket-activity" className="mb-3 text-base font-semibold text-ink">
        Ticket activity
      </h2>
      <Timeline
        label="Ticket activity"
        empty="No activity yet."
        entries={history.map((entry, index) => ({
          key: index,
          title: historyText(entry),
          actor: entry.actorLoginId,
          at: entry.createdAt,
          tone: toneOf(entry),
        }))}
      />
    </section>
  )
}
