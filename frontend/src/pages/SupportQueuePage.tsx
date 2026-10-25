import { type FormEvent, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getAdminHelpdeskCategories, getAdminHelpdeskTickets } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { border, fieldClasses } from '../teaching/formStyles'
import { Pager } from '../support/Pager'
import { TicketList } from '../support/TicketList'
import { idParam, PAGE_SIZE, pageParam, priorityParam, statusParam } from '../support/filters'
import { priorityLabel, SIGN_IN_AGAIN, statusLabel, TICKET_PRIORITIES, TICKET_STATUSES } from '../support/helpdeskText'

const QUEUE_DENIED = 'You don’t have access to the support queue.'
const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

/**
 * The support team's queue at /support/admin: every ticket from GET /api/helpdesk/admin/tickets, newest first, page by
 * page, narrowed by the backend's own filters. Each ticket opens the shared ticket page. Admin accounts only, and the
 * backend checks HELPDESK_TICKET_VIEW_ALL on every request.
 */
export function SupportQueuePage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const [searchParams, setSearchParams] = useSearchParams()
  const status = statusParam(searchParams.get('status'))
  const priority = priorityParam(searchParams.get('priority'))
  const categoryId = idParam(searchParams.get('category'))
  const assignedToMe = searchParams.get('mine') === 'true'
  const requesterLoginId = searchParams.get('requester')?.trim() || undefined
  const page = pageParam(searchParams.get('page'))
  const [requesterInput, setRequesterInput] = useState(requesterLoginId ?? '')
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)

  const load = useMemo(
    () =>
      isAdmin
        ? (signal: AbortSignal) =>
            getAdminHelpdeskTickets(
              { status, priority, categoryId, assignedToMe: assignedToMe || undefined, requesterLoginId, page, size: PAGE_SIZE },
              signal,
            )
        : null,
    [isAdmin, status, priority, categoryId, assignedToMe, requesterLoginId, page],
  )
  const request = useApiRequest(load)
  const categories = useApiRequest(isAdmin ? getAdminHelpdeskCategories : null)

  function update(change: Record<string, string | undefined>, keepPage = false) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(change)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!keepPage) next.delete('page')
    setSearchParams(next)
  }

  function applyRequester(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    update({ requester: requesterInput.trim() })
  }

  const anyFilter = status || priority || categoryId || assignedToMe || requesterLoginId

  let content
  if (!isAdmin) {
    content = (
      <StatePanel>
        <AccessDeniedState title="The support queue is for admin accounts." />
      </StatePanel>
    )
  } else if (request.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading support queue…" />
      </StatePanel>
    )
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title={QUEUE_DENIED} message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (request.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load the support queue." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else if (request.data.items.length === 0) {
    content = (
      <StatePanel>
        <EmptyState title="No matching support tickets." />
      </StatePanel>
    )
  } else {
    content = (
      <>
        <p className="mb-2 text-sm text-ink-muted">
          {request.data.totalElements === 1 ? '1 ticket' : `${request.data.totalElements} tickets`}
        </p>
        <TicketList tickets={request.data.items} label="Support tickets" queue />
        <Pager
          page={request.data.page}
          totalPages={request.data.totalPages}
          label="Queue pages"
          onPage={(next, focusKey) => {
            update({ page: String(next) }, true)
            focusLater(focusKey)
          }}
        />
      </>
    )
  }

  return (
    <>
      <title>Support Queue · Stacc</title>
      <Breadcrumb parent={{ to: '/support', label: 'Support' }} current="Support Queue" />
      <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">Support Queue</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">Every support ticket, newest first.</p>

      {isAdmin && (
        <div role="group" aria-label="Filter tickets" className="mt-5 grid gap-3 rounded-panel border border-line bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <label htmlFor="queue-status" className="block text-sm font-medium text-ink">
              Status
            </label>
            <select id="queue-status" value={status ?? ''} onChange={(event) => update({ status: event.target.value })} className={selectClasses}>
              <option value="">All statuses</option>
              {TICKET_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {statusLabel(value)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="queue-priority" className="block text-sm font-medium text-ink">
              Priority
            </label>
            <select id="queue-priority" value={priority ?? ''} onChange={(event) => update({ priority: event.target.value })} className={selectClasses}>
              <option value="">All priorities</option>
              {TICKET_PRIORITIES.map((value) => (
                <option key={value} value={value}>
                  {priorityLabel(value)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="queue-category" className="block text-sm font-medium text-ink">
              Category
            </label>
            <select id="queue-category" value={categoryId ?? ''} onChange={(event) => update({ category: event.target.value })} className={selectClasses}>
              <option value="">All categories</option>
              {categories.status === 'loaded' &&
                categories.data.map((category) => (
                  <option key={category.categoryId} value={category.categoryId}>
                    {category.active ? category.name : `${category.name} (inactive)`}
                  </option>
                ))}
            </select>
          </div>
          <form onSubmit={applyRequester} className="min-w-0">
            <label htmlFor="queue-requester" className="block text-sm font-medium text-ink">
              Raised by (login ID)
            </label>
            <div className="mt-1.5 flex gap-2">
              <input
                id="queue-requester"
                type="text"
                autoComplete="off"
                maxLength={100}
                value={requesterInput}
                onChange={(event) => setRequesterInput(event.target.value)}
                className={`h-11 min-w-0 flex-1 rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:h-9 sm:text-sm ${border(false)}`}
              />
              <ActionButton type="submit">Apply</ActionButton>
            </div>
          </form>
          <div className="flex flex-wrap items-center gap-3 sm:col-span-2 lg:col-span-4">
            <label className="inline-flex min-h-11 items-center gap-2 text-sm text-ink sm:min-h-8">
              <input
                type="checkbox"
                checked={assignedToMe}
                onChange={(event) => update({ mine: event.target.checked ? 'true' : undefined })}
                className="size-4 accent-stacc-primary"
              />
              Assigned to me
            </label>
            {anyFilter && (
              <ActionButton
                onClick={() => {
                  setRequesterInput('')
                  setSearchParams(new URLSearchParams())
                }}
              >
                Clear filters
              </ActionButton>
            )}
          </div>
        </div>
      )}

      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </>
  )
}
