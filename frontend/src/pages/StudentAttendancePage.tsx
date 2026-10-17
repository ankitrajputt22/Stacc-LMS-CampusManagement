import { ASK_THE_COLLEGE, ATTENDANCE_DENIED, SIGN_IN_AGAIN } from '../attendance/attendanceText'
import { formatPercentage } from '../attendance/percentage'
import { useMyAttendance } from '../attendance/useAttendance'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import type { StudentCourseAttendance } from '../types/attendance'

function Figure({ term, value }: { term: string; value: string }) {
  return (
    <div className="rounded-control border border-line bg-surface-muted px-3 py-2">
      <dt className="text-xs text-ink-muted">{term}</dt>
      <dd className="text-base font-semibold text-ink">{value}</dd>
    </div>
  )
}

function CourseAttendance({ course }: { course: StudentCourseAttendance }) {
  const headingId = `attendance-course-${course.courseOfferingId}`
  return (
    <section aria-labelledby={headingId} className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
      <h2 id={headingId} className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {course.courseName}
      </h2>
      <p className="mt-1 text-sm leading-6 break-words text-ink-muted">
        <span className="sr-only">Program </span>
        {course.programCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Semester {course.semesterNumber}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, Academic session </span>
        {course.academicSessionCode}
      </p>

      {/* Exactly the backend's counts and percentage. There is no threshold or warning. */}
      <dl className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Figure term="Present" value={String(course.presentCount)} />
        <Figure term="Absent" value={String(course.absentCount)} />
        <Figure term="Sessions" value={String(course.totalSessions)} />
        <Figure
          term="Attendance"
          value={course.attendancePercentage === null ? 'Not available' : formatPercentage(course.attendancePercentage)}
        />
      </dl>

      <h3 className="mt-4 text-sm font-semibold text-ink">Session history</h3>
      <ol aria-label={`Sessions of ${course.courseName}`} className="mt-2 divide-y divide-line rounded-control border border-line">
        {course.sessions.map((session) => (
          <li key={session.attendanceSessionId} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-3 py-2">
            <div className="min-w-0">
              <p className="text-sm text-ink">
                <time dateTime={session.heldAt}>{formatDateTime(session.heldAt)}</time>
              </p>
              {session.topic && <p className="text-xs break-words text-ink-muted">{session.topic}</p>}
            </div>
            <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
              {session.status === 'PRESENT' ? 'Present' : 'Absent'}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}

function MyAttendance() {
  const attendance = useMyAttendance()

  switch (attendance.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading attendance…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={ATTENDANCE_DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'failed':
    case 'notFound':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load attendance." message="Check your connection and try again." onRetry={attendance.retry} />
        </StatePanel>
      )
    case 'loaded':
      if (attendance.data.courses.length === 0) {
        return (
          <StatePanel>
            <EmptyState
              title="No attendance recorded yet."
              description="Attendance appears here once your teachers finalize it for a class."
            />
          </StatePanel>
        )
      }
      return (
        <div className="space-y-4">
          {attendance.data.courses.map((course) => (
            <CourseAttendance key={course.courseOfferingId} course={course} />
          ))}
        </div>
      )
  }
}

/**
 * The signed-in student's own finalized attendance, by course, at /academic/attendance. The backend finds the
 * student from the access token and never shows open sessions. Accounts without the student role see an
 * explanation instead, and no request is made.
 */
export function StudentAttendancePage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])

  return (
    <>
      <title>Attendance · Academic · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Attendance</h1>
      {isStudent && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Your attendance in each course, as your teachers finalized it, latest class first.
        </p>
      )}
      <div className="mt-6 max-w-3xl">
        {isStudent ? (
          <MyAttendance />
        ) : (
          <StatePanel>
            <AccessDeniedState title="This page is for student accounts." message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
