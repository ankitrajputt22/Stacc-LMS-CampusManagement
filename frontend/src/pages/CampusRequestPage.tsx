import { useMemo, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { cancelCampusRequest, getMyCampusRequest } from '../api/campusApi'
import { useApiRequest } from '../api/useApiRequest'
import { ApprovalSteps } from '../approvals/ApprovalSteps'
import { approvalStatusLabel, refusalText, SIGN_IN_AGAIN } from '../approvals/approvalText'
import { RequestTags } from '../campus/RequestTags'
import { requestStatusLabel } from '../campus/campusText'
import { ActionButton } from '../components/ActionButton'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import type { CampusRequestDetail } from '../types/campus'

type Notice = { text: string; tone: 'success' | 'error' }

function Detail({ detail, onChanged }: { detail: CampusRequestDetail; onChanged: (notice: Notice) => void }) {
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-panel border border-line bg-surface p-4 text-sm lg:grid-cols-4">
        <div className="min-w-0">
          <dt className="text-xs font-medium text-ink-muted">Service</dt>
          <dd className="break-words text-ink">{detail.serviceName}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-ink-muted">Request status</dt>
          <dd className="text-ink">{requestStatusLabel(detail.status)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-ink-muted">Submitted</dt>
          <dd className="text-ink"><time dateTime={detail.submittedAt}>{formatDateTime(detail.submittedAt)}</time></dd>
        </div>
        {detail.completedAt && (
          <div>
            <dt className="text-xs font-medium text-ink-muted">Completed</dt>
            <dd className="text-ink"><time dateTime={detail.completedAt}>{formatDateTime(detail.completedAt)}</time></dd>
          </div>
        )}
      </dl>

      <section aria-labelledby="campus-request-subject" className="mt-4 rounded-panel border border-line bg-surface p-4">
        <h2 id="campus-request-subject" className="text-sm font-semibold break-words text-ink">{detail.subject}</h2>
        {/* Plain text: React shows it as text, never as HTML. */}
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{detail.details}</p>
      </section>

      <section aria-labelledby="campus-request-approval" className="mt-4 rounded-panel border border-line bg-surface p-4">
        <h2 id="campus-request-approval" className="text-sm font-semibold text-ink">Approval</h2>
        {detail.approvalStatus ? (
          <>
            <p className="mt-1 text-sm text-ink">Approval: {approvalStatusLabel(detail.approvalStatus)}</p>
            <div className="mt-3">
              <ApprovalSteps steps={detail.approvalSteps} />
            </div>
          </>
        ) : (
          <p className="mt-1 text-sm text-ink-muted">This service needs no approval. The campus office handles the request directly.</p>
        )}
      </section>

      {detail.canCancel && (
        <div className="mt-4">
          {confirming ? (
            <StatusConfirmation
              question="Cancel this service request?"
              detail={
                detail.approvalStatus === 'PENDING'
                  ? `${detail.requestNumber} will be cancelled and its approval stopped. This cannot be undone.`
                  : `${detail.requestNumber} will be cancelled. This cannot be undone.`
              }
              confirmLabel="Cancel request"
              busyLabel="Cancelling…"
              dismissLabel="Keep request"
              onCancel={() => setConfirming(false)}
              onConfirm={async () => {
                try {
                  await cancelCampusRequest(detail.requestId)
                } catch (failure) {
                  if (getHttpStatus(failure) === 409) {
                    // The request changed meanwhile: the page says so and shows it as it is now.
                    setConfirming(false)
                    onChanged({ text: refusalText(409, getApiError(failure)?.message ?? null, 'cancel the request'), tone: 'error' })
                    return
                  }
                  throw failure
                }
                setConfirming(false)
                onChanged({ text: 'Request cancelled.', tone: 'success' })
              }}
              describeRefusal={(status, message) => refusalText(status, message, 'cancel the request')}
            />
          ) : (
            <ActionButton onClick={() => setConfirming(true)}>Cancel request</ActionButton>
          )}
        </div>
      )}
    </>
  )
}

/**
 * One of the account's own service requests at /campus/requests/:requestId, with its approval progress from the
 * approval engine. Another account's request and a missing one look the same: "Request unavailable".
 */
export function CampusRequestPage() {
  const { requestId: param } = useParams()
  const location = useLocation()
  const requestId = param && /^[1-9]\d{0,17}$/.test(param) ? Number(param) : null
  const load = useMemo(
    () => (requestId === null ? null : (signal: AbortSignal) => getMyCampusRequest(requestId, signal)),
    [requestId],
  )
  const request = useApiRequest(load)
  const createdNumber = (location.state as { createdNumber?: string } | null)?.createdNumber
  const [notice, setNotice] = useState<Notice | null>(
    createdNumber ? { text: `Request ${createdNumber} submitted.`, tone: 'success' } : null,
  )

  let content
  if (requestId === null || request.status === 'notFound') {
    content = <StatePanel><EmptyState title="Request unavailable" description="This service request doesn’t exist or isn’t yours." /></StatePanel>
  } else if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading service requests…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to service requests." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (request.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load service requests." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else {
    content = (
      <Detail
        detail={request.data}
        onChanged={(next) => {
          setNotice(next)
          request.refresh()
        }}
      />
    )
  }

  const detail = request.status === 'loaded' ? request.data : null
  return (
    <>
      <title>{`${detail?.requestNumber ?? 'Service request'} · Campus · Stacc`}</title>
      <Breadcrumb parent={{ to: '/campus', label: 'Campus' }} current={detail?.requestNumber} />
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <h1 className="font-mono text-2xl font-semibold tracking-tight text-ink">{detail?.requestNumber ?? 'Service request'}</h1>
        {detail && <RequestTags status={detail.status} approvalStatus={detail.approvalStatus} />}
      </div>
      <p role="status" className="mt-3 text-sm text-success empty:hidden">
        {notice?.tone === 'success' ? notice.text : ''}
      </p>
      <div role="alert" className="empty:hidden">
        {notice?.tone === 'error' && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{notice.text}</p>
        )}
      </div>
      <div className="mt-4">{content}</div>
    </>
  )
}
