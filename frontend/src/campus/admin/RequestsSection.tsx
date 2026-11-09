import { type FormEvent, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getAdminCampusRequests, getAdminCampusServices } from '../../api/campusApi'
import { useApiRequest } from '../../api/useApiRequest'
import { SIGN_IN_AGAIN } from '../../approvals/approvalText'
import { ActionButton } from '../../components/ActionButton'
import { actionLinkClasses } from '../../components/actionStyles'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { usePendingFocus } from '../../components/usePendingFocus'
import { formatDateTime } from '../../courses/dates'
import { Pager } from '../../support/Pager'
import { border, fieldClasses } from '../../teaching/formStyles'
import type { CampusRequestStatus } from '../../types/campus'
import { RequestTags } from '../RequestTags'
import { REQUEST_STATUSES, requestStatusLabel } from '../campusText'

const PAGE_SIZE = 20
const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

/** Every campus service request, newest first, filtered by service, status, and requester, page by page. */
export function RequestsSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const serviceParam = searchParams.get('service')
  const serviceId = serviceParam && /^\d+$/.test(serviceParam) ? Number(serviceParam) : undefined
  const status = REQUEST_STATUSES.find((value) => value === searchParams.get('status')) as CampusRequestStatus | undefined
  const requesterLoginId = searchParams.get('requester')?.trim() || undefined
  const pageParam = searchParams.get('page')
  const page = pageParam && /^\d+$/.test(pageParam) ? Number(pageParam) : 0
  const [requesterInput, setRequesterInput] = useState(requesterLoginId ?? '')
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)

  const load = useMemo(
    () => (signal: AbortSignal) =>
      getAdminCampusRequests({ serviceId, status, requesterLoginId, page, size: PAGE_SIZE }, signal),
    [serviceId, status, requesterLoginId, page],
  )
  const requests = useApiRequest(load)
  const services = useApiRequest(getAdminCampusServices)

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

  let content
  if (requests.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading campus service requests…" /></StatePanel>
  } else if (requests.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to campus service requests." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (requests.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load service requests." message="Check your connection and try again." onRetry={requests.retry} />
      </StatePanel>
    )
  } else if (requests.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No matching service requests." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="Campus service requests" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {requests.data.items.map((item) => (
            <li key={item.requestId} className="px-4 py-3 sm:px-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-ink">{item.requestNumber}</span>
                    <RequestTags status={item.status} approvalStatus={item.approvalStatus} />
                  </div>
                  <p className="mt-0.5 text-sm break-words text-ink">
                    {item.requesterLoginId} · {item.serviceName}
                  </p>
                  <p className="text-sm break-words text-ink-muted">{item.subject}</p>
                  <p className="text-xs text-ink-muted">
                    Submitted <time dateTime={item.submittedAt}>{formatDateTime(item.submittedAt)}</time>
                  </p>
                </div>
                <Link to={`/admin/campus/requests/${item.requestId}`} className={`${actionLinkClasses} shrink-0 self-start`}>
                  Review<span className="sr-only">: {item.requestNumber}, {item.requesterLoginId}</span>
                </Link>
              </div>
            </li>
          ))}
        </ul>
        <Pager
          page={requests.data.page}
          totalPages={requests.data.totalPages}
          label="Service request pages"
          onPage={(next, key) => {
            update({ page: String(next) }, true)
            focusLater(key)
          }}
        />
      </>
    )
  }

  return (
    <>
      <h2 className="text-base font-semibold text-ink">Service Requests</h2>
      <div role="group" aria-label="Filter service requests" className="mt-3 grid gap-3 rounded-panel border border-line bg-surface p-4 sm:grid-cols-3">
        <div>
          <label htmlFor="campus-filter-service" className="block text-sm font-medium text-ink">Service</label>
          <select id="campus-filter-service" value={serviceId ?? ''} onChange={(event) => update({ service: event.target.value })} className={selectClasses}>
            <option value="">All services</option>
            {services.status === 'loaded' &&
              services.data.map((service) => (
                <option key={service.serviceId} value={service.serviceId}>
                  {service.name}
                </option>
              ))}
          </select>
        </div>
        <div>
          <label htmlFor="campus-filter-status" className="block text-sm font-medium text-ink">Request status</label>
          <select id="campus-filter-status" value={status ?? ''} onChange={(event) => update({ status: event.target.value })} className={selectClasses}>
            <option value="">All statuses</option>
            {REQUEST_STATUSES.map((value) => (
              <option key={value} value={value}>
                {requestStatusLabel(value)}
              </option>
            ))}
          </select>
        </div>
        <form onSubmit={applyRequester} className="min-w-0">
          <label htmlFor="campus-filter-requester" className="block text-sm font-medium text-ink">Requester (login ID)</label>
          <div className="mt-1.5 flex gap-2">
            <input
              id="campus-filter-requester"
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
      </div>
      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </>
  )
}
