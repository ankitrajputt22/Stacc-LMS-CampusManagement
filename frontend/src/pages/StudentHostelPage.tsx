import { type FormEvent, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  applyForHostel,
  getHostelQueuePlace,
  cancelHostelApplication,
  getEligibleHostelSessions,
  getMyHostelApplication,
  getMyHostelApplications,
} from '../api/hostelApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
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
import { ApprovalSteps } from '../approvals/ApprovalSteps'
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
import { TextField } from '../ui'
import { RoomProblemCard, StudentOutings } from '../hostel/outings/StudentOutings'
import { ResidentMessCard } from '../hostel/mess/Mess'
import { ResidentVisitors } from '../hostel/visitors/Visitors'
import { StudentHostelOverview } from '../hostel/student/StudentHostelOverview'
import { Card, LinkTabs } from '../ui'
import { useMyProfile } from '../person/useDisplayName'
import { StudentRoomChanges } from '../hostel/rooms/RoomChanges'

function ApplyForm({ sessions, onApplied }: { sessions: EligibleHostelSession[]; onApplied: (applicationId: number) => void }) {
  const open = sessions.filter((session) => !session.hasCurrentApplication)
  const busyRef = useRef(false)
  const [sessionId, setSessionId] = useState('')
  const [note, setNote] = useState('')
  const [preference, setPreference] = useState('')
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
      const created = await applyForHostel({
        academicSessionId: Number(sessionId),
        note: note.trim() === '' ? null : note.trim(),
        preferenceNote: preference.trim() === '' ? null : preference.trim(),
      })
      setSessionId('')
      setNote('')
      setPreference('')
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
      <div className="sm:col-span-2">
        <TextField
          label="Preference (optional)"
          hint="Such as a hostel or a ground-floor room. A wish only: the hostel office allocates beds."
          value={preference}
          maxLength={500}
          onChange={(event) => setPreference(event.target.value)}
        />
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

function QueueText({ applicationId }: { applicationId: number }) {
  const place = useApiQuery(queryKeys.hostel.queue(applicationId), (signal) => getHostelQueuePlace(applicationId, signal))
  const position = place.status === 'loaded' ? place.data.position : null
  return (
    <p className="text-sm text-ink">
      Waiting for hostel allocation.
      {position !== null && place.status === 'loaded' && ` You are number ${position} of ${place.data.waiting} in the queue.`}
    </p>
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
          <QueueText applicationId={detail.applicationId} />
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

export type HostelSection = 'overview' | 'applications' | 'room-changes' | 'outings' | 'mess' | 'visitors' | 'room-problem'

const SECTIONS: { section: HostelSection; to: string; label: string }[] = [
  { section: 'overview', to: '/hostel', label: 'Overview' },
  { section: 'applications', to: '/hostel/applications', label: 'Applications' },
  { section: 'room-changes', to: '/hostel/room-changes', label: 'Room changes' },
  { section: 'outings', to: '/hostel/outings', label: 'Outings' },
  { section: 'mess', to: '/hostel/mess', label: 'Mess' },
  { section: 'visitors', to: '/hostel/visitors', label: 'Visitors' },
  { section: 'room-problem', to: '/hostel/room-problem', label: 'Room problem' },
]

const PROCESS = [
  { title: 'Submit application', text: 'Choose the session, add an optional note and a room preference (a wish only).' },
  { title: 'Approval', text: 'The hostel administration reviews your application and approves or rejects it.' },
  { title: 'Hostel office allocates a bed', text: 'After approval, the hostel office assigns you a bed from the beds available.' },
]

function IdentityLine() {
  const person = useMyProfile()
  const { account } = useAuth()
  if (!account) {
    return null
  }
  const name = person.status === 'loaded' ? (person.data.fullName ?? person.data.displayName) : null
  return <p className="mt-1 text-[0.95rem] text-ink">{[name, account.loginId].filter(Boolean).join(' · ')}</p>
}

export function StudentHostelPage({ section = 'overview' }: { section?: HostelSection }) {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const [searchParams, setSearchParams] = useSearchParams()
  const sessions = useApiRequest(isStudent && section === 'applications' ? getEligibleHostelSessions : null)
  const list = useApiRequest(isStudent && (section === 'applications' || section === 'overview') ? getMyHostelApplications : null)
  const applications = list.status === 'loaded' ? list.data : null
  const chosen = Number(searchParams.get('application'))
  const selectedId = applications?.some((item) => item.applicationId === chosen) ? chosen
    : (applications?.find((item) => item.hasActiveAllocation) ?? applications?.[0])?.applicationId
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
  } else if (section === 'room-changes') {
    content = <StudentRoomChanges />
  } else if (section === 'outings') {
    content = <StudentOutings />
  } else if (section === 'mess') {
    content = <ResidentMessCard />
  } else if (section === 'visitors') {
    content = <ResidentVisitors />
  } else if (section === 'room-problem') {
    content = <RoomProblemCard />
  } else if (list.status === 'loading' || (section === 'applications' && sessions.status === 'loading')) {
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
  } else if (applications === null || (section === 'applications' && sessions.status !== 'loaded')) {
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
  } else if (section === 'overview') {
    content = (
      <StudentHostelOverview detail={applications.length === 0 ? null : detail.status === 'loaded' ? detail.data : null} />
    )
  } else {
    content = (
      <div className="space-y-6">
        <div className="grid items-start gap-6 xl:grid-cols-2">
          <Card title="Apply for hostel" icon="hostel">
            {sessions.status === 'loaded' && <ApplyForm sessions={sessions.data} onApplied={(id) => changed(id)} />}
          </Card>
          <Card title="Application process" icon="academics">
            <ol className="space-y-5">
              {PROCESS.map((step, index) => (
                <li key={step.title} className="flex gap-4">
                  <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-portal-bar-strong font-semibold text-portal-navy">{index + 1}</span>
                  <span>
                    <span className="block text-lg font-semibold text-ink">{step.title}</span>
                    <span className="block text-ink-muted">{step.text}</span>
                  </span>
                </li>
              ))}
            </ol>
          </Card>
        </div>
        <Card title="Your applications" icon="documents">
          {applications.length === 0 ? (
            <EmptyState title="You do not have a hostel application yet." />
          ) : (
            <>
              <ul aria-label="Hostel applications" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
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
                  <LoadingState label="Loading hostel information…" />
                ) : (
                  <ErrorState title="Couldn’t load this application." message="Try again." onRetry={detail.retry} />
                )}
              </div>
            </>
          )}
        </Card>
      </div>
    )
  }

  return (
    <>
      <title>Hostel · Stacc</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Hostel</h1>
      <p className="mt-2 text-[0.95rem] leading-6 text-ink-muted sm:text-base">Your room, requests, and hostel life.</p>
      <IdentityLine />
      {isStudent && (
        <div className="mt-5">
          <LinkTabs label="Hostel sections" tabs={SECTIONS.map(({ to, label }) => ({ to, label, end: true }))} />
        </div>
      )}
      <div className={isStudent ? '' : 'mt-6'}>{content}</div>
    </>
  )
}
