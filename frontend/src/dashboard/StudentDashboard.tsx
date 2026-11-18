import { Link } from 'react-router'
import { getStudentDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { formatPercentage } from '../attendance/percentage'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { formatMarks } from '../courses/marks'
import type { StudentDashboard as Dashboard } from '../types/dashboard'
import { courseColor } from './courseColors'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'
import { PlannerList } from './PlannerList'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { StudentStrip } from '../erp/StudentStrip'
import { ErpPreview, QuickLinks } from './StudentErpPreview'

function Panel({ title, tag, icon, children }: { title: string; tag: string; icon: NavIconName; children: React.ReactNode }) {
  return (
    <div className="rounded-panel border border-line bg-surface shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="relative mx-5 flex flex-wrap items-center justify-between gap-3 border-b border-line pt-5 pb-3">
        <span aria-hidden="true" className="absolute -bottom-px left-0 h-0.5 w-16 bg-portal-cta" />
        <p className="flex items-center gap-3 text-2xl font-bold text-ink"><NavIcon name={icon} className="size-7" />{title}</p>
        <span className="rounded-md bg-portal-bar-strong/60 px-2.5 py-1 text-xs text-portal-navy">{tag}</span>
      </div>
      <div className="space-y-4 p-4">{children}</div>
    </div>
  )
}

const itemLinkClasses =
  'font-medium break-words text-ink underline-offset-4 hover:text-stacc-primary hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

function Courses({ dashboard, level }: { dashboard: Dashboard; level: 2 | 3 }) {
  return (
    <DashboardSection level={level} id="dashboard-courses" title="My Courses" link={{ to: '/courses', label: 'View all' }}>
      {dashboard.courses.length === 0 ? (
        <SectionEmpty>No LMS courses available.</SectionEmpty>
      ) : (
        <ul className="space-y-2.5">
          {dashboard.courses.map((course) => (
            <li key={course.lmsCourseId} className="flex min-w-0 items-center gap-3 rounded-panel border border-line border-l-[6px] bg-surface px-4 py-2.5"
              style={{ borderLeftColor: courseColor(course.lmsCourseId) }}>
              <div className="min-w-0 flex-1">
                <Link to={`/courses/${course.lmsCourseId}`} className={`text-[0.95rem] font-semibold ${itemLinkClasses}`}>
                  {course.courseCode}: {course.courseName}
                </Link>
                <p className="text-sm break-words text-ink-muted">
                  {course.programCode} · Semester {course.semesterNumber} · {course.academicSessionCode}
                </p>
              </div>
              <span aria-hidden="true" className="text-ink-muted">›</span>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.courses.length} total={dashboard.courseCount} />
    </DashboardSection>
  )
}

function UpcomingAssignments({ dashboard, level }: { dashboard: Dashboard; level: 2 | 3 }) {
  return (
    <DashboardSection level={level} id="dashboard-assignments" title="Upcoming Assignments">
      {dashboard.upcomingAssignments.length === 0 ? (
        <SectionEmpty>No upcoming assignments.</SectionEmpty>
      ) : (
        <PlannerList
          headingLevel={level === 2 ? 3 : 4}
          items={dashboard.upcomingAssignments.map((assignment) => ({
            key: String(assignment.assignmentId),
            colorId: assignment.lmsCourseId,
            courseCode: assignment.courseCode,
            courseName: assignment.courseName,
            title: assignment.title,
            to: `/courses/${assignment.lmsCourseId}/assignments/${assignment.assignmentId}`,
            dueAt: assignment.dueAt,
            maxMarks: assignment.maxMarks,
          }))}
        />
      )}
      <PreviewNote shown={dashboard.upcomingAssignments.length} total={dashboard.upcomingAssignmentCount} />
    </DashboardSection>
  )
}

function RecentAnnouncements({ dashboard, level }: { dashboard: Dashboard; level: 2 | 3 }) {
  return (
    <DashboardSection level={level} id="dashboard-announcements" title="Recent Announcements">
      {dashboard.recentAnnouncements.length === 0 ? (
        <SectionEmpty>No recent announcements.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.recentAnnouncements.map((announcement) => (
            <li key={announcement.announcementId} className="py-2.5 first:pt-0 last:pb-0">
              <Link to={`/courses/${announcement.lmsCourseId}`} className={`text-sm ${itemLinkClasses}`}>
                {announcement.title}
                <span className="sr-only">, in {announcement.courseName}</span>
              </Link>
              <p className="text-xs leading-5 break-words text-ink-muted">
                {announcement.courseCode}
                <span aria-hidden="true"> · </span>
                <span className="sr-only">, </span>
                Published {formatDateTime(announcement.publishedAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </DashboardSection>
  )
}

function Attendance({ dashboard, level }: { dashboard: Dashboard; level: 2 | 3 }) {
  return (
    <DashboardSection level={level} id="dashboard-attendance" title="Attendance" link={{ to: '/erp/attendance', label: 'View attendance' }}>
      {dashboard.attendance.length === 0 ? (
        <SectionEmpty>No attendance recorded yet.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.attendance.map((course) => (
            <li key={course.courseOfferingId} className="flex items-start justify-between gap-3 py-2.5 first:pt-0 last:pb-0">
              <div className="min-w-0">
                <p className="text-sm font-medium break-words text-ink">{course.courseName}</p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {course.courseCode}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {course.presentCount} present · {course.absentCount} absent of {course.totalSessions}{' '}
                  {course.totalSessions === 1 ? 'session' : 'sessions'}
                </p>
              </div>
              <p className="shrink-0 text-sm font-semibold text-ink">
                {course.attendancePercentage === null ? 'No attendance recorded yet.' : formatPercentage(course.attendancePercentage)}
              </p>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.attendance.length} total={dashboard.attendanceCourseCount} />
    </DashboardSection>
  )
}

function RecentResults({ dashboard, level }: { dashboard: Dashboard; level: 2 | 3 }) {
  return (
    <DashboardSection level={level} id="dashboard-results" title="Recent Results" link={{ to: '/erp/exams/results', label: 'View results' }}>
      {dashboard.recentResults.length === 0 ? (
        <SectionEmpty>No published results available yet.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.recentResults.map((result) => (
            <li
              key={`${result.academicSessionCode}-${result.programCode}-${result.semesterNumber}-${result.courseCode}`}
              className="flex items-start justify-between gap-3 py-2.5 first:pt-0 last:pb-0"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium break-words text-ink">{result.courseName}</p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {result.courseCode}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {result.academicSessionCode}, Semester {result.semesterNumber}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-sm font-semibold text-ink">
                  <span className="sr-only">Marks </span>
                  {formatMarks(result.marksObtained)} / {formatMarks(result.maxMarks)}
                </p>
                {result.gradeCode && <p className="text-xs text-ink-muted">Grade {result.gradeCode}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.recentResults.length} total={dashboard.resultCount} />
    </DashboardSection>
  )
}

export function StudentDashboard({ level = 2 }: { level?: 2 | 3 }) {
  const dashboard = useApiRequest(getStudentDashboard)

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
        <AccessDeniedState title="You don’t have access to the Student Dashboard." message={SIGN_IN_AGAIN} />
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
    <div className="space-y-6">
      <StudentStrip />
      <dl className="flex flex-wrap gap-x-8 gap-y-2">
        <div className="flex items-baseline gap-3">
          <dt className="text-ink-muted">My Courses</dt>
          <dd className="text-2xl font-semibold text-ink">{data.courseCount}</dd>
        </div>
        <div className="flex items-baseline gap-3">
          <dt className="text-ink-muted">Upcoming Assignments</dt>
          <dd className="text-2xl font-semibold text-ink">{data.upcomingAssignmentCount}</dd>
        </div>
      </dl>
      <div className="grid items-start gap-6 xl:grid-cols-2">
        <Panel title="Learning · LMS" tag="Courses, assignments, announcements" icon="courses">
          <Courses dashboard={data} level={level} />
          <UpcomingAssignments dashboard={data} level={level} />
          <RecentAnnouncements dashboard={data} level={level} />
        </Panel>
        <Panel title="Academic Records · ERP" tag="Attendance, results, fees, exams" icon="academics">
          <Attendance dashboard={data} level={level} />
          <RecentResults dashboard={data} level={level} />
          <ErpPreview level={level} />
        </Panel>
      </div>
      <QuickLinks />
    </div>
  )
}
