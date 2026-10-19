import { Link } from 'react-router'
import { getFacultyDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { actionLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { TimingTag } from '../courses/AttemptParts'
import { formatDateTime } from '../courses/dates'
import { marksText } from '../courses/marks'
import { lmsStatusWord } from '../teaching/lmsCourseText'
import type { FacultyDashboard as Dashboard, FacultyDashboardCourse, FacultyDashboardOpenSession } from '../types/dashboard'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'

type Level = 2 | 3

const itemLinkClasses =
  'font-medium break-words text-ink underline-offset-4 hover:text-stacc-primary hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'
const tagClasses = 'inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink'
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

/** Where a teaching course leads: its LMS course when faculty can open it, otherwise the Teaching page. */
function CourseAction({ course }: { course: FacultyDashboardCourse }) {
  const name = <span className="sr-only"> {course.courseCode}</span>
  if (course.lmsCourseId !== null && (course.lmsCourseStatus === 'DRAFT' || course.lmsCourseStatus === 'PUBLISHED')) {
    return (
      <Link to={`/teaching/${course.lmsCourseId}`} className={actionLinkClasses}>
        Open LMS course{name}
      </Link>
    )
  }
  // No LMS course yet: Teaching is where one is created. Archived: Teaching shows it, and nothing opens it.
  return (
    <Link to="/teaching" className={actionLinkClasses}>
      {course.lmsCourseStatus === null ? 'Manage teaching' : 'View teaching'}
      {name}
    </Link>
  )
}

function TeachingCourses({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection level={level} id="faculty-courses" title="Teaching Courses" link={{ to: '/teaching', label: 'View teaching' }}>
      {dashboard.teachingCourses.length === 0 ? (
        <SectionEmpty>No teaching courses available.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.teachingCourses.map((course) => (
            <li key={course.courseOfferingId} className="flex flex-col gap-2 py-2.5 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm break-words text-ink">
                  <span className="font-semibold">{course.courseCode}</span>
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {course.courseName}
                </p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {course.programCode}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  Semester {course.semesterNumber}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {course.academicSessionCode}
                </p>
                <p className="mt-1 flex items-center gap-2 text-xs text-ink-muted">
                  LMS course <span className={tagClasses}>{lmsStatusWord(course.lmsCourseStatus)}</span>
                </p>
              </div>
              <CourseAction course={course} />
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.teachingCourses.length} total={dashboard.teachingCourseCount} />
    </DashboardSection>
  )
}

function UpcomingAssignments({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection level={level} id="faculty-assignments" title="Upcoming Assignments" link={{ to: '/assignments', label: 'View assignments' }}>
      {dashboard.upcomingAssignments.length === 0 ? (
        <SectionEmpty>No upcoming assignments.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.upcomingAssignments.map((assignment) => (
            <li key={assignment.assignmentId} className="py-2.5 first:pt-0 last:pb-0">
              {/* The course page's Assignments section is where assignments are managed. */}
              <Link to={`/teaching/${assignment.lmsCourseId}#assignments`} className={`text-sm ${itemLinkClasses}`}>
                {assignment.title}
                <span className="sr-only">, {assignment.courseName}</span>
              </Link>
              <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs leading-5 text-ink-muted">
                <span>{assignment.courseCode}</span>
                <span>Due {formatDateTime(assignment.dueAt)}</span>
                <span>{marksText(assignment.maxMarks)}</span>
                <span className={tagClasses}>{assignment.status === 'DRAFT' ? 'Draft' : 'Published'}</span>
              </p>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.upcomingAssignments.length} total={dashboard.upcomingAssignmentCount} />
    </DashboardSection>
  )
}

function NeedsGrading({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection level={level} id="faculty-grading" title="Needs Grading">
      {dashboard.needsGrading.length === 0 ? (
        <SectionEmpty>No submissions need grading.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.needsGrading.map((pending) => (
            <li key={pending.submissionId} className="flex flex-col gap-2 py-2.5 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm font-medium break-all text-ink">{pending.studentLoginId}</p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {pending.assignmentTitle}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {pending.courseCode}
                </p>
                <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-muted">
                  <span>Attempt {pending.latestAttemptNumber}</span>
                  <span>Submitted {formatDateTime(pending.latestSubmittedAt)}</span>
                  <TimingTag late={pending.latestLate} />
                </p>
              </div>
              {/* The existing grading page opens this submission with ?submission=. */}
              <Link
                to={`/teaching/${pending.lmsCourseId}/assignments/${pending.assignmentId}/submissions?submission=${pending.submissionId}`}
                className={actionLinkClasses}
              >
                Review<span className="sr-only"> {pending.studentLoginId}’s {pending.assignmentTitle}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.needsGrading.length} total={dashboard.needsGradingCount} />
    </DashboardSection>
  )
}

function markingText(session: FacultyDashboardOpenSession): string {
  if (session.totalRecords === 0) {
    return 'No students on the roster'
  }
  return session.unmarkedCount === 0 ? 'All students marked' : `${session.unmarkedCount} unmarked`
}

function OpenAttendance({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection level={level} id="faculty-attendance" title="Open Attendance" link={{ to: '/attendance', label: 'View attendance' }}>
      {dashboard.openAttendanceSessions.length === 0 ? (
        <SectionEmpty>No open attendance sessions.</SectionEmpty>
      ) : (
        <ul className="divide-y divide-line">
          {dashboard.openAttendanceSessions.map((session) => (
            <li key={session.attendanceSessionId} className="flex flex-col gap-2 py-2.5 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm break-words text-ink">
                  <span className="font-semibold">{session.courseCode}</span>
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {session.courseName}
                </p>
                <p className="text-xs leading-5 break-words text-ink-muted">
                  {formatDateTime(session.heldAt)}
                  {session.topic && (
                    <>
                      <span aria-hidden="true"> · </span>
                      <span className="sr-only">, </span>
                      {session.topic}
                    </>
                  )}
                </p>
                <p className="text-xs text-ink">{markingText(session)}</p>
              </div>
              {/* The existing attendance page opens this session with ?session=. */}
              <Link to={`/attendance/${session.courseOfferingId}?session=${session.attendanceSessionId}`} className={actionLinkClasses}>
                Continue<span className="sr-only"> {session.courseCode} attendance of {formatDateTime(session.heldAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <PreviewNote shown={dashboard.openAttendanceSessions.length} total={dashboard.openAttendanceCount} />
    </DashboardSection>
  )
}

/**
 * The faculty dashboard: what GET /api/dashboard/faculty sends, as short previews that open the existing Teaching,
 * assignment, grading, and attendance pages. The backend already decided what is visible; nothing is filtered,
 * calculated, or invented here.
 */
export function FacultyDashboard({ level = 2 }: { level?: Level }) {
  const dashboard = useApiRequest(getFacultyDashboard)

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
        <AccessDeniedState title="You don’t have access to the Faculty Dashboard." message={SIGN_IN_AGAIN} />
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
      {/* Three real counts, kept small; the sections below show their previews. */}
      <dl className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
        <div>
          <dt className="text-ink-muted">Teaching Courses</dt>
          <dd className="text-xl font-semibold text-ink">{data.teachingCourseCount}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Needs Grading</dt>
          <dd className="text-xl font-semibold text-ink">{data.needsGradingCount}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Open Attendance</dt>
          <dd className="text-xl font-semibold text-ink">{data.openAttendanceCount}</dd>
        </div>
      </dl>
      <TeachingCourses dashboard={data} level={level} />
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <UpcomingAssignments dashboard={data} level={level} />
        <NeedsGrading dashboard={data} level={level} />
      </div>
      <OpenAttendance dashboard={data} level={level} />
    </div>
  )
}
