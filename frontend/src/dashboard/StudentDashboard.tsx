import { Link } from 'react-router'
import { getStudentDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { formatPercentage } from '../attendance/percentage'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { formatMarks, marksText } from '../courses/marks'
import type { StudentDashboard as Dashboard } from '../types/dashboard'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'

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
        <ul className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
          {dashboard.courses.map((course) => (
            <li key={course.lmsCourseId} className="min-w-0">
              <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
              {/* The course route uses the LMS course ID only. */}
              <Link to={`/courses/${course.lmsCourseId}`} className={`text-sm ${itemLinkClasses}`}>
                {course.courseName}
              </Link>
              <p className="text-xs leading-5 break-words text-ink-muted">
                {course.programCode}
                <span aria-hidden="true"> · </span>
                <span className="sr-only">, </span>
                Semester {course.semesterNumber}
                <span aria-hidden="true"> · </span>
                <span className="sr-only">, </span>
                {course.academicSessionCode}
              </p>
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
        <ul className="divide-y divide-line">
          {dashboard.upcomingAssignments.map((assignment) => (
            <li key={assignment.assignmentId} className="py-2.5 first:pt-0 last:pb-0">
              <Link
                to={`/courses/${assignment.lmsCourseId}/assignments/${assignment.assignmentId}`}
                className={`text-sm ${itemLinkClasses}`}
              >
                {assignment.title}
                <span className="sr-only">, {assignment.courseName}</span>
              </Link>
              <p className="text-xs leading-5 break-words text-ink-muted">
                {assignment.courseCode}
                <span aria-hidden="true"> · </span>
                <span className="sr-only">, </span>
                Due {formatDateTime(assignment.dueAt)}
                <span aria-hidden="true"> · </span>
                <span className="sr-only">, </span>
                {marksText(assignment.maxMarks)}
              </p>
            </li>
          ))}
        </ul>
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
              {/* The full announcement is on the course page. */}
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
    <DashboardSection level={level} id="dashboard-attendance" title="Attendance" link={{ to: '/academic/attendance', label: 'View attendance' }}>
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
              {/* The backend's own percentage, never recalculated here, and never shown for nothing recorded. */}
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
    <DashboardSection level={level} id="dashboard-results" title="Recent Results" link={{ to: '/academic/results', label: 'View results' }}>
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
              {/* Exactly what was published: no percentage, grade point, rank, or pass or fail. */}
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

/**
 * The student dashboard: what GET /api/dashboard/student sends, as short previews that link to the full pages.
 * The backend already decided what is visible; nothing is filtered, calculated, or invented here.
 */
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
    <div className="space-y-4">
      {/* The two real counts, kept small; the sections below show their previews. */}
      <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
        <div>
          <dt className="text-ink-muted">My Courses</dt>
          <dd className="text-xl font-semibold text-ink">{data.courseCount}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Upcoming Assignments</dt>
          <dd className="text-xl font-semibold text-ink">{data.upcomingAssignmentCount}</dd>
        </div>
      </dl>
      <Courses dashboard={data} level={level} />
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <UpcomingAssignments dashboard={data} level={level} />
        <RecentAnnouncements dashboard={data} level={level} />
        <Attendance dashboard={data} level={level} />
        <RecentResults dashboard={data} level={level} />
      </div>
    </div>
  )
}
