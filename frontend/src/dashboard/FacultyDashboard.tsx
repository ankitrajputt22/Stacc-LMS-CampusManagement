import { Link } from 'react-router'
import { getFacultyDashboard } from '../api/dashboardApi'
import { useApiRequest } from '../api/useApiRequest'
import { actionLinkClasses } from '../components/actionStyles'
import { buttonClasses } from '../ui/styles'
import { DataTable } from '../ui'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { TimingTag } from '../courses/AttemptParts'
import { formatDateTime } from '../courses/dates'
import { lmsStatusWord } from '../teaching/lmsCourseText'
import type { FacultyDashboard as Dashboard, FacultyDashboardCourse, FacultyDashboardOpenSession } from '../types/dashboard'
import { DashboardSection, PreviewNote, SectionEmpty } from './DashboardSection'
import { PlannerList } from './PlannerList'

type Level = 2 | 3

const primarySmallClasses = buttonClasses('primary', 'sm')
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

function CourseAction({ course }: { course: FacultyDashboardCourse }) {
  const name = <span className="sr-only"> {course.courseCode}</span>
  if (course.lmsCourseId !== null && (course.lmsCourseStatus === 'DRAFT' || course.lmsCourseStatus === 'PUBLISHED')) {
    return (
      <Link to={`/teaching/${course.lmsCourseId}`} className={primarySmallClasses}>
        Open LMS course{name}
      </Link>
    )
  }
  return (
    <Link to="/teaching" className={primarySmallClasses}>
      {course.lmsCourseStatus === null ? 'Manage teaching' : 'View teaching'}
      {name}
    </Link>
  )
}

const LMS_TONE: Record<string, string> = {
  PUBLISHED: 'bg-success-soft text-ink',
  DRAFT: 'bg-amber-50 text-ink',
  ARCHIVED: 'bg-surface-muted text-ink-muted',
}

function TeachingCourses({ dashboard, level }: { dashboard: Dashboard; level: Level }) {
  return (
    <DashboardSection level={level} id="faculty-courses" title="Teaching Courses" link={{ to: '/teaching', label: 'View teaching' }}>
      {dashboard.teachingCourses.length === 0 ? (
        <SectionEmpty>No teaching courses available.</SectionEmpty>
      ) : (
        <DataTable<FacultyDashboardCourse>
          caption="Teaching courses"
          rows={dashboard.teachingCourses}
          rowKey={(course) => course.courseOfferingId}
          columns={[
            { key: 'code', header: 'Course code', primary: true, cell: (course) => <span className="font-bold">{course.courseCode}</span> },
            { key: 'name', header: 'Course title', cell: (course) => course.courseName },
            { key: 'program', header: 'Program', cell: (course) => course.programCode },
            { key: 'semester', header: 'Semester', cell: (course) => `Semester ${course.semesterNumber}` },
            { key: 'session', header: 'Academic year', cell: (course) => course.academicSessionCode },
            {
              key: 'lms',
              header: 'LMS status',
              cell: (course) => (
                <span className={`inline-block rounded-sm px-2 py-0.5 text-sm font-medium ${LMS_TONE[course.lmsCourseStatus ?? ''] ?? 'bg-surface-muted text-ink'}`}>
                  {lmsStatusWord(course.lmsCourseStatus)}
                </span>
              ),
            },
            { key: 'action', header: 'Action', cell: (course) => <CourseAction course={course} /> },
          ]}
        />
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
        <PlannerList
          headingLevel={level === 2 ? 3 : 4}
          items={dashboard.upcomingAssignments.map((assignment) => ({
            key: String(assignment.assignmentId),
            colorId: assignment.lmsCourseId,
            courseCode: assignment.courseCode,
            courseName: assignment.courseName,
            title: assignment.title,
            to: `/teaching/${assignment.lmsCourseId}#assignments`,
            dueAt: assignment.dueAt,
            maxMarks: assignment.maxMarks,
            tag: assignment.status === 'DRAFT' ? 'Draft' : undefined,
          }))}
        />
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
      <dl className="grid gap-4 sm:grid-cols-3">
        {([
          ['Teaching Courses', data.teachingCourseCount],
          ['Needs Grading', data.needsGradingCount],
          ['Open Attendance', data.openAttendanceCount],
        ] as const).map(([label, value]) => (
          <div key={label} className="rounded-panel border border-line bg-surface px-5 py-4">
            <dt className="text-lg font-bold text-ink">{label}</dt>
            <dd className="mt-1 text-4xl font-bold text-ink tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      <TeachingCourses dashboard={data} level={level} />
      <UpcomingAssignments dashboard={data} level={level} />
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <NeedsGrading dashboard={data} level={level} />
        <OpenAttendance dashboard={data} level={level} />
      </div>
    </div>
  )
}
