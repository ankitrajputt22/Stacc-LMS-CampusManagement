import { useState } from 'react'
import { getTeamHelpdeskTickets } from '../api/helpdeskApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { statusLabel, TICKET_STATUSES } from '../support/helpdeskText'
import { TicketList } from '../support/TicketList'
import { Button, Card, PageHeader, SelectField } from '../ui'

const PAGE_SIZE = 20

/**
 * /support/team: the tickets routed to the signed-in staff member's support teams, newest first. Each ticket opens on
 * the shared ticket page, where the backend checks the team again.
 */
export function SupportTeamQueuePage() {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const tickets = useApiQuery(queryKeys.helpdesk.teamQueue(status, page), (signal) =>
    getTeamHelpdeskTickets({ ...(status ? { status } : {}), page, size: PAGE_SIZE }, signal),
  )

  return (
    <div className="max-w-4xl">
      <title>Team tickets · Support · Stacc</title>
      <PageHeader title="Team tickets" description="Tickets routed to your support teams." />
      <div className="mt-6 space-y-4">
        <div className="max-w-xs">
          <SelectField
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(0)
            }}
          >
            <option value="">Any status</option>
            {TICKET_STATUSES.map((value) => (
              <option key={value} value={value}>
                {statusLabel(value)}
              </option>
            ))}
          </SelectField>
        </div>
        {tickets.status !== 'loaded' ? (
          <Card title="Tickets">
            <SectionState request={tickets} what="team tickets" />
          </Card>
        ) : tickets.data.items.length === 0 ? (
          <Card title="Tickets">
            <EmptyState title="No tickets here." description="Tickets for your teams appear here as they arrive." />
          </Card>
        ) : (
          <>
            <TicketList tickets={tickets.data.items} label="Team tickets" queue />
            {tickets.data.totalPages > 1 && (
              <div className="flex items-center gap-3">
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous page
                </Button>
                <span className="text-sm text-ink-muted">
                  Page {page + 1} of {tickets.data.totalPages}
                </span>
                <Button variant="secondary" size="sm" disabled={page + 1 >= tickets.data.totalPages} onClick={() => setPage(page + 1)}>
                  Next page
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
