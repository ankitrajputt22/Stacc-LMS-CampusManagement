import { useMemo, useRef } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getHelpdeskCategories, getMyHelpdeskTickets } from '../api/helpdeskApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { actionLinkClasses, primaryLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { border, fieldClasses } from '../teaching/formStyles'
import { Pager } from '../support/Pager'
import { TicketList } from '../support/TicketList'
import { idParam, PAGE_SIZE, pageParam, statusParam } from '../support/filters'
import { SIGN_IN_AGAIN, statusLabel, TICKET_STATUSES } from '../support/helpdeskText'
import type { HelpdeskTicketStatus } from '../types/helpdesk'

/**
 * Support at /support: one helpdesk for every account, whatever roles it holds. It offers Create ticket and lists the
 * account's own tickets from GET /api/helpdesk/tickets/me, page by page, with the backend's own filters. Admins also get
 * the links to the support queue and the categories; those pages are checked by the backend like any other.
 */
export function SupportPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const hasSupport = hasAnyRole(roles, ['STUDENT', 'FACULTY', 'ADMIN'])
  const isAdmin = hasAnyRole(roles, ['ADMIN'])
  const [searchParams, setSearchParams] = useSearchParams()
  const status = statusParam(searchParams.get('status'))
  const categoryId = idParam(searchParams.get('category'))
  const page = pageParam(searchParams.get('page'))
  const filtered = status !== undefined || categoryId !== undefined
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)

  const load = useMemo(
    () => (hasSupport ? (signal: AbortSignal) => getMyHelpdeskTickets({ status, categoryId, page, size: PAGE_SIZE }, signal) : null),
    [hasSupport, status, categoryId, page],
  )
  const request = useApiRequest(load)
  const categories = useApiRequest(hasSupport ? getHelpdeskCategories : null)

  function update(next: { status?: HelpdeskTicketStatus; category?: number; page?: number }) {
    const params = new URLSearchParams()
    if (next.status) params.set('status', next.status)
    if (next.category) params.set('category', String(next.category))
    if (next.page) params.set('page', String(next.page))
    setSearchParams(params)
  }

  const statusButton = (value: HelpdeskTicketStatus | undefined, label: string) => (
    <button
      key={label}
      type="button"
      aria-pressed={status === value}
      onClick={() => update({ status: value, category: categoryId })}
      className={`h-11 rounded-control border px-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:h-9 ${
        status === value
          ? 'border-stacc-primary bg-surface-muted text-ink underline underline-offset-4'
          : 'border-line-strong bg-surface text-ink-muted hover:bg-surface-muted hover:text-ink'
      }`}
    >
      {label}
    </button>
  )

  let content
  if (!hasSupport) {
    content = (
      <StatePanel>
        <AccessDeniedState title="Support is for student, faculty, and admin accounts." />
      </StatePanel>
    )
  } else if (request.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading your tickets…" />
      </StatePanel>
    )
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to your support tickets." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (request.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load your support tickets." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else if (request.data.items.length === 0) {
    content = (
      <StatePanel>
        {filtered ? (
          <EmptyState title="No tickets match this filter." />
        ) : (
          <EmptyState
            title="You haven’t created any support tickets yet."
            action={
              <Link to="/support/new" className={primaryLinkClasses}>
                Create ticket
              </Link>
            }
          />
        )}
      </StatePanel>
    )
  } else {
    content = (
      <>
        <TicketList tickets={request.data.items} label="My tickets" />
        <Pager
          page={request.data.page}
          totalPages={request.data.totalPages}
          label="Ticket pages"
          onPage={(next, focusKey) => {
            update({ status, category: categoryId, page: next })
            focusLater(focusKey)
          }}
        />
      </>
    )
  }

  return (
    <>
      <title>Support · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Support</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Get help with academic, LMS, technical, and campus-related issues.
      </p>

      {hasSupport && (
        <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          <Link to="/support/new" className={primaryLinkClasses}>
            Create ticket
          </Link>
          {/* For the interface only: the backend decides who may use the queue and the categories. */}
          {isAdmin && (
            <>
              <Link to="/support/admin" className={`${actionLinkClasses} justify-center`}>
                Admin ticket queue
              </Link>
              <Link to="/support/admin/categories" className={`${actionLinkClasses} justify-center`}>
                Manage categories
              </Link>
            </>
          )}
        </div>
      )}

      <h2 className="mt-8 text-lg font-semibold text-ink">My tickets</h2>
      {hasSupport && (
        <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end">
          <div role="group" aria-label="Show tickets by status" className="flex flex-wrap gap-2">
            {statusButton(undefined, 'All')}
            {TICKET_STATUSES.map((value) => statusButton(value, statusLabel(value)))}
          </div>
          {categories.status === 'loaded' && categories.data.length > 0 && (
            <div className="sm:w-60">
              <label htmlFor="support-category-filter" className="block text-sm font-medium text-ink">
                Category
              </label>
              <select
                id="support-category-filter"
                value={categoryId ?? ''}
                onChange={(event) => update({ status, category: idParam(event.target.value) })}
                className={`h-11 sm:h-9 ${fieldClasses} ${border(false)}`}
              >
                <option value="">All categories</option>
                {categories.data.map((category) => (
                  <option key={category.categoryId} value={category.categoryId}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      )}

      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </>
  )
}
