import { Link } from 'react-router'
import { getAdminDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { actionLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import type { AdminDashboard as Dashboard } from '../types/dashboard'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'

type Level = 2 | 3

const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

/** Label and value rows, read in that order. A zero is shown as 0. */
function Counts({ rows }: { rows: [string, number][] }) {
  return (
    <dl className="divide-y divide-line">
      {rows.map(([label, value]) => (
        <div key={label} className="flex items-baseline justify-between gap-4 py-2 first:pt-0">
          <dt className="text-sm text-ink-muted">{label}</dt>
          <dd className="text-base font-semibold text-ink tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Actions({ links }: { links: { to: string; label: string }[] }) {
  return (
    <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
      {links.map((link) => (
        <Link key={link.to} to={link.to} className={actionLinkClasses}>
          {link.label}
        </Link>
      ))}
    </div>
  )
}

function AcademicMasters({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  const masters = dashboard.academicMasters
  return (
    <DashboardSection level={level} id="admin-masters" title="Academic Masters">
      <Counts
        rows={[
          ['Departments', masters.departmentCount],
          ['Programs', masters.programCount],
          ['Academic Sessions', masters.academicSessionCount],
          ['Semesters', masters.semesterCount],
          ['Courses', masters.courseCount],
        ]}
      />
      <Actions links={[{ to: '/admin/academic', label: 'Manage academic data' }]} />
    </DashboardSection>
  )
}

function AcademicOperations({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  const operations = dashboard.academicOperations
  return (
    <DashboardSection level={level} id="admin-operations" title="Academic Operations">
      <Counts
        rows={[
          ['Course Offerings', operations.courseOfferingCount],
          ['Active Semester Enrollments', operations.activeSemesterEnrollmentCount],
          ['Active Course Enrollments', operations.activeCourseEnrollmentCount],
        ]}
      />
      <p className="mt-2 text-xs leading-5 text-ink-muted">
        Active means currently enrolled; completed and withdrawn enrollments are not counted.
      </p>
      <Actions
        links={[
          { to: '/admin/academic/offerings', label: 'Manage course offerings' },
          { to: '/admin/academic/enrollments', label: 'Manage enrollments' },
        ]}
      />
    </DashboardSection>
  )
}

function CourseOfferings({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection
      level={level}
      id="admin-offerings"
      title="Course Offerings"
      link={{ to: '/admin/academic/offerings', label: 'View all' }}
    >
      {dashboard.courseOfferings.length === 0 ? (
        <SectionEmpty>No course offerings available.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.courseOfferings.map((offering) => (
            <li key={offering.courseOfferingId} className="flex flex-col gap-2 py-2.5 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm break-words text-ink">
                  <span className="font-semibold">{offering.courseCode}</span>
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {offering.courseName}
                </p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {offering.programCode}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  Semester {offering.semesterNumber}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {offering.academicSessionCode}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {creditsText(offering.credits)}
                </p>
              </div>
              <Link to={`/admin/academic/offerings/${offering.courseOfferingId}/enrollments`} className={actionLinkClasses}>
                View roster
                <span className="sr-only">
                  {' '}
                  of {offering.courseCode} in semester {offering.semesterNumber}, {offering.academicSessionCode}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.courseOfferings.length} total={dashboard.academicOperations.courseOfferingCount} />
    </DashboardSection>
  )
}

/**
 * The admin dashboard: the counts and offering preview GET /api/dashboard/admin sends, with links to the existing
 * Academic Admin pages. Nothing is filtered, calculated, or invented here.
 */
export function AdminDashboard({ level = 2 }: { level?: Level }) {
  const dashboard = useApiRequest(getAdminDashboard)

  if (dashboard.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading your dashboard…" />
      </StatePanel>
    )
  }
  if (dashboard.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to the Admin Dashboard." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  }
  if (dashboard.status === 'failed' || dashboard.status === 'notFound') {
    return (
      <StatePanel>
        <ErrorState title="Couldn’t load your dashboard." message="Check your connection and try again." onRetry={dashboard.retry} />
      </StatePanel>
    )
  }

  const data = dashboard.data
  return (
    <div className="space-y-4">
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <AcademicMasters dashboard={data} level={level} />
        <AcademicOperations dashboard={data} level={level} />
      </div>
      <CourseOfferings dashboard={data} level={level} />
    </div>
  )
}
