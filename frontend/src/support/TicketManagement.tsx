import { useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  assignHelpdeskTicket,
  changeHelpdeskTicketPriority,
  changeHelpdeskTicketStatus,
} from '../api/helpdeskApi'
import type { ApiRequestState } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { border, fieldClasses } from '../teaching/formStyles'
import type { HelpdeskAssignee, HelpdeskTicketDetail, HelpdeskTicketPriority, HelpdeskTicketStatus } from '../types/helpdesk'
import { nextStatuses, priorityLabel, refusalText, statusLabel, TICKET_PRIORITIES } from './helpdeskText'

type TicketManagementProps = {
  ticket: HelpdeskTicketDetail
  /** The signed-in admin's own login ID, for "Assign to me". */
  myLoginId: string | null
  /** The ticket as the backend answered after a change. */
  onUpdated: (ticket: HelpdeskTicketDetail) => void
  /** Shows why a change was refused because the ticket changed elsewhere, and loads it again. */
  onStale: (message: string) => void
  /** The active admin accounts a ticket can be assigned to, loaded once by the page. */
  assignees: ApiRequestState<HelpdeskAssignee[]>
}

type Saving = 'assignee' | 'priority' | 'status' | null

const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

/**
 * The support team's controls on the shared ticket page: assignee, priority, and status. They are shown to admin
 * accounts only, for the interface; the backend checks HELPDESK_TICKET_MANAGE on every change. Only the moves the
 * lifecycle allows are offered, and closing is confirmed first. A 409, such as a ticket changed by someone else, is
 * shown in the backend's words and the ticket is loaded again; a 403 never signs the user out.
 */
