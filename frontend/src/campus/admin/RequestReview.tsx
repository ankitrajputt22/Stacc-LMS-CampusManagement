import { useMemo, useState } from 'react'
import { useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../../api/apiError'
import {
  completeCampusRequest,
  completeCampusRequestWithDocument,
  getAdminCampusRequest,
  markCampusRequestReady,
  startProcessingCampusRequest,
} from '../../api/campusApi'
import { useApiRequest } from '../../api/useApiRequest'
import { ApprovalSteps } from '../../approvals/ApprovalSteps'
import { approvalStatusLabel, refusalText, SIGN_IN_AGAIN } from '../../approvals/approvalText'
import { DecisionForm } from '../../approvals/DecisionForm'
import { ActionButton } from '../../components/ActionButton'
import { Breadcrumb } from '../../components/navigation/Breadcrumb'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { formatDateTime } from '../../courses/dates'
import type { AdminCampusRequestDetail } from '../../types/campus'
import { RequestTags } from '../RequestTags'
import { requestStatusLabel } from '../campusText'

type Notify = (message: string, tone?: 'success' | 'error') => void

function CompleteButton({ detail, onChanged }: { detail: AdminCampusRequestDetail; onChanged: Notify }) {
  const [confirming, setConfirming] = useState(false)
  if (!confirming) {
    return <ActionButton onClick={() => setConfirming(true)}>Mark completed</ActionButton>
  }
  return (
    <StatusConfirmation
      question="Mark this service request as completed?"
      detail={`${detail.requestNumber} will be marked completed and ${detail.requesterLoginId} will be told in their Inbox. This cannot be undone.`}
      confirmLabel="Mark completed"
      busyLabel="Completing…"
      onCancel={() => setConfirming(false)}
      onConfirm={async () => {
        try {
          await completeCampusRequest(detail.requestId)
        } catch (failure) {
          if (getHttpStatus(failure) === 409) {
            // The request changed meanwhile: the page says so and shows it as it is now.
            setConfirming(false)
            onChanged(refusalText(409, getApiError(failure)?.message ?? null, 'complete the request'), 'error')
            return
          }
          throw failure
        }
        setConfirming(false)
        onChanged('Request marked completed.')
      }}
      describeRefusal={(status, message) => refusalText(status, message, 'complete the request')}
    />
  )
}

/** Being processed and Ready: steps forward only, each telling the page what happened. */
function ProgressButtons({ detail, onChanged }: { detail: AdminCampusRequestDetail; onChanged: Notify }) {
  const [busy, setBusy] = useState(false)
  async function step(action: () => Promise<unknown>, done: string, what: string) {
    setBusy(true)
    try {
      await action()
      onChanged(done)
    } catch (failure) {
      onChanged(refusalText(getHttpStatus(failure), getApiError(failure)?.message ?? null, what), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      {detail.canProcess && (
        <ActionButton disabled={busy} onClick={() => void step(() => startProcessingCampusRequest(detail.requestId), 'Marked as being processed.', 'update the request')}>
          Mark being processed
        </ActionButton>
      )}
      {detail.canMarkReady && (
        <ActionButton
          disabled={busy}
          onClick={() => void step(() => markCampusRequestReady(detail.requestId), `Marked ready. ${detail.requesterLoginId} was told in their Inbox.`, 'update the request')}
        >
          Mark ready
        </ActionButton>
      )}
    </>
  )
}

/** Complete with an issued document: a title and one file, placed in the requester's document vault. */
function CompleteWithDocument({ detail, onChanged }: { detail: AdminCampusRequestDetail; onChanged: Notify }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (!open) {
    return <ActionButton onClick={() => setOpen(true)}>Complete with a document</ActionButton>
  }
  return (
    <form
      className="mt-3 w-full space-y-3 rounded-control border border-line p-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (title.trim() === '' || file === null) {
          setError('Enter a title and choose the file.')
          return
        }
        setBusy(true)
        setError(null)
        void completeCampusRequestWithDocument(detail.requestId, title.trim(), file)
          .then(() => onChanged(`Request completed. The document is in ${detail.requesterLoginId}’s document vault.`))
          .catch((failure) => {
            setError(refusalText(getHttpStatus(failure), getApiError(failure)?.message ?? null, 'complete the request'))
            setBusy(false)
          })
      }}
    >
      <p className="text-sm text-ink-muted">The document goes into the requester’s document vault with a verification code, and the request is completed. This cannot be undone.</p>
      <label className="block text-sm font-medium text-ink" htmlFor="campus-document-title">Document title</label>
      <input id="campus-document-title" className="h-10 w-full rounded-control border border-line-strong px-3 text-sm" maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} />
      <label className="block text-sm font-medium text-ink" htmlFor="campus-document-file">Document file</label>
      <input id="campus-document-file" type="file" className="text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      {error && <p role="alert" className="text-sm text-danger">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <ActionButton type="submit" disabled={busy}>{busy ? 'Completing…' : 'Complete with this document'}</ActionButton>
        <ActionButton onClick={() => setOpen(false)}>Cancel</ActionButton>
      </div>
    </form>
  )
}

