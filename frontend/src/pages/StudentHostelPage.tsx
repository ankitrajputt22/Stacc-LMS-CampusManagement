import { type FormEvent, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  applyForHostel,
  cancelHostelApplication,
  getEligibleHostelSessions,
  getMyHostelApplication,
  getMyHostelApplications,
} from '../api/hostelApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { ApprovalSteps } from '../hostel/ApprovalSteps'
import {
  allocationStatusLabel,
  approvalStatusLabel,
  approvalTagClasses,
  NOTE_MAX,
  plainTagClasses,
  refusalText,
  SIGN_IN_AGAIN,
} from '../hostel/hostelText'
import { border, fieldClasses } from '../teaching/formStyles'
import type { EligibleHostelSession, HostelApplicationDetail, StudentHostelAllocation } from '../types/hostel'

function ApplyForm({ sessions, onApplied }: { sessions: EligibleHostelSession[]; onApplied: (applicationId: number) => void }) {
  const open = sessions.filter((session) => !session.hasCurrentApplication)
  const busyRef = useRef(false)
  const [sessionId, setSessionId] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (sessions.length === 0) {
    return <p className="text-sm text-ink-muted">You can apply once you are enrolled in an academic session.</p>
  }
  if (open.length === 0) {
    return <p className="text-sm text-ink-muted">You already have a pending or approved application for each session you are enrolled in.</p>
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) {
      return
    }
    if (sessionId === '') {
      setError('Choose an academic session.')
      document.getElementById('hostel-session')?.focus()
      return
    }
    if (note.trim().length > NOTE_MAX) {
      setError(`Use at most ${NOTE_MAX.toLocaleString()} characters in the note.`)
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      // Only the session and note are sent: the student is the account in the access token.
      const created = await applyForHostel({ academicSessionId: Number(sessionId), note: note.trim() === '' ? null : note.trim() })
      setSessionId('')
      setNote('')
      onApplied(created.applicationId)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusalText(status, getApiError(failure)?.message ?? null, 'submit the application'))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Apply for hostel" className="grid gap-4 sm:grid-cols-2">
      <div>
        <label htmlFor="hostel-session" className="block text-sm font-medium text-ink">
          Academic Session
        </label>
        <select
          id="hostel-session"
          value={sessionId}
          aria-invalid={error !== null && sessionId === '' ? true : undefined}
          onChange={(event) => {
            setSessionId(event.target.value)
            setError(null)
          }}
          className={`h-11 ${fieldClasses} ${border(false)}`}
        >
          <option value="">Choose a session</option>
          {open.map((session) => (
            <option key={session.academicSessionId} value={session.academicSessionId}>
              {session.academicSessionCode}
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2">
        <label htmlFor="hostel-note" className="block text-sm font-medium text-ink">
          Note <span className="font-normal text-ink-muted">(optional)</span>
        </label>
        <textarea
          id="hostel-note"
          rows={3}
          maxLength={NOTE_MAX}
          value={note}
          aria-describedby="hostel-note-hint"
          onChange={(event) => setNote(event.target.value)}
          className={`py-2 ${fieldClasses} ${border(false)}`}
        />
        <p id="hostel-note-hint" className="mt-1 text-xs text-ink-muted">
          Anything the hostel office should know. Plain text.
        </p>
      </div>
      <div role="alert" className="sm:col-span-2 empty:hidden">
        {error && <p className="rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error}</p>}
      </div>
      <div className="sm:col-span-2">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Applying…' : 'Apply for hostel'}
        </Button>
      </div>
    </form>
  )
}

function AllocationFacts({ allocation }: { allocation: StudentHostelAllocation }) {
  return (
    <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-5">
      <div className="min-w-0">
        <dt className="text-xs font-medium text-ink-muted">Hostel</dt>
        <dd className="break-words text-ink">{allocation.hostelName}</dd>
      </div>
      <div>
        <dt className="text-xs font-medium text-ink-muted">Room</dt>
        <dd className="break-words text-ink">{allocation.roomNumber}</dd>
      </div>
      <div>
        <dt className="text-xs font-medium text-ink-muted">Bed</dt>
        <dd className="break-words text-ink">{allocation.bedNumber}</dd>
      </div>
      <div>
        <dt className="text-xs font-medium text-ink-muted">Allocated</dt>
        <dd className="text-ink">
          <time dateTime={allocation.allocatedAt}>{formatDateTime(allocation.allocatedAt)}</time>
        </dd>
      </div>
      <div>
        <dt className="text-xs font-medium text-ink-muted">Status</dt>
        <dd className="text-ink">{allocationStatusLabel(allocation.status)}</dd>
      </div>
    </dl>
  )
}

function ApplicationPanel({ detail, onChanged }: { detail: HostelApplicationDetail; onChanged: () => void }) {
  const [confirming, setConfirming] = useState(false)
  return (
    <article aria-labelledby="hostel-application-heading" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h3 id="hostel-application-heading" className="font-mono text-sm font-semibold text-ink">
          {detail.applicationNumber}
        </h3>
        <span className={approvalTagClasses(detail.approvalStatus)}>
          <span className="sr-only">Approval status: </span>
          {approvalStatusLabel(detail.approvalStatus)}
        </span>
      </div>
      <p className="mt-1 text-sm text-ink-muted">
        {detail.academicSessionCode} · Submitted <time dateTime={detail.submittedAt}>{formatDateTime(detail.submittedAt)}</time>
      </p>
      {detail.note && <p className="mt-2 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{detail.note}</p>}

      <h4 className="mt-5 text-sm font-semibold text-ink">Approval progress</h4>
      <div className="mt-2">
        <ApprovalSteps steps={detail.approvalSteps} />
      </div>

      <h4 className="mt-5 text-sm font-semibold text-ink">Allocation</h4>
      <div className="mt-2">
        {detail.activeAllocation ? (
          <AllocationFacts allocation={detail.activeAllocation} />
        ) : detail.approvalStatus === 'APPROVED' ? (
          <p className="text-sm text-ink">Waiting for hostel allocation.</p>
        ) : (
          <p className="text-sm text-ink-muted">A bed is allocated by the hostel office after approval.</p>
        )}
      </div>

      {detail.pastAllocations.length > 0 && (
        <>
          <h4 className="mt-5 text-sm font-semibold text-ink">Earlier allocations</h4>
          <ul className="mt-2 space-y-1 text-sm text-ink">
            {detail.pastAllocations.map((past, index) => (
              <li key={index} className="break-words">
                {past.hostelName}, Room {past.roomNumber}, Bed {past.bedNumber} ·{' '}
                <span className={plainTagClasses(true)}>{allocationStatusLabel(past.status)}</span>{' '}
                {past.vacatedAt && (
                  <span className="text-xs text-ink-muted">
                    until <time dateTime={past.vacatedAt}>{formatDateTime(past.vacatedAt)}</time>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {detail.canCancel && (
        <div className="mt-5">
          {confirming ? (
            <StatusConfirmation
              question="Cancel this hostel application?"
              detail="This stops the current approval workflow."
              confirmLabel="Cancel application"
              busyLabel="Cancelling…"
              dismissLabel="Keep application"
              onCancel={() => setConfirming(false)}
              onConfirm={async () => {
                await cancelHostelApplication(detail.applicationId)
                setConfirming(false)
                onChanged()
              }}
              describeRefusal={(status, message) => refusalText(status, message, 'cancel the application')}
            />
          ) : (
            <ActionButton onClick={() => setConfirming(true)}>Cancel application</ActionButton>
          )}
        </div>
      )}
    </article>
  )
}

/**
 * The student's Hostel page at /hostel: applying for a session they are enrolled in, their applications with the
 * approval progress from the approval engine, and their allocation. Students never choose a hostel, room, or bed, and
 * nothing here decides an approval. Only accounts holding STUDENT see it; the backend checks every request.
 */
export function StudentHostelPage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const [searchParams, setSearchParams] = useSearchParams()
  const sessions = useApiRequest(isStudent ? getEligibleHostelSessions : null)
  const list = useApiRequest(isStudent ? getMyHostelApplications : null)
  const applications = list.status === 'loaded' ? list.data : null
  const chosen = Number(searchParams.get('application'))
  const selectedId = applications?.some((item) => item.applicationId === chosen) ? chosen : applications?.[0]?.applicationId
  const loadDetail = useMemo(
    () => (selectedId === undefined ? null : (signal: AbortSignal) => getMyHostelApplication(selectedId, signal)),
    [selectedId],
  )
  const detail = useApiRequest(loadDetail)

  function changed(applicationId?: number) {
    if (applicationId !== undefined) {
      setSearchParams({ application: String(applicationId) })
    }
    sessions.refresh()
    list.refresh()
    detail.refresh()
  }

  let content
  if (!isStudent) {
    content = (
      <StatePanel>
        <AccessDeniedState title="The Hostel page is for student accounts." />
      </StatePanel>
    )
  } else if (list.status === 'loading' || sessions.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading hostel information…" />
      </StatePanel>
    )
  } else if (list.status === 'denied' || sessions.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to hostel applications." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (applications === null || sessions.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState
          title="Couldn’t load hostel information."
          message="Check your connection and try again."
          onRetry={() => {
            list.retry()
            sessions.retry()
          }}
        />
      </StatePanel>
    )
  } else {
    content = (
      <>
        <section aria-labelledby="hostel-apply" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
          <h2 id="hostel-apply" className="text-base font-semibold text-ink">
            Apply for hostel
          </h2>
          <div className="mt-3">
            <ApplyForm sessions={sessions.data} onApplied={(id) => changed(id)} />
          </div>
        </section>

        <section aria-labelledby="hostel-applications" className="mt-6">
          <h2 id="hostel-applications" className="text-base font-semibold text-ink">
            Your applications
          </h2>
          {applications.length === 0 ? (
            <div className="mt-3">
              <StatePanel>
                <EmptyState title="You do not have a hostel application yet." />
              </StatePanel>
            </div>
          ) : (
            <>
              <ul aria-label="Hostel applications" className="mt-3 divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
                {applications.map((item) => (
                  <li key={item.applicationId} className={`px-4 py-3 sm:px-5 ${item.applicationId === selectedId ? 'bg-surface-muted' : ''}`}>
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-sm text-ink">{item.applicationNumber}</span>
                          <span className={approvalTagClasses(item.approvalStatus)}>
                            <span className="sr-only">Approval status: </span>
                            {approvalStatusLabel(item.approvalStatus)}
                          </span>
                          {item.hasActiveAllocation && <span className={plainTagClasses()}>Allocated</span>}
                        </div>
                        <p className="mt-0.5 text-xs text-ink-muted">
                          {item.academicSessionCode} · Submitted{' '}
                          <time dateTime={item.submittedAt}>{formatDateTime(item.submittedAt)}</time>
                        </p>
                      </div>
                      {item.applicationId === selectedId ? (
                        <span className="text-sm text-ink-muted">Shown below</span>
                      ) : (
                        <ActionButton onClick={() => setSearchParams({ application: String(item.applicationId) })}>
                          View details<span className="sr-only"> of {item.applicationNumber}</span>
                        </ActionButton>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <div className="mt-4">
                {detail.status === 'loaded' ? (
                  <ApplicationPanel detail={detail.data} onChanged={() => changed()} />
                ) : detail.status === 'loading' ? (
                  <StatePanel>
                    <LoadingState label="Loading hostel information…" />
                  </StatePanel>
                ) : (
                  <StatePanel>
                    <ErrorState title="Couldn’t load this application." message="Try again." onRetry={detail.retry} />
                  </StatePanel>
                )}
              </div>
            </>
          )}
        </section>
      </>
    )
  }

  return (
    <>
      <title>Hostel · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Hostel</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">View and manage your hostel application and allocation.</p>
      <div className="mt-6">{content}</div>
    </>
  )
}