export function TicketManagement({ ticket, myLoginId, onUpdated, onStale, assignees }: TicketManagementProps) {
  const [assignee, setAssignee] = useState(ticket.assignedToLoginId ?? '')
  const [priority, setPriority] = useState<HelpdeskTicketPriority>(ticket.priority)
  const [status, setStatus] = useState<HelpdeskTicketStatus | ''>('')
  const [confirmingClose, setConfirmingClose] = useState(false)
  const [saving, setSaving] = useState<Saving>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState('')
  // When the ticket changes, here or elsewhere, the choices start again from what it is now. Adjusting state while
  // rendering keeps focus where it is, unlike remounting.
  const [shownVersion, setShownVersion] = useState(ticket.updatedAt)
  if (shownVersion !== ticket.updatedAt) {
    setShownVersion(ticket.updatedAt)
    setAssignee(ticket.assignedToLoginId ?? '')
    setPriority(ticket.priority)
    setStatus('')
    setConfirmingClose(false)
  }

  const choices = nextStatuses(ticket.status)
  const loginIds = assignees.status === 'loaded' ? assignees.data.map((entry) => entry.loginId) : []
  // The current assignee is always a choice, even if the account can no longer be assigned, so the select is truthful.
  const options = ticket.assignedToLoginId && !loginIds.includes(ticket.assignedToLoginId)
    ? [ticket.assignedToLoginId, ...loginIds]
    : loginIds
  const canAssignToMe = myLoginId !== null && loginIds.includes(myLoginId) && ticket.assignedToLoginId !== myLoginId

  async function save(what: Exclude<Saving, null>, change: () => Promise<HelpdeskTicketDetail>, message: string) {
    if (saving !== null) {
      return
    }
    setSaving(what)
    setError(null)
    setDone('')
    try {
      const updated = await change()
      onUpdated(updated)
      setDone(message)
    } catch (failure) {
      const httpStatus = getHttpStatus(failure)
      if (httpStatus !== 401) {
        const text = refusalText(httpStatus, getApiError(failure)?.message ?? null)
        if (httpStatus === 409 || httpStatus === 404) {
          // The message is shown by the page, because these controls may be gone once the ticket is loaded again.
          onStale(text)
        } else {
          setError(text)
        }
      }
    } finally {
      setSaving(null)
    }
  }

  function assign(loginId: string) {
    void save('assignee', () => assignHelpdeskTicket(ticket.ticketId, { assignedToLoginId: loginId === '' ? null : loginId }),
      loginId === '' ? 'Ticket unassigned.' : `Assigned to ${loginId}.`)
  }

  return (
    <section aria-labelledby="ticket-manage" className="rounded-panel border border-line-strong bg-surface p-4">
      <h2 id="ticket-manage" className="text-base font-semibold text-ink">
        Manage ticket
      </h2>

      <div className="mt-3">
        <label htmlFor="ticket-assignee" className="block text-sm font-medium text-ink">
          Assigned to
        </label>
        <select id="ticket-assignee" value={assignee} onChange={(event) => setAssignee(event.target.value)} className={selectClasses}>
          <option value="">Unassigned</option>
          {options.map((loginId) => (
            <option key={loginId} value={loginId}>
              {loginId}
            </option>
          ))}
        </select>
        {assignees.status === 'loading' && <p className="mt-1 text-xs text-ink-muted">Loading admin accounts…</p>}
        {(assignees.status === 'failed' || assignees.status === 'denied' || assignees.status === 'notFound') && (
          <p className="mt-1 text-xs text-danger">Couldn’t load the admin accounts to assign.</p>
        )}
        <div className="mt-2 flex flex-wrap gap-2">
          <ActionButton
            aria-disabled={saving !== null || assignee === (ticket.assignedToLoginId ?? '') || undefined}
            onClick={() => assignee !== (ticket.assignedToLoginId ?? '') && assign(assignee)}
          >
            {saving === 'assignee' ? 'Saving…' : 'Save assignee'}
          </ActionButton>
          {canAssignToMe && (
            <ActionButton aria-disabled={saving !== null || undefined} onClick={() => myLoginId && assign(myLoginId)}>
              Assign to me
            </ActionButton>
          )}
        </div>
      </div>

      <div className="mt-4">
        <label htmlFor="ticket-priority" className="block text-sm font-medium text-ink">
          Priority
        </label>
        <select
          id="ticket-priority"
          value={priority}
          onChange={(event) => setPriority(event.target.value as HelpdeskTicketPriority)}
          className={selectClasses}
        >
          {TICKET_PRIORITIES.map((value) => (
            <option key={value} value={value}>
              {priorityLabel(value)}
            </option>
          ))}
        </select>
        <div className="mt-2">
          <ActionButton
            aria-disabled={saving !== null || priority === ticket.priority || undefined}
            onClick={() =>
              priority !== ticket.priority &&
              void save('priority', () => changeHelpdeskTicketPriority(ticket.ticketId, { priority }), `Priority set to ${priorityLabel(priority)}.`)
            }
          >
            {saving === 'priority' ? 'Saving…' : 'Save priority'}
          </ActionButton>
        </div>
      </div>

      <div className="mt-4">
        <label htmlFor="ticket-status" className="block text-sm font-medium text-ink">
          Status
        </label>
        <p id="ticket-status-hint" className="mt-0.5 text-xs text-ink-muted">
          Now {statusLabel(ticket.status)}. A ticket only moves forward, and closing is final.
        </p>
        <select
          id="ticket-status"
          value={status}
          aria-describedby="ticket-status-hint"
          onChange={(event) => {
            setStatus(event.target.value as HelpdeskTicketStatus | '')
            setConfirmingClose(false)
          }}
          className={selectClasses}
        >
          <option value="">Choose a new status</option>
          {choices.map((value) => (
            <option key={value} value={value}>
              {statusLabel(value)}
            </option>
          ))}
        </select>
        <div className="mt-2">
          <ActionButton
            aria-disabled={saving !== null || status === '' || undefined}
            onClick={() => {
              if (status === 'CLOSED') {
                setConfirmingClose(true)
              } else if (status !== '') {
                const target = status
                void save('status', () => changeHelpdeskTicketStatus(ticket.ticketId, { status: target }), `Status changed to ${statusLabel(target)}.`)
              }
            }}
          >
            {saving === 'status' ? 'Saving…' : 'Change status'}
          </ActionButton>
        </div>
        {confirmingClose && (
          <StatusConfirmation
            question="Close this ticket?"
            detail="After closing, no more replies or management changes can be made."
            confirmLabel="Close ticket"
            busyLabel="Closing…"
            onCancel={() => setConfirmingClose(false)}
            onConfirm={async () => {
              try {
                onUpdated(await changeHelpdeskTicketStatus(ticket.ticketId, { status: 'CLOSED' }))
              } catch (failure) {
                const httpStatus = getHttpStatus(failure)
                if (httpStatus === 409 || httpStatus === 404) {
                  setConfirmingClose(false)
                  onStale(refusalText(httpStatus, getApiError(failure)?.message ?? null))
                  return
                }
                throw failure
              }
            }}
            describeRefusal={(httpStatus, apiMessage) => refusalText(httpStatus, apiMessage)}
          />
        )}
      </div>

      <div role="alert">
        {error && <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2 text-sm text-danger">{error}</p>}
      </div>
      <p role="status" className="mt-3 text-sm text-ink-muted empty:hidden">
        {done}
      </p>
    </section>
  )
}
