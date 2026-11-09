import { useMemo, useRef } from 'react'
import { useSearchParams } from 'react-router'
import { getAcademicSessions } from '../../api/adminAcademicApi'
import { getHostelAllocations, getHostels } from '../../api/hostelApi'
import { useApiRequest } from '../../api/useApiRequest'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { usePendingFocus } from '../../components/usePendingFocus'
import { formatDateTime } from '../../courses/dates'
import { Pager } from '../../support/Pager'
import { border, fieldClasses } from '../../teaching/formStyles'
import type { HostelAllocationStatus } from '../../types/hostel'
import { allocationStatusLabel, plainTagClasses, SIGN_IN_AGAIN } from '../hostelText'
import { VacateButton } from './VacateButton'

const PAGE_SIZE = 20
const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

function idParam(value: string | null): number | undefined {
  return value !== null && /^[1-9]\d{0,17}$/.test(value) ? Number(value) : undefined
}

/** Every allocation, newest first, with Vacate for active ones. History is kept; nothing is deleted. */
export function AllocationsSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (['ACTIVE', 'VACATED'] as const).find((value) => value === searchParams.get('status')) as HostelAllocationStatus | undefined
  const hostelId = idParam(searchParams.get('hostel'))
  const academicSessionId = idParam(searchParams.get('session'))
  const pageParam = searchParams.get('page')
  const page = pageParam && /^\d+$/.test(pageParam) ? Number(pageParam) : 0
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)

  const load = useMemo(
    () => (signal: AbortSignal) => getHostelAllocations({ status, hostelId, academicSessionId, page, size: PAGE_SIZE }, signal),
    [status, hostelId, academicSessionId, page],
  )
  const request = useApiRequest(load)
  const hostels = useApiRequest(getHostels)
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

  let content
  if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading hostel allocations…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to hostel management." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (request.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load hostel allocations." message="Check your connection and try again." onRetry={request.retry} /></StatePanel>
  } else if (request.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No hostel allocations found." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="Hostel allocations" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {request.data.items.map((allocation) => (
            <li key={allocation.allocationId} className="px-4 py-3 sm:px-5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-semibold break-words text-ink">{allocation.studentLoginId}</span>
                <span className={plainTagClasses(allocation.status === 'VACATED')}>{allocationStatusLabel(allocation.status)}</span>
              </div>
              <p className="mt-0.5 text-sm break-words text-ink">
                {allocation.hostelName}, Room {allocation.roomNumber}, Bed {allocation.bedNumber} · {allocation.academicSessionCode} ·{' '}
                <span className="font-mono text-xs">{allocation.applicationNumber}</span>
              </p>
              <p className="text-xs text-ink-muted">
                Allocated <time dateTime={allocation.allocatedAt}>{formatDateTime(allocation.allocatedAt)}</time>
                {allocation.vacatedAt && <> · Vacated <time dateTime={allocation.vacatedAt}>{formatDateTime(allocation.vacatedAt)}</time></>}
              </p>
              {allocation.status === 'ACTIVE' && (
                <div className="mt-2">
                  <VacateButton
                    allocationId={allocation.allocationId}
                    label={`${allocation.studentLoginId}, ${allocation.roomNumber} ${allocation.bedNumber}`}
                    onVacated={() => request.refresh()}
                  />
                </div>
              )}
            </li>
          ))}
        </ul>
        <Pager
          page={request.data.page}
          totalPages={request.data.totalPages}
          label="Allocation pages"
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
      <div role="group" aria-label="Filter allocations" className="grid gap-3 rounded-panel border border-line bg-surface p-4 sm:grid-cols-3">
        <div>
          <label htmlFor="allocation-filter-status" className="block text-sm font-medium text-ink">Status</label>
          <select id="allocation-filter-status" value={status ?? ''} onChange={(event) => update({ status: event.target.value })} className={selectClasses}>
            <option value="">Active and vacated</option>
            <option value="ACTIVE">Active</option>
            <option value="VACATED">Vacated</option>
          </select>
        </div>
        <div>
          <label htmlFor="allocation-filter-hostel" className="block text-sm font-medium text-ink">Hostel</label>
          <select id="allocation-filter-hostel" value={hostelId ?? ''} onChange={(event) => update({ hostel: event.target.value })} className={selectClasses}>
            <option value="">All hostels</option>
            {hostels.status === 'loaded' && hostels.data.map((hostel) => <option key={hostel.hostelId} value={hostel.hostelId}>{hostel.name}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="allocation-filter-session" className="block text-sm font-medium text-ink">Academic Session</label>
          <select id="allocation-filter-session" value={academicSessionId ?? ''} onChange={(event) => update({ session: event.target.value })} className={selectClasses}>
            <option value="">All sessions</option>
            {sessions.status === 'loaded' && sessions.data.map((session) => <option key={session.id} value={session.id}>{session.code}</option>)}
          </select>
        </div>
      </div>
      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </>
  )
}
