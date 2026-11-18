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
import { approvalStatusLabel, approvalTagClasses } from '../approvals/approvalText'
import type { CampusRequestSummary } from '../types/campus'
import { DataTable } from '../ui'
import { requestStatusLabel } from './campusText'

const PAGE_SIZE = 20

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
        <div className="overflow-hidden rounded-panel border border-line">
          <DataTable<CampusRequestSummary>
            caption="My service requests"
            rows={requests.data.items}
            rowKey={(item) => item.requestId}
            columns={[
              { key: 'number', header: 'Request', primary: true, cell: (item) => <span className="font-mono">{item.requestNumber}</span> },
              { key: 'service', header: 'Service', cell: (item) => item.serviceName },
              { key: 'subject', header: 'Subject', cell: (item) => <span className="break-words">{item.subject}</span> },
              {
                key: 'status',
                header: 'Request status',
                cell: (item) => (
                  <span className={approvalTagClasses(item.status)}>
                    <span className="sr-only">Request status: </span>
                    {requestStatusLabel(item.status)}
                  </span>
                ),
              },
              { key: 'approval', header: 'Approval', cell: (item) => (item.approvalStatus ? approvalStatusLabel(item.approvalStatus) : 'Not needed') },
              { key: 'submitted', header: 'Submitted', cell: (item) => <time dateTime={item.submittedAt}>{formatDateTime(item.submittedAt)}</time> },
              {
                key: 'view',
                header: 'Action',
                cell: (item) => (
                  <Link to={`/campus/requests/${item.requestId}`} className={actionLinkClasses}>
                    View<span className="sr-only"> request {item.requestNumber}</span>
                  </Link>
                ),
              },
            ]}
          />
        </div>
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
    <section aria-labelledby="campus-requests-heading" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <h2 id="campus-requests-heading" className="text-xl font-bold text-portal-navy">
        My Service Requests
      </h2>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-16 rounded bg-portal-cta" />
      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </section>
  )
}
