import { Link } from 'react-router'
import { actionLinkClasses } from '../components/actionStyles'
import { formatDateTime } from '../courses/dates'
import type { HelpdeskTicketSummary } from '../types/helpdesk'
import { TicketPriorityTag, TicketStatusTag } from './TicketTags'

type TicketListProps = {
  tickets: HelpdeskTicketSummary[]
  /** Such as "My tickets". */
  label: string
  /** The support queue also shows who raised each ticket and who has it. */
  queue?: boolean
}

/**
 * Tickets as stacked rows that fit any screen: the number and tags, the subject, then the category and times. There is
 * never a description here. Each row has one descriptive link to the shared ticket page.
 */
export function TicketList({ tickets, label, queue = false }: TicketListProps) {
  return (
    <ul aria-label={label} className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
      {tickets.map((ticket) => (
        <li key={ticket.ticketId} className="px-4 py-3 sm:px-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-xs text-ink-muted">{ticket.ticketNumber}</span>
                <TicketPriorityTag priority={ticket.priority} />
                <TicketStatusTag status={ticket.status} />
              </div>
              <p className="mt-1 text-sm font-semibold break-words text-ink">{ticket.subject}</p>
              <p className="mt-1 text-sm break-words text-ink-muted">
                {ticket.categoryName}
                {queue && (
                  <>
                    {' · '}Raised by {ticket.requesterLoginId}
                    {' · '}
                    {ticket.assignedToLoginId ? `Assigned to ${ticket.assignedToLoginId}` : 'Unassigned'}
                  </>
                )}
              </p>
              <p className="mt-0.5 text-xs text-ink-muted">
                Updated <time dateTime={ticket.updatedAt}>{formatDateTime(ticket.updatedAt)}</time>
                {' · '}Created <time dateTime={ticket.createdAt}>{formatDateTime(ticket.createdAt)}</time>
              </p>
            </div>
            <Link to={`/support/tickets/${ticket.ticketId}`} className={`${actionLinkClasses} shrink-0 self-start`}>
              {queue ? 'Open' : 'View ticket'}
              <span className="sr-only">
                : {ticket.ticketNumber}, {ticket.subject}
              </span>
            </Link>
          </div>
        </li>
      ))}
    </ul>
  )
}
