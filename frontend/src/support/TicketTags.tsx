import type { HelpdeskTicketPriority, HelpdeskTicketStatus } from '../types/helpdesk'
import { priorityLabel, statusLabel } from './helpdeskText'

const tagClasses = 'inline-block shrink-0 rounded-sm border px-1.5 py-0.5 text-xs font-medium'

/** The status as a word, with a quiet border; never only a colour. */
export function TicketStatusTag({ status }: { status: HelpdeskTicketStatus }) {
  const tone = status === 'CLOSED' ? 'border-line-strong text-ink-muted' : 'border-line-strong text-ink'
  return (
    <span className={`${tagClasses} ${tone}`}>
      <span className="sr-only">Status: </span>
      {statusLabel(status)}
    </span>
  )
}

/** The priority as a word. High and Urgent get a calm warning border, nothing that flashes. */
export function TicketPriorityTag({ priority }: { priority: HelpdeskTicketPriority }) {
  const tone = priority === 'URGENT' || priority === 'HIGH' ? 'border-warning/60 text-warning' : 'border-line-strong text-ink-muted'
  return (
    <span className={`${tagClasses} ${tone}`}>
      <span className="sr-only">Priority: </span>
      {priorityLabel(priority)}
    </span>
  )
}
