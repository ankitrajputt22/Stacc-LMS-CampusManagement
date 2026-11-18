import type { HelpdeskTicketPriority, HelpdeskTicketStatus } from '../types/helpdesk'
import { priorityLabel, statusLabel } from './helpdeskText'

const tagClasses = 'inline-block shrink-0 rounded-sm border px-1.5 py-0.5 text-xs font-medium'

const STATUS_TONES: Record<HelpdeskTicketStatus, string> = {
  OPEN: 'border-sky-200 bg-sky-50 text-ink',
  IN_PROGRESS: 'border-sky-300 bg-sky-100 text-portal-navy',
  WAITING_ON_REQUESTER: 'border-amber-300 bg-amber-50 text-ink',
  RESOLVED: 'border-success-line bg-success-soft text-ink',
  CLOSED: 'border-line-strong bg-surface-muted text-ink-muted',
}

export function TicketStatusTag({ status }: { status: HelpdeskTicketStatus }) {
  return (
    <span className={`${tagClasses} ${STATUS_TONES[status] ?? 'border-line-strong text-ink'}`}>
      <span className="sr-only">Status: </span>
      {statusLabel(status)}
    </span>
  )
}

export function TicketPriorityTag({ priority }: { priority: HelpdeskTicketPriority }) {
  const tone =
    priority === 'URGENT'
      ? 'border-danger/40 bg-danger/5 text-danger'
      : priority === 'HIGH'
        ? 'border-orange-200 bg-orange-50 text-warning'
        : priority === 'NORMAL'
          ? 'border-sky-200 bg-sky-50 text-ink'
          : 'border-line-strong bg-surface-muted text-ink-muted'
  return (
    <span className={`${tagClasses} ${tone}`}>
      <span className="sr-only">Priority: </span>
      {priorityLabel(priority)}
    </span>
  )
}
