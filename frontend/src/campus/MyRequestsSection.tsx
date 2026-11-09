import { useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { getMyCampusRequests } from '../api/campusApi'
import { useApiRequest } from '../api/useApiRequest'
import { SIGN_IN_AGAIN } from '../approvals/approvalText'
import { actionLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { formatDateTime } from '../courses/dates'
import { Pager } from '../support/Pager'
import { RequestTags } from './RequestTags'

const PAGE_SIZE = 20

/** The account's own service requests, newest first, page by page. Each opens its detail page. */
export function MyRequestsSection() {
  const [page, setPage] = useState(0)
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const load = useMemo(() => (signal: AbortSignal) => getMyCampusRequests({ page, size: PAGE_SIZE }, signal), [page])
  const requests = useApiRequest(load)

  let content
  if (requests.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading service requests…" /></StatePanel>
  } else if (requests.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to service requests." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (requests.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load service requests." message="Check your connection and try again." onRetry={requests.retry} />
      </StatePanel>
    )
  } else if (requests.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No service requests yet." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="My service requests" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {requests.data.items.map((item) => (
            <li key={item.requestId} className="px-4 py-3 sm:px-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-ink">{item.requestNumber}</span>
                    <RequestTags status={item.status} approvalStatus={item.approvalStatus} />
                  </div>
                  <p className="mt-0.5 text-sm break-words text-ink">
                    {item.serviceName} · {item.subject}
                  </p>
                  <p className="text-xs text-ink-muted">
                    Submitted <time dateTime={item.submittedAt}>{formatDateTime(item.submittedAt)}</time>
                  </p>
                </div>
                <Link to={`/campus/requests/${item.requestId}`} className={`${actionLinkClasses} shrink-0 self-start`}>
                  View<span className="sr-only"> request {item.requestNumber}</span>
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
            setPage(next)
            focusLater(key)
          }}
        />
      </>
    )
  }

  return (
    <section aria-labelledby="campus-requests-heading">
      <h2 id="campus-requests-heading" className="text-base font-semibold text-ink">
        My Service Requests
      </h2>
      <div ref={contentRef} className="mt-3">
        {content}
      </div>
    </section>
  )
}
