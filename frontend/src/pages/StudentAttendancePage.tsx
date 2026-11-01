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
import { Badge, Card, DataTable, PageHeader } from '../ui'

type StudentAttendanceSession = StudentCourseAttendance['sessions'][number]

function Figure({ term, value }: { term: string; value: string }) {
  return (
    <div className="rounded-control border border-line bg-surface-muted px-3 py-2">
      <dt className="text-sm text-ink-muted">{term}</dt>
      <dd className="text-lg font-semibold text-ink">{value}</dd>
    </div>
  )
}

function CourseAttendance({ course }: { course: StudentCourseAttendance }) {
  return (
    <Card
      title={
        <>
          <span className="block text-sm font-semibold tracking-[0.04em] text-ink-muted">{course.courseCode}</span>
          {course.courseName}
        </>
      }
      description={
        <>
          <span className="sr-only">Program </span>
          {course.programCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          Semester {course.semesterNumber}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, Academic session </span>
          {course.academicSessionCode}
        </>
      }
      padding="none"
    >
      {/* Exactly the backend's counts and percentage. There is no threshold or warning. */}
      <dl className="grid grid-cols-2 gap-2 px-4 py-4 sm:grid-cols-4 sm:px-5">
        <Figure term="Present" value={String(course.presentCount)} />
        <Figure term="Absent" value={String(course.absentCount)} />
        <Figure term="Sessions" value={String(course.totalSessions)} />
        <Figure
          term="Attendance"
          value={course.attendancePercentage === null ? 'Not available' : formatPercentage(course.attendancePercentage)}
        />
      </dl>
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Session history</h3>
      <DataTable<StudentAttendanceSession>
        caption={`Sessions of ${course.courseName}, latest first`}
        columns={[
          {
            key: 'when',
            header: 'Class',
            primary: true,
            cell: (session) => <time dateTime={session.heldAt}>{formatDateTime(session.heldAt)}</time>,
          },
          { key: 'topic', header: 'Topic', cell: (session) => session.topic ?? '—' },
          {
            key: 'status',
            header: 'Attendance',
            cell: (session) => (
              <Badge tone={session.status === 'PRESENT' ? 'success' : 'danger'}>
                {session.status === 'PRESENT' ? 'Present' : 'Absent'}
              </Badge>
            ),
          },
        ]}
        rows={course.sessions}
        rowKey={(session) => session.attendanceSessionId}
      />
    </Card>
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
    <div className="max-w-4xl">
      <title>Attendance · Academic · Stacc</title>
      <PageHeader
        eyebrow="ERP · Academics"
        title="Attendance"
        description={isStudent ? 'Your attendance in each course, as your teachers finalized it, latest class first.' : undefined}
      />
      <div>
        {isStudent ? (
          <MyAttendance />
        ) : (
          <StatePanel>
            <AccessDeniedState title="This page is for student accounts." message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </div>
  )
}
