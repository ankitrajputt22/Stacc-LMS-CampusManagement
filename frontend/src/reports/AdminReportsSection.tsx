import { useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getAcademicSessions, getPrograms, getSemesters } from '../api/adminAcademicApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getAdminEnrollmentCsvExport, getAdminEnrollmentReport, getAdminReportOverview } from '../api/reportsApi'
import { useApiRequest } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { saveBlob } from '../courses/materials'
import { Pager } from '../support/Pager'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AdminReportOverview, ReportFilterQuery } from '../types/reports'
import { formatCount } from './reportFormat'
import { ReportSection, ReportStats } from './ReportStats'

const PAGE_SIZE = 20
const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'
const selectClasses = `h-11 sm:h-9 ${fieldClasses} ${border(false)}`

const idParam = (value: string | null) => (value && /^[1-9]\d{0,17}$/.test(value) ? Number(value) : undefined)

/** Session, program, and semester, filled from the admin academic API. They change the address only when applied. */
function Filters({ applied, onApply }: { applied: ReportFilterQuery; onApply: (next: ReportFilterQuery) => void }) {
  const sessions = useApiRequest(getAcademicSessions)
  const programs = useApiRequest(getPrograms)
  const semesters = useApiRequest(getSemesters)
  const [session, setSession] = useState(applied.academicSessionId ? String(applied.academicSessionId) : '')
  const [program, setProgram] = useState(applied.programId ? String(applied.programId) : '')
  const [semester, setSemester] = useState(applied.semesterId ? String(applied.semesterId) : '')

  // Only semesters of the chosen program and session are offered, so a combination never contradicts itself.
  const semesterOptions = semesters.status === 'loaded'
    ? semesters.data.filter((item) => (program === '' || String(item.programId) === program)
        && (session === '' || String(item.academicSessionId) === session))
    : []
  const keepSemester = (nextSession: string, nextProgram: string) => {
    if (semesters.status !== 'loaded' || semester === '') return
    const chosen = semesters.data.find((item) => String(item.id) === semester)
    if (!chosen || (nextProgram !== '' && String(chosen.programId) !== nextProgram)
        || (nextSession !== '' && String(chosen.academicSessionId) !== nextSession)) {
      setSemester('')
    }
  }

  return (
    <form
      aria-label="Report filters"
      onSubmit={(event) => {
        event.preventDefault()
        onApply({ academicSessionId: idParam(session), programId: idParam(program), semesterId: idParam(semester) })
      }}
      className="grid gap-3 rounded-panel border border-line bg-surface p-4 sm:grid-cols-3"
    >
      <div>
        <label htmlFor="report-filter-session" className="block text-sm font-medium text-ink">Academic Session</label>
        <select id="report-filter-session" value={session} onChange={(event) => { setSession(event.target.value); keepSemester(event.target.value, program) }} className={selectClasses}>
          <option value="">All sessions</option>
          {sessions.status === 'loaded' && sessions.data.map((item) => <option key={item.id} value={item.id}>{item.code}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="report-filter-program" className="block text-sm font-medium text-ink">Program</label>
        <select id="report-filter-program" value={program} onChange={(event) => { setProgram(event.target.value); keepSemester(session, event.target.value) }} className={selectClasses}>
          <option value="">All programs</option>
          {programs.status === 'loaded' && programs.data.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="report-filter-semester" className="block text-sm font-medium text-ink">Semester</label>
        <select id="report-filter-semester" value={semester} onChange={(event) => setSemester(event.target.value)} className={selectClasses}>
          <option value="">All semesters</option>
          {semesterOptions.map((item) => (
            <option key={item.id} value={item.id}>{item.programCode} · Semester {item.semesterNumber} · {item.academicSessionCode}</option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-2 sm:col-span-3 sm:flex-row">
        <Button type="submit">Apply filters</Button>
        <ActionButton
          onClick={() => {
            setSession('')
            setProgram('')
            setSemester('')
            onApply({})
          }}
        >
          Clear filters
        </ActionButton>
      </div>
    </form>
  )
}

function Overview({ overview }: { overview: AdminReportOverview }) {
  const { academic, enrollment, operations } = overview
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <ReportSection id="admin-report-academic" title="Academic overview" note="Departments, programs, and courses are college-wide; course offerings follow the filters.">
        <ReportStats label="Academic overview" items={[
          { label: 'Departments', value: academic.departmentCount },
          { label: 'Programs', value: academic.programCount },
          { label: 'Courses', value: academic.courseCount },
          { label: 'Course Offerings', value: academic.courseOfferingCount },
        ]} />
      </ReportSection>
      <ReportSection id="admin-report-enrollment" title="Enrollment summary" note="By exact status, within the filters.">
        <h4 className="text-xs font-semibold text-ink">Semester Enrollments</h4>
        <div className="mt-1">
          <ReportStats label="Semester enrollments" items={[
            { label: 'Enrolled', value: enrollment.semesterEnrollments.enrolledCount },
            { label: 'Completed', value: enrollment.semesterEnrollments.completedCount },
            { label: 'Withdrawn', value: enrollment.semesterEnrollments.withdrawnCount },
          ]} />
        </div>
        <h4 className="mt-4 text-xs font-semibold text-ink">Course Enrollments</h4>
        <div className="mt-1">
          <ReportStats label="Course enrollments" items={[
            { label: 'Enrolled', value: enrollment.courseEnrollments.enrolledCount },
            { label: 'Completed', value: enrollment.courseEnrollments.completedCount },
            { label: 'Withdrawn', value: enrollment.courseEnrollments.withdrawnCount },
          ]} />
        </div>
      </ReportSection>
      {operations.helpdesk && (
        <ReportSection id="admin-report-helpdesk" title="Helpdesk" note="All tickets by status, college-wide.">
          <ReportStats label="Helpdesk tickets" items={[
            { label: 'Open', value: operations.helpdesk.openTicketCount },
            { label: 'In Progress', value: operations.helpdesk.inProgressTicketCount },
            { label: 'Resolved', value: operations.helpdesk.resolvedTicketCount },
            { label: 'Closed', value: operations.helpdesk.closedTicketCount },
          ]} />
        </ReportSection>
      )}
      {operations.hostel && (
        <ReportSection id="admin-report-hostel" title="Hostel" note="Allocations by status, college-wide.">
          <ReportStats label="Hostel allocations" items={[
            { label: 'Active allocations', value: operations.hostel.activeAllocationCount },
            { label: 'Vacated allocations', value: operations.hostel.vacatedAllocationCount },
          ]} />
        </ReportSection>
      )}
      {operations.campusServices && (
        <ReportSection id="admin-report-campus" title="Campus Services" note="Service requests by the status they show, college-wide.">
          <ReportStats label="Campus service requests" items={[
            { label: 'Submitted', value: operations.campusServices.submittedCount },
            { label: 'Approved', value: operations.campusServices.approvedCount },
            { label: 'Rejected', value: operations.campusServices.rejectedCount },
            { label: 'Cancelled', value: operations.campusServices.cancelledCount },
            { label: 'Completed', value: operations.campusServices.completedCount },
          ]} />
        </ReportSection>
      )}
    </div>
  )
}

/** One row per course offering: a table on wide screens, labelled records on narrow ones. */
function EnrollmentReport({ filters, page, onPage }: { filters: ReportFilterQuery; page: number; onPage: (page: number) => void }) {
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const load = useMemo(() => (signal: AbortSignal) => getAdminEnrollmentReport(filters, page, PAGE_SIZE, signal), [filters, page])
  const report = useApiRequest(load)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  async function exportCsv() {
    if (exporting) return
    setExporting(true)
    setExportError(null)
    try {
      saveBlob(await getAdminEnrollmentCsvExport(filters), 'stacc-enrollment-report.csv')
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setExportError(status === 400 ? (getApiError(failure)?.message ?? 'Couldn’t export the report.') : 'Couldn’t export the report. Try again.')
      }
    } finally {
      setExporting(false)
    }
  }

  let content
  if (report.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading enrollment report…" /></StatePanel>
  } else if (report.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to admin reports." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (report.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load the enrollment report." message="Check your connection and the filters, and try again." onRetry={report.retry} /></StatePanel>
  } else if (report.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No course offerings match these filters." /></StatePanel>
  } else {
    const rows = report.data.items
    content = (
      <>
        <div className="hidden overflow-hidden rounded-panel border border-line bg-surface md:block">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Course enrollments by course offering</caption>
            <thead className="border-b border-line bg-surface-muted text-xs text-ink-muted">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">Course</th>
                <th scope="col" className="px-3 py-2 font-medium">Program</th>
                <th scope="col" className="px-3 py-2 font-medium">Semester</th>
                <th scope="col" className="px-3 py-2 font-medium">Session</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Enrolled</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">Completed</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Withdrawn</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map((row) => (
                <tr key={row.courseOfferingId}>
                  <th scope="row" className="px-4 py-2 font-normal text-ink"><span className="font-mono">{row.courseCode}</span> {row.courseName}</th>
                  <td className="px-3 py-2 text-ink">{row.programCode}</td>
                  <td className="px-3 py-2 text-ink">{row.semesterNumber}</td>
                  <td className="px-3 py-2 text-ink">{row.academicSessionCode}</td>
                  <td className="px-3 py-2 text-right text-ink tabular-nums">{formatCount(row.enrolledCount)}</td>
                  <td className="px-3 py-2 text-right text-ink tabular-nums">{formatCount(row.completedCount)}</td>
                  <td className="px-4 py-2 text-right text-ink tabular-nums">{formatCount(row.withdrawnCount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <ul aria-label="Course enrollments by course offering" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface md:hidden">
          {rows.map((row) => (
            <li key={row.courseOfferingId} className="px-4 py-3">
              <p className="text-sm font-semibold break-words text-ink"><span className="font-mono">{row.courseCode}</span> {row.courseName}</p>
              <p className="text-xs text-ink-muted">{row.programCode} · Semester {row.semesterNumber} · {row.academicSessionCode}</p>
              <dl className="mt-2 grid grid-cols-3 gap-2 text-sm">
                <div><dt className="text-xs text-ink-muted">Enrolled</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(row.enrolledCount)}</dd></div>
                <div><dt className="text-xs text-ink-muted">Completed</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(row.completedCount)}</dd></div>
                <div><dt className="text-xs text-ink-muted">Withdrawn</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(row.withdrawnCount)}</dd></div>
              </dl>
            </li>
          ))}
        </ul>
        <Pager
          page={report.data.page}
          totalPages={report.data.totalPages}
          label="Enrollment report pages"
          onPage={(next, key) => {
            onPage(next)
            focusLater(key)
          }}
        />
      </>
    )
  }

  return (
    <section aria-labelledby="admin-enrollment-report-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="admin-enrollment-report-heading" className="text-base font-semibold text-ink">
          Course Enrollment Report
          {report.status === 'loaded' && (
            <span className="text-sm font-normal text-ink-muted">
              {' '}({formatCount(report.data.totalElements)} {report.data.totalElements === 1 ? 'offering' : 'offerings'})
            </span>
          )}
        </h3>
        <ActionButton aria-disabled={exporting || undefined} onClick={exportCsv}>
          {exporting ? 'Exporting…' : 'Export CSV'}<span className="sr-only"> of the course enrollment report with these filters</span>
        </ActionButton>
      </div>
      <div role="alert" className="empty:hidden">
        {exportError && <p className="mt-2 rounded-control border border-danger/40 bg-danger/5 px-3 py-2 text-sm text-danger">{exportError}</p>}
      </div>
      <div ref={contentRef} className="mt-3">{content}</div>
    </section>
  )
}

/**
 * Admin reports: filters in the address (session, program, semester, page), the overview, operational sections the
 * backend returned, and the per-offering enrollment report with its CSV export.
 */
export function AdminReportsSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const sessionParam = searchParams.get('session')
  const programParam = searchParams.get('program')
  const semesterParam = searchParams.get('semester')
  const pageParam = searchParams.get('page')
  const page = pageParam && /^\d{1,6}$/.test(pageParam) ? Number(pageParam) : 0
  const filters = useMemo<ReportFilterQuery>(() => {
    const query: ReportFilterQuery = {}
    const session = idParam(sessionParam)
    const program = idParam(programParam)
    const semester = idParam(semesterParam)
    if (session) query.academicSessionId = session
    if (program) query.programId = program
    if (semester) query.semesterId = semester
    return query
  }, [sessionParam, programParam, semesterParam])

  const load = useMemo(() => (signal: AbortSignal) => getAdminReportOverview(filters, signal), [filters])
  const overview = useApiRequest(load)

  function apply(next: ReportFilterQuery, nextPage = 0) {
    const params = new URLSearchParams(searchParams)
    for (const [key, value] of [['session', next.academicSessionId], ['program', next.programId], ['semester', next.semesterId]] as const) {
      if (value) params.set(key, String(value))
      else params.delete(key)
    }
    if (nextPage > 0) params.set('page', String(nextPage))
    else params.delete('page')
    setSearchParams(params)
  }

  let content
  if (overview.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading reports…" /></StatePanel>
  } else if (overview.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to admin reports." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (overview.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load reports." message="Check your connection and the filters, and try again." onRetry={overview.retry} /></StatePanel>
  } else {
    content = <Overview overview={overview.data} />
  }

  return (
    <section aria-labelledby="admin-reports-heading">
      <h2 id="admin-reports-heading" className="text-base font-semibold text-ink">
        Academic and Operational Reports
      </h2>
      <div className="mt-3">
        {/* Keyed by the address, so Back and Forward show the filters they lead to. */}
        <Filters key={`${sessionParam}|${programParam}|${semesterParam}`} applied={filters} onApply={(next) => apply(next)} />
      </div>
      <div className="mt-4">{content}</div>
      {overview.status !== 'denied' && (
        <div className="mt-6">
          <EnrollmentReport filters={filters} page={page} onPage={(next) => apply(filters, next)} />
        </div>
      )}
    </section>
  )
}
