import { useMemo, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { getHelpdeskAssignees, getHelpdeskTicket } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { actionLinkClasses } from '../components/actionStyles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { TicketActivity } from '../support/TicketActivity'
import { TicketConversation } from '../support/TicketConversation'
import { TicketManagement } from '../support/TicketManagement'
import { TicketPriorityTag, TicketStatusTag } from '../support/TicketTags'
import { idParam } from '../support/filters'
import { priorityLabel, SIGN_IN_AGAIN, statusLabel } from '../support/helpdeskText'
import type { HelpdeskTicketDetail } from '../types/helpdesk'

type Fact = { label: string; value: string; time?: string }

function TicketFacts({ ticket, showRequester }: { ticket: HelpdeskTicketDetail; showRequester: boolean }) {
  const facts: Fact[] = [
    { label: 'Category', value: ticket.categoryActive ? ticket.categoryName : `${ticket.categoryName} (no longer offered)` },
    { label: 'Priority', value: priorityLabel(ticket.priority) },
    { label: 'Status', value: statusLabel(ticket.status) },
    ...(showRequester ? [{ label: 'Raised by', value: ticket.requesterLoginId }] : []),
    { label: 'Assigned to', value: ticket.assignedToLoginId ?? 'Unassigned' },
    { label: 'Created', value: formatDateTime(ticket.createdAt), time: ticket.createdAt },
    { label: 'Updated', value: formatDateTime(ticket.updatedAt), time: ticket.updatedAt },
    ...(ticket.resolvedAt ? [{ label: 'Resolved', value: formatDateTime(ticket.resolvedAt), time: ticket.resolvedAt }] : []),
    ...(ticket.closedAt ? [{ label: 'Closed', value: formatDateTime(ticket.closedAt), time: ticket.closedAt }] : []),
  ]
  return (
    <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 rounded-panel border border-line bg-surface p-4 text-sm lg:grid-cols-3">
      {facts.map((fact) => (
        <div key={fact.label} className="min-w-0">
          <dt className="text-xs font-medium text-ink-muted">{fact.label}</dt>
          <dd className="break-words text-ink">{fact.time ? <time dateTime={fact.time}>{fact.value}</time> : fact.value}</dd>
        </div>
      ))}
    </dl>
  )
}

/**
 * One ticket at /support/tickets/:ticketId, the same page for the requester and the support team. The backend decides
 * who may see it: anything else, including a ticket of another account, answers the same 404 and shows "Ticket
 * unavailable". Admin accounts also get the management controls, for the interface only. The page shows only what the
 * ticket API returns.
 */
export function SupportTicketPage() {
  const { ticketId: ticketParam } = useParams()
  const ticketId = idParam(ticketParam ?? null)
  const location = useLocation()
  const createdNumber = (location.state as { createdNumber?: string } | null)?.createdNumber
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const headingRef = useRef<HTMLHeadingElement>(null)

  const load = useMemo(
    () => (ticketId === undefined ? null : (signal: AbortSignal) => getHelpdeskTicket(ticketId, signal)),
    [ticketId],
  )
  const request = useApiRequest(load)
  const assignees = useApiRequest(isAdmin ? getHelpdeskAssignees : null)
  // A change answers with the whole ticket; it is shown until the next load, which then replaces it.
  const [fresh, setFresh] = useState<{ ticket: HelpdeskTicketDetail; basedOn: HelpdeskTicketDetail } | null>(null)
  // Why the last change was refused when the ticket had changed elsewhere, such as a 409 for a closed ticket.
  const [staleNotice, setStaleNotice] = useState<string | null>(null)
  const loaded = request.status === 'loaded' ? request.data : null
  const ticket = fresh !== null && fresh.basedOn === loaded ? fresh.ticket : loaded

  function stale(message: string) {
    setStaleNotice(message)
    request.refresh()
    headingRef.current?.focus()
  }

  function updated(next: HelpdeskTicketDetail) {
    setStaleNotice(null)
    if (loaded !== null) {
      setFresh({ ticket: next, basedOn: loaded })
    }
    if (next.status === 'CLOSED') {
      // The controls are gone; focus returns to the top of the ticket, which now says it is closed.
      headingRef.current?.focus()
    }
  }

  let content
  if (request.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading ticket…" />
      </StatePanel>
    )
  } else if (request.status === 'notFound') {
    content = (
      <StatePanel>
        <EmptyState
          title="Ticket unavailable"
          description="This ticket doesn’t exist or isn’t available to your account."
          action={
            <Link to="/support" className={actionLinkClasses}>
              Back to Support
            </Link>
          }
        />
      </StatePanel>
    )
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to support tickets." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (ticket === null) {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load this ticket." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else {
    const closed = ticket.status === 'CLOSED'
    content = (
      <>
        <p className="font-mono text-sm text-ink-muted">{ticket.ticketNumber}</p>
        <h1 ref={headingRef} tabIndex={-1} className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink outline-none">
          {ticket.subject}
        </h1>
        <div className="mt-2 flex flex-wrap gap-2">
          <TicketPriorityTag priority={ticket.priority} />
          <TicketStatusTag status={ticket.status} />
        </div>
        {createdNumber === ticket.ticketNumber && (
          <p role="status" className="mt-4 rounded-control border border-success-line bg-success-soft px-3 py-2.5 text-sm text-success">
            Ticket {ticket.ticketNumber} created. The support team will reply here.
          </p>
        )}
        <div role="alert">
          {staleNotice && (
            <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">
              {staleNotice} The ticket has been loaded again.
            </p>
          )}
        </div>
        <TicketFacts ticket={ticket} showRequester={isAdmin} />

        <div className="mt-6 grid gap-6 lg:grid-cols-3">
          <div className="min-w-0 lg:col-span-2">
            <TicketConversation
              ticket={ticket}
              onChanged={() => {
                setStaleNotice(null)
                request.refresh()
              }}
              onStale={stale}
            />
          </div>
          <div className="min-w-0 space-y-6">
            {/* For the interface only: every change is checked by the backend. A closed ticket has no controls. */}
            {isAdmin && !closed && (
              <TicketManagement
                ticket={ticket}
                myLoginId={account?.loginId ?? null}
                assignees={assignees}
                onUpdated={updated}
                onStale={stale}
              />
            )}
            <TicketActivity history={ticket.history} />
          </div>
        </div>
      </>
    )
  }

  return (
    <>
      <title>{ticket ? `${ticket.ticketNumber} · Support · Stacc` : 'Ticket · Support · Stacc'}</title>
      <Breadcrumb parent={{ to: '/support', label: 'Support' }} current={ticket?.ticketNumber} />
      {ticket === null && <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Support ticket</h1>}
      <div className={ticket === null ? 'mt-6' : 'mt-3'}>{content}</div>
    </>
  )
}