function Review({ detail, onChanged }: { detail: AdminCampusRequestDetail; onChanged: Notify }) {
  const current = detail.currentApprovalStep
  return (
    <>
      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 rounded-panel border border-line bg-surface p-4 text-sm lg:grid-cols-5">
        <div className="min-w-0"><dt className="text-xs font-medium text-ink-muted">Requester</dt><dd className="break-words text-ink">{detail.requesterLoginId}</dd></div>
        <div className="min-w-0"><dt className="text-xs font-medium text-ink-muted">Service</dt><dd className="break-words text-ink">{detail.serviceName}</dd></div>
        <div><dt className="text-xs font-medium text-ink-muted">Submitted</dt><dd className="text-ink"><time dateTime={detail.submittedAt}>{formatDateTime(detail.submittedAt)}</time></dd></div>
        <div><dt className="text-xs font-medium text-ink-muted">Request status</dt><dd className="text-ink">{requestStatusLabel(detail.status)}</dd></div>
        <div>
          <dt className="text-xs font-medium text-ink-muted">{detail.completedAt ? 'Completed' : 'Approval'}</dt>
          <dd className="text-ink">
            {detail.completedAt ? (
              <time dateTime={detail.completedAt}>{formatDateTime(detail.completedAt)}</time>
            ) : detail.approvalStatus ? (
              approvalStatusLabel(detail.approvalStatus)
            ) : (
              'Not needed'
            )}
          </dd>
        </div>
      </dl>

      <section aria-labelledby="campus-review-subject" className="mt-4 rounded-panel border border-line bg-surface p-4">
        <h3 id="campus-review-subject" className="text-sm font-semibold break-words text-ink">{detail.subject}</h3>
        {/* Plain text: React shows it as text, never as HTML. */}
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{detail.details}</p>
      </section>

      {detail.approvalStatus && (
        <section aria-labelledby="campus-review-approval" className="mt-4 rounded-panel border border-line bg-surface p-4">
          <h3 id="campus-review-approval" className="text-sm font-semibold text-ink">Approval progress</h3>
          <div className="mt-2">
            <ApprovalSteps steps={detail.approvalSteps} />
          </div>
          {current && detail.canDecide && (
            <DecisionForm
              stepId={current.approvalRequestStepId}
              onDecided={onChanged}
              idPrefix="campus"
              approvedMessage="Request approved."
              rejectedMessage="Request rejected."
              audienceHint="The requester sees it as plain text."
            />
          )}
          {current && !detail.canDecide && (
            <p className="mt-3 text-sm text-ink-muted">
              {current.stepName} needs an account holding the {current.requiredRoleCode} role with approval rights, other than the requester.
            </p>
          )}
        </section>
      )}

      {detail.issuedDocumentTitle && (
        <p className="mt-4 text-sm text-ink">Issued document: {detail.issuedDocumentTitle}</p>
      )}

      {detail.canComplete && (
        <section aria-labelledby="campus-review-handling" className="mt-4 rounded-panel border border-line bg-surface p-4">
          <h3 id="campus-review-handling" className="text-sm font-semibold text-ink">Handling</h3>
          <p className="mt-1 text-sm text-ink-muted">
            Show the requester how it is going, then mark it completed once the service has been provided, with a document if one was issued.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <ProgressButtons detail={detail} onChanged={onChanged} />
            <CompleteButton detail={detail} onChanged={onChanged} />
            <CompleteWithDocument detail={detail} onChanged={onChanged} />
          </div>
        </section>
      )}
    </>
  )
}

/** One service request for admins at /admin/campus/requests/:requestId. Approving uses the approval engine's routes. */
export function RequestReview() {
  const { requestId: param } = useParams()
  const requestId = param && /^[1-9]\d{0,17}$/.test(param) ? Number(param) : null
  const load = useMemo(
    () => (requestId === null ? null : (signal: AbortSignal) => getAdminCampusRequest(requestId, signal)),
    [requestId],
  )
  const request = useApiRequest(load)
  const [notice, setNotice] = useState<{ text: string; tone: 'success' | 'error' } | null>(null)

  let content
  if (requestId === null || request.status === 'notFound') {
    content = <StatePanel><EmptyState title="Request unavailable" description="This service request doesn’t exist." /></StatePanel>
  } else if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading campus service requests…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to campus service requests." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (request.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load service requests." message="Check your connection and try again." onRetry={request.retry} /></StatePanel>
  } else {
    content = (
      <Review
        detail={request.data}
        onChanged={(message, tone = 'success') => {
          setNotice({ text: message, tone })
          request.refresh()
        }}
      />
    )
  }

  const detail = request.status === 'loaded' ? request.data : null
  return (
    <>
      <Breadcrumb parent={{ to: '/admin/campus/requests', label: 'Service Requests' }} current={detail?.requestNumber} />
      {detail && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <h2 className="font-mono text-lg font-semibold text-ink">{detail.requestNumber}</h2>
          <RequestTags status={detail.status} approvalStatus={detail.approvalStatus} />
        </div>
      )}
      <p role="status" className="mt-3 text-sm text-success empty:hidden">
        {notice?.tone === 'success' ? notice.text : ''}
      </p>
      <div role="alert" className="empty:hidden">
        {notice?.tone === 'error' && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{notice.text}</p>
        )}
      </div>
      <div className="mt-2">{content}</div>
    </>
  )
}
