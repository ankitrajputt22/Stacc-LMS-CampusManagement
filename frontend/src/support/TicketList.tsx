import { Link } from 'react-router'
import { formatDateTime } from '../courses/dates'
import type { HelpdeskTicketSummary } from '../types/helpdesk'
import { DataTable } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { TicketPriorityTag, TicketStatusTag } from './TicketTags'

type TicketListProps = {
  tickets: HelpdeskTicketSummary[]
  /** Such as "My tickets". */
  label: string
  /** The support queue also shows who raised each ticket and who has it. */
  queue?: boolean
}

/**
 * Tickets as a table on wide screens and stacked records on phones: the number (the row's one link to the shared
 * ticket page), the subject, the category, the tags, and when it last changed. There is never a description here.
 */
export function TicketList({ tickets, label, queue = false }: TicketListProps) {
  return (
    <div className="overflow-hidden rounded-panel border border-line bg-surface">
      <DataTable<HelpdeskTicketSummary>
        caption={label}
        rows={tickets}
        rowKey={(ticket) => ticket.ticketId}
        columns={[
          {
            key: 'number',
            header: 'Ticket ID',
            primary: true,
            cell: (ticket) => (
              <Link to={`/support/tickets/${ticket.ticketId}`} className={`font-medium whitespace-nowrap ${textLinkClasses}`}>
                {ticket.ticketNumber}
                <span className="sr-only">: {ticket.subject}</span>
              </Link>
            ),
          },
          { key: 'subject', header: 'Subject', cell: (ticket) => <span className="break-words text-ink">{ticket.subject}</span> },
          {
            key: 'category',
            header: 'Category',
            cell: (ticket) => (
              <>
                {ticket.categoryName}
                <span className="block text-xs text-ink-muted">({ticket.categoryCode})</span>
              </>
            ),
          },
          ...(queue
            ? [
                { key: 'requester', header: 'Raised by', cell: (ticket: HelpdeskTicketSummary) => ticket.requesterLoginId },
                {
                  key: 'assignee',
                  header: 'Assigned to',
                  cell: (ticket: HelpdeskTicketSummary) => ticket.assignedToLoginId ?? 'Unassigned',
                },
              ]
            : []),
          { key: 'priority', header: 'Priority', cell: (ticket) => <TicketPriorityTag priority={ticket.priority} /> },
          { key: 'status', header: 'Status', cell: (ticket) => <TicketStatusTag status={ticket.status} /> },
          {
            key: 'updated',
            header: 'Updated',
            cell: (ticket) => <time dateTime={ticket.updatedAt}>{formatDateTime(ticket.updatedAt)}</time>,
          },
        ]}
      />
    </div>
  )
}
