import { type FormEvent, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { approveTask, rejectTask } from '../../api/approvalsApi'
import { allocateBed, getAvailableBeds, getHostelApplication } from '../../api/hostelApi'
import { useApiRequest } from '../../api/useApiRequest'
import { Button } from '../../components/Button'
import { Breadcrumb } from '../../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { formatDateTime } from '../../courses/dates'
import { border, fieldClasses } from '../../teaching/formStyles'
import type { HostelAdminApplicationDetail } from '../../types/hostel'
import { ApprovalSteps } from '../ApprovalSteps'
import {
  allocationStatusLabel,
  approvalStatusLabel,
  approvalTagClasses,
  COMMENT_MAX,
  plainTagClasses,
  refusalText,
  SIGN_IN_AGAIN,
} from '../hostelText'
import { VacateButton } from './VacateButton'

const selectClasses = `h-11 ${fieldClasses} ${border(false)}`

/** A page-level message after a change, or after a refusal that reloads the application. */
type Notify = (message: string, tone?: 'success' | 'error') => void

/**
 * Approve or Reject the current approval step through the approval engine's task routes. Approving takes an optional
 * comment; rejecting needs one. The backend checks the step's role and APPROVAL_TASK_DECIDE again.
 */
function DecisionForm({ stepId, onDecided }: { stepId: number; onDecided: Notify }) {
  const busyRef = useRef(false)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function decide(kind: 'approve' | 'reject') {
    if (busyRef.current) {
      return
    }
    if (kind === 'reject' && comment.trim() === '') {
      setError('A comment is required to reject.')
      document.getElementById('hostel-decision-comment')?.focus()
      return
    }
    busyRef.current = true
    setBusy(kind)
    setError(null)
    try {
      if (kind === 'approve') {
        await approveTask(stepId, comment)
      } else {
        await rejectTask(stepId, comment)
      }
      onDecided(kind === 'approve' ? 'Application approved.' : 'Application rejected.')
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const text = refusalText(status, getApiError(failure)?.message ?? null, 'record the decision')
        if (status === 409 || status === 404) {
          // Decided elsewhere meanwhile: the page says so and shows the application as it is now.
          onDecided(text, 'error')
        } else {
          setError(text)
        }
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  return (
    <form onSubmit={(event: FormEvent) => event.preventDefault()} aria-label="Decision" className="mt-4 rounded-control border border-line-strong p-3">
      <label htmlFor="hostel-decision-comment" className="block text-sm font-medium text-ink">
        Comment
      </label>
      <p id="hostel-decision-comment-hint" className="mt-0.5 text-xs text-ink-muted">
        Optional when approving, required when rejecting. The student sees it as plain text.
      </p>
      <textarea
        id="hostel-decision-comment"
        rows={3}
        maxLength={COMMENT_MAX}
        value={comment}
        aria-describedby="hostel-decision-comment-hint"
        onChange={(event) => {
          setComment(event.target.value)
          setError(null)
        }}
        className={`py-2 ${fieldClasses} ${border(Boolean(error))}`}
      />
      <div role="alert">
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </div>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <Button aria-disabled={busy !== null || undefined} onClick={() => decide('approve')}>
          {busy === 'approve' ? 'Approving…' : 'Approve'}
        </Button>
        <Button variant="secondary" aria-disabled={busy !== null || undefined} onClick={() => decide('reject')}>
          {busy === 'reject' ? 'Rejecting…' : 'Reject'}
        </Button>
      </div>
    </form>
  )
}

/** Hostel, then room, then bed: only beds the backend reports as available. A 409 reloads the choices. */
function AllocationForm({ applicationId, onAllocated }: { applicationId: number; onAllocated: Notify }) {
  const beds = useApiRequest(getAvailableBeds)
  const busyRef = useRef(false)
  const [hostelId, setHostelId] = useState('')
  const [roomId, setRoomId] = useState('')
  const [bedId, setBedId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (beds.status === 'loading') {
    return <LoadingState label="Loading available beds…" />
  }
  if (beds.status !== 'loaded') {
    return <ErrorState title="Couldn’t load available beds." message="Try again." onRetry={beds.retry} />
  }
  if (beds.data.length === 0) {
    return <p className="text-sm text-ink-muted">No beds are available. Add beds or vacate an allocation first.</p>
  }
  const hostels = [...new Map(beds.data.map((bed) => [bed.hostelId, `${bed.hostelName} (${bed.hostelCode})`])).entries()]
  const rooms = [...new Map(beds.data.filter((bed) => String(bed.hostelId) === hostelId).map((bed) => [bed.roomId, bed.roomNumber])).entries()]
  const roomBeds = beds.data.filter((bed) => String(bed.roomId) === roomId)

  async function allocate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) {
      return
    }
    if (bedId === '') {
      setError('Choose a hostel, a room, and a bed.')
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      const allocation = await allocateBed(applicationId, Number(bedId))
      onAllocated(`Allocated ${allocation.hostelName}, Room ${allocation.roomNumber}, Bed ${allocation.bedNumber}.`)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const text = refusalText(status, getApiError(failure)?.message ?? null, 'allocate the bed')
        if (status !== 409) {
          setError(text)
        } else {
          // The bed or the application changed meanwhile: the choices and the application are loaded again.
          setBedId('')
          beds.refresh()
          onAllocated(text, 'error')
        }
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form onSubmit={allocate} noValidate aria-label="Allocate a bed" className="grid gap-3 sm:grid-cols-3">
      <div>
        <label htmlFor="allocate-hostel" className="block text-sm font-medium text-ink">Hostel</label>
        <select id="allocate-hostel" value={hostelId} onChange={(event) => { setHostelId(event.target.value); setRoomId(''); setBedId('') }} className={selectClasses}>
          <option value="">Choose a hostel</option>
          {hostels.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="allocate-room" className="block text-sm font-medium text-ink">Room</label>
        <select id="allocate-room" value={roomId} disabled={hostelId === ''} onChange={(event) => { setRoomId(event.target.value); setBedId('') }} className={selectClasses}>
          <option value="">Choose a room</option>
          {rooms.map(([id, number]) => <option key={id} value={id}>{number}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="allocate-bed" className="block text-sm font-medium text-ink">Bed</label>
        <select id="allocate-bed" value={bedId} disabled={roomId === ''} onChange={(event) => setBedId(event.target.value)} className={selectClasses}>
          <option value="">Choose a bed</option>
          {roomBeds.map((bed) => <option key={bed.bedId} value={bed.bedId}>{bed.bedNumber}</option>)}
        </select>
      </div>
      <div role="alert" className="sm:col-span-3 empty:hidden">
        {error && <p className="rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error}</p>}
      </div>
      <div className="sm:col-span-3">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Allocating…' : 'Allocate bed'}
        </Button>
      </div>
    </form>
  )
}

function Review({ detail, onChanged }: { detail: HostelAdminApplicationDetail; onChanged: Notify }) {
  const current = detail.currentApprovalStep
  return (
    <>
      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 rounded-panel border border-line bg-surface p-4 text-sm lg:grid-cols-4">
        <div className="min-w-0"><dt className="text-xs font-medium text-ink-muted">Student</dt><dd className="break-words text-ink">{detail.studentLoginId}</dd></div>
        <div><dt className="text-xs font-medium text-ink-muted">Academic Session</dt><dd className="text-ink">{detail.academicSessionCode}</dd></div>
        <div><dt className="text-xs font-medium text-ink-muted">Submitted</dt><dd className="text-ink"><time dateTime={detail.submittedAt}>{formatDateTime(detail.submittedAt)}</time></dd></div>
        <div><dt className="text-xs font-medium text-ink-muted">Approval</dt><dd className="text-ink">{approvalStatusLabel(detail.approvalStatus)}</dd></div>
      </dl>
      {detail.note && (
        <section aria-labelledby="hostel-note-heading" className="mt-4 rounded-panel border border-line bg-surface p-4">
          <h2 id="hostel-note-heading" className="text-sm font-semibold text-ink">Student note</h2>
          <p className="mt-1 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{detail.note}</p>
        </section>
      )}

      <section aria-labelledby="hostel-approval-heading" className="mt-4 rounded-panel border border-line bg-surface p-4">
        <h2 id="hostel-approval-heading" className="text-sm font-semibold text-ink">Approval progress</h2>
        <div className="mt-2">
          <ApprovalSteps steps={detail.approvalSteps} />
        </div>
        {current && detail.canDecideCurrentStep && <DecisionForm stepId={current.approvalRequestStepId} onDecided={onChanged} />}
        {current && !detail.canDecideCurrentStep && (
          <p className="mt-3 text-sm text-ink-muted">
            {current.stepName} needs an account holding the {current.requiredRoleCode} role with approval rights, other than the student.
          </p>
        )}
      </section>

      {detail.canAllocate && (
        <section aria-labelledby="hostel-allocate-heading" className="mt-4 rounded-panel border border-line bg-surface p-4">
          <h2 id="hostel-allocate-heading" className="text-sm font-semibold text-ink">Allocate bed</h2>
          <div className="mt-3">
            <AllocationForm applicationId={detail.applicationId} onAllocated={onChanged} />
          </div>
        </section>
      )}

      <section aria-labelledby="hostel-allocations-heading" className="mt-4 rounded-panel border border-line bg-surface p-4">
        <h2 id="hostel-allocations-heading" className="text-sm font-semibold text-ink">Allocations</h2>
        {detail.allocations.length === 0 ? (
          <p className="mt-2 text-sm text-ink-muted">No bed has been allocated.</p>
        ) : (
          <ul className="mt-2 space-y-3">
            {detail.allocations.map((allocation) => (
              <li key={allocation.allocationId} className="text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="break-words text-ink">{allocation.hostelName}, Room {allocation.roomNumber}, Bed {allocation.bedNumber}</span>
                  <span className={plainTagClasses(allocation.status === 'VACATED')}>{allocationStatusLabel(allocation.status)}</span>
                </div>
                <p className="text-xs text-ink-muted">
                  Allocated <time dateTime={allocation.allocatedAt}>{formatDateTime(allocation.allocatedAt)}</time> by {allocation.allocatedByLoginId}
                  {allocation.vacatedAt && (
                    <> · Vacated <time dateTime={allocation.vacatedAt}>{formatDateTime(allocation.vacatedAt)}</time> by {allocation.vacatedByLoginId}</>
                  )}
                </p>
                {allocation.status === 'ACTIVE' && (
                  <div className="mt-1">
                    <VacateButton allocationId={allocation.allocationId} label={`${allocation.roomNumber} ${allocation.bedNumber}`} onVacated={() => onChanged('Allocation vacated.')} />
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

/** One application for the hostel office at /admin/hostel/applications/:applicationId. */
export function ApplicationReview() {
  const { applicationId: param } = useParams()
  const applicationId = param && /^[1-9]\d{0,17}$/.test(param) ? Number(param) : null
  const load = useMemo(
    () => (applicationId === null ? null : (signal: AbortSignal) => getHostelApplication(applicationId, signal)),
    [applicationId],
  )
  const request = useApiRequest(load)
  const [notice, setNotice] = useState<{ text: string; tone: 'success' | 'error' } | null>(null)

  let content
  if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading hostel applications…" /></StatePanel>
  } else if (request.status === 'notFound') {
    content = <StatePanel><EmptyState title="Application unavailable" description="This hostel application doesn’t exist." /></StatePanel>
  } else if (request.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to hostel management." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (request.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load this application." message="Check your connection and try again." onRetry={request.retry} /></StatePanel>
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
      <Breadcrumb parent={{ to: '/admin/hostel/applications', label: 'Applications' }} current={detail?.applicationNumber} />
      {detail && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <h2 className="font-mono text-lg font-semibold text-ink">{detail.applicationNumber}</h2>
          <span className={approvalTagClasses(detail.approvalStatus)}>
            <span className="sr-only">Approval status: </span>
            {approvalStatusLabel(detail.approvalStatus)}
          </span>
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
