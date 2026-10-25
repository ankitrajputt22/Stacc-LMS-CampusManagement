import { type FormEvent, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getAcademicSessions } from '../../api/adminAcademicApi'
import { getHostelApplications } from '../../api/hostelApi'
import { useApiRequest } from '../../api/useApiRequest'
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
import type { ApprovalStatus } from '../../types/approvals'
import { APPROVAL_STATUSES, approvalStatusLabel, approvalTagClasses, plainTagClasses, SIGN_IN_AGAIN } from '../hostelText'

const PAGE_SIZE = 20
const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

/** Every hostel application, newest first, filtered by session, approval status, and student, page by page. */
export function ApplicationsSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const sessionParam = searchParams.get('session')
  const academicSessionId = sessionParam && /^\d+$/.test(sessionParam) ? Number(sessionParam) : undefined
  const approvalStatus = APPROVAL_STATUSES.find((status) => status === searchParams.get('status')) as ApprovalStatus | undefined
  const studentLoginId = searchParams.get('student')?.trim() || undefined
  const pageParam = searchParams.get('page')
  const page = pageParam && /^\d+$/.test(pageParam) ? Number(pageParam) : 0
  const [studentInput, setStudentInput] = useState(studentLoginId ?? '')
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)

  const load = useMemo(
    () => (signal: AbortSignal) =>
      getHostelApplications({ academicSessionId, approvalStatus, studentLoginId, page, size: PAGE_SIZE }, signal),
    [academicSessionId, approvalStatus, studentLoginId, page],
  )
  const request = useApiRequest(load)
  const sessions = useApiRequest(getAcademicSessions)

  function update(change: Record<string, string | undefined>, keepPage = false) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(change)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!keepPage) next.delete('page')
    setSearchParams(next)
  }

  function applyStudent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    update({ student: studentInput.trim() })
  }

  let content
  if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading hostel applications…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to hostel management." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (request.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load hostel applications." message="Check your connection and try again." onRetry={request.retry} />
      </StatePanel>
    )
  } else if (request.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No hostel applications found." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="Hostel applications" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {request.data.items.map((item) => (
            <li key={item.applicationId} className="px-4 py-3 sm:px-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-ink">{item.applicationNumber}</span>
                    <span className={approvalTagClasses(item.approvalStatus)}>
                      <span className="sr-only">Approval status: </span>
                      {approvalStatusLabel(item.approvalStatus)}
                    </span>
                    {item.activeAllocation && <span className={plainTagClasses()}>Allocated</span>}
                  </div>
                  <p className="mt-0.5 text-sm break-words text-ink">
                    {item.studentLoginId} · {item.academicSessionCode}
                    {item.activeAllocation &&
                      ` · ${item.activeAllocation.hostelName}, Room ${item.activeAllocation.roomNumber}, Bed ${item.activeAllocation.bedNumber}`}
                  </p>
                  <p className="text-xs text-ink-muted">
                    Submitted <time dateTime={item.submittedAt}>{formatDateTime(item.submittedAt)}</time>
                  </p>
                </div>
                <Link to={`/admin/hostel/applications/${item.applicationId}`} className={`${actionLinkClasses} shrink-0 self-start`}>
                  Review<span className="sr-only">: {item.applicationNumber}, {item.studentLoginId}</span>
                </Link>
              </div>
            </li>
          ))}
        </ul>
        <Pager
          page={request.data.page}
          totalPages={request.data.totalPages}
          label="Application pages"
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
      <div role="group" aria-label="Filter applications" className="grid gap-3 rounded-panel border border-line bg-surface p-4 sm:grid-cols-3">
        <div>
          <label htmlFor="hostel-filter-session" className="block text-sm font-medium text-ink">Academic Session</label>
          <select id="hostel-filter-session" value={academicSessionId ?? ''} onChange={(event) => update({ session: event.target.value })} className={selectClasses}>
            <option value="">All sessions</option>
            {sessions.status === 'loaded' &&
              sessions.data.map((session) => (
                <option key={session.id} value={session.id}>
                  {session.code}
                </option>
              ))}
          </select>
        </div>
        <div>
          <label htmlFor="hostel-filter-status" className="block text-sm font-medium text-ink">Approval status</label>
          <select id="hostel-filter-status" value={approvalStatus ?? ''} onChange={(event) => update({ status: event.target.value })} className={selectClasses}>
            <option value="">All statuses</option>
            {APPROVAL_STATUSES.map((status) => (
              <option key={status} value={status}>
                {approvalStatusLabel(status)}
              </option>
            ))}
          </select>
        </div>
        <form onSubmit={applyStudent} className="min-w-0">
          <label htmlFor="hostel-filter-student" className="block text-sm font-medium text-ink">Student (login ID)</label>
          <div className="mt-1.5 flex gap-2">
            <input
              id="hostel-filter-student"
              type="text"
              autoComplete="off"
              maxLength={100}
              value={studentInput}
              onChange={(event) => setStudentInput(event.target.value)}
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
