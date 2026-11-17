import { Link } from 'react-router'
import { getAdminDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import type { AdminDashboard as Dashboard } from '../types/dashboard'
import { DataTable, textLinkClasses } from '../ui'
import { buttonClasses } from '../ui/styles'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'

type Level = 2 | 3

const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

function Counts({ rows }: { rows: [string, number][] }) {
  return (
    <dl className="grid grid-cols-2 gap-y-4 sm:flex sm:flex-wrap">
      {rows.map(([label, value]) => (
        <div key={label} className="min-w-28 flex-1 border-line px-4 text-center sm:not-first:border-l">
          <dt className="text-[0.95rem] text-ink">{label}</dt>
          <dd className="mt-1 text-4xl font-bold text-ink tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Actions({ links }: { links: { to: string; label: string }[] }) {
  return (
    <ul className="mt-5 flex flex-wrap items-center gap-y-2">
      {links.map((link) => (
        <li key={link.to} className="flex items-center not-first:before:mx-4 not-first:before:h-5 not-first:before:w-px not-first:before:bg-line-strong not-first:before:content-['']">
          <Link key={link.to} to={link.to} className={`text-[0.95rem] ${textLinkClasses}`}>
            {link.label}
          </Link>
        </li>
      ))}
    </ul>
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
      <Actions
        links={[
          { to: '/admin/academic/departments', label: 'Departments' },
          { to: '/admin/academic/programs', label: 'Programs' },
          { to: '/admin/academic/courses', label: 'Courses' },
          { to: '/admin/academic/offerings', label: 'Course offerings' },
          { to: '/admin/academic/faculty-assignments', label: 'Faculty assignments' },
        ]}
      />
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
        <DataTable<Dashboard['courseOfferings'][number]>
          caption="Course offerings"
          rows={dashboard.courseOfferings}
          rowKey={(offering) => offering.courseOfferingId}
          columns={[
            { key: 'code', header: 'Course code', primary: true, cell: (offering) => offering.courseCode },
            { key: 'name', header: 'Course name', cell: (offering) => offering.courseName },
            { key: 'program', header: 'Program', cell: (offering) => offering.programCode },
            { key: 'semester', header: 'Semester', cell: (offering) => `Semester ${offering.semesterNumber}` },
            { key: 'session', header: 'Academic session', cell: (offering) => offering.academicSessionCode },
            { key: 'credits', header: 'Credits', cell: (offering) => creditsText(offering.credits) },
            {
              key: 'roster',
              header: 'Action',
              cell: (offering) => (
                <Link to={`/admin/academic/offerings/${offering.courseOfferingId}/enrollments`} className={buttonClasses('primary', 'sm')}>
                  View roster
                  <span className="sr-only">
                    {' '}
                    of {offering.courseCode} in semester {offering.semesterNumber}, {offering.academicSessionCode}
                  </span>
                </Link>
              ),
            },
          ]}
        />
      )}
      <PreviewNote shown={dashboard.courseOfferings.length} total={dashboard.academicOperations.courseOfferingCount} />
    </DashboardSection>
  )
}

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
      <AcademicMasters dashboard={data} level={level} />
      <AcademicOperations dashboard={data} level={level} />
      <CourseOfferings dashboard={data} level={level} />
    </div>
  )
}
