import { type ReactNode, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { createAttendanceSession } from '../api/attendanceApi'
import { ASK_THE_COLLEGE, ATTENDANCE_DENIED, SIGN_IN_AGAIN, backLinkClasses } from '../attendance/attendanceText'
import { SessionForm } from '../attendance/SessionForm'
import { SessionStatusTag } from '../attendance/SessionStatusTag'
import { SessionView } from '../attendance/SessionView'
import { useAttendanceSessions, useMyAttendanceCourses } from '../attendance/useAttendance'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Button } from '../components/Button'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseRouteId } from '../courses/courseRoute'
import { formatDateTime } from '../courses/dates'
import type { AttendanceCourseOffering, AttendanceSession } from '../types/attendance'

const ATTENDANCE = { to: '/attendance', label: 'Attendance' }

/** A page state with the breadcrumb above it and a heading for screen readers. */
function AttendanceState({ children }: { children: ReactNode }) {
  return (
    <>
      <title>Attendance · Stacc</title>
      <Breadcrumb parent={ATTENDANCE} />
      <h1 className="sr-only">Attendance</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function CourseUnavailable() {
  return (
    <>
      <title>Attendance course unavailable · Stacc</title>
      <Breadcrumb parent={ATTENDANCE} />
      <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Attendance course unavailable</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        This course offering could not be found, or it is not one you currently teach.
      </p>
      <Link to={ATTENDANCE.to} className={`mt-6 ${backLinkClasses}`}>
        Back to Attendance
      </Link>
    </>
  )
}

function SessionRow({ session }: { session: AttendanceSession }) {
  const when = formatDateTime(session.heldAt)
  return (
    <li className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <h3 className="text-sm font-semibold text-ink">
            <time dateTime={session.heldAt}>{when}</time>
          </h3>
          <SessionStatusTag status={session.status} />
        </div>
        <p className="mt-0.5 text-sm break-words text-ink-muted">{session.topic ?? 'No topic'}</p>
      </div>
      <Link
        to={{ search: `?session=${session.id}` }}
        data-session-id={session.id}
        className="inline-flex min-h-11 shrink-0 items-center self-start rounded-control border border-line-strong bg-surface px-3 text-sm font-medium text-ink hover:border-slate-400 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8 sm:self-auto"
      >
        {session.status === 'OPEN' ? 'Continue marking' : 'View / Correct'}
        <span className="sr-only"> the session of {when}</span>
      </Link>
    </li>
  )
}

type SessionListProps = {
  course: AttendanceCourseOffering
  sessions: AttendanceSession[]
  onCreated: (session: AttendanceSession) => void
}

function SessionList({ course, sessions, onCreated }: SessionListProps) {
  const listRef = useRef<HTMLOListElement>(null)
  const [adding, setAdding] = useState(false)
  const { state } = useLocation()
  const fromSession = (state as { fromSession?: unknown } | null)?.fromSession

  // Coming back from a session, focus returns to its row.
  useEffect(() => {
    if (typeof fromSession === 'number') {
      listRef.current?.querySelector<HTMLElement>(`[data-session-id="${fromSession}"]`)?.focus()
    }
  }, [fromSession])

  return (
    <section aria-labelledby="attendance-sessions-heading" className="mt-8 max-w-3xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="attendance-sessions-heading" className="text-base font-semibold text-ink">
          Sessions
        </h2>
        {!adding && (
          <div className="w-full sm:w-auto">
            <Button variant="secondary" onClick={() => setAdding(true)}>
              New session
            </Button>
          </div>
        )}
      </div>
      <p className="mt-1 mb-3 text-sm leading-6 text-ink-muted">
        Latest class first. A new session lists the students enrolled in {course.courseName} at that moment.
      </p>

      {adding && (
        <div className="mb-3">
          <SessionForm
            heading="New session"
            submitLabel="Create session"
            onSubmit={async (input) => {
              onCreated(await createAttendanceSession(course.courseOfferingId, input))
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      )}

      {sessions.length === 0 ? (
        !adding && (
          <StatePanel>
            <EmptyState title="No sessions yet." description="Create a session to take attendance for a class." />
          </StatePanel>
        )
      ) : (
        <ol ref={listRef} aria-label="Sessions" className="divide-y divide-line rounded-panel border border-line bg-surface">
          {sessions.map((session) => (
            <SessionRow key={session.id} session={session} />
          ))}
        </ol>
      )}

      <Link to={ATTENDANCE.to} className={`mt-6 ${backLinkClasses}`}>
        Back to Attendance
      </Link>
    </section>
  )
}

function CourseAttendance({ courseOfferingId }: { courseOfferingId: number }) {
  const courses = useMyAttendanceCourses()
  const sessions = useAttendanceSessions(courseOfferingId)
  const [searchParams] = useSearchParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const sessionParam = searchParams.get('session')

  if (sessions.status === 'notFound') {
    return <CourseUnavailable />
  }
  if (sessions.status === 'denied' || courses.status === 'denied') {
    return (
      <AttendanceState>
        <AccessDeniedState title={ATTENDANCE_DENIED} message={SIGN_IN_AGAIN} />
      </AttendanceState>
    )
  }
  if (sessions.status === 'failed' || courses.status === 'failed' || courses.status === 'notFound') {
    return (
      <AttendanceState>
        <ErrorState
          title="Couldn’t load attendance."
          message="Check your connection and try again."
          onRetry={() => {
            courses.retry()
            sessions.retry()
          }}
        />
      </AttendanceState>
    )
  }
  if (sessions.status === 'loading' || courses.status === 'loading') {
    return (
      <AttendanceState>
        <LoadingState label="Loading attendance…" />
      </AttendanceState>
    )
  }

  const course = courses.data.find((candidate) => candidate.courseOfferingId === courseOfferingId)
  if (course === undefined) {
    return <CourseUnavailable />
  }
  const courseCrumb = { to: pathname, label: course.courseName }

  return (
    <>
      <title>{`${course.courseName} · Attendance · Stacc`}</title>
      <Breadcrumb
        parent={sessionParam === null ? ATTENDANCE : [ATTENDANCE, courseCrumb]}
        current={sessionParam === null ? course.courseName : 'Session'}
      />
      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">{course.courseName}</h1>
        <p className="mt-2 text-sm leading-6 break-words text-ink-muted">
          <span className="sr-only">Program </span>
          {course.programCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          Semester {course.semesterNumber}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, Academic session </span>
          {course.academicSessionCode}
        </p>
      </header>

      {sessionParam === null ? (
        <SessionList
          course={course}
          sessions={sessions.data}
          onCreated={(created) => {
            sessions.refresh()
            // The new session opens straight away, ready to mark.
            navigate({ search: `?session=${created.id}` })
          }}
        />
      ) : (
        // A new view for each session, so nothing chosen or typed carries over to another one.
        <SessionView
          key={sessionParam}
          courseOfferingId={courseOfferingId}
          sessionId={parseRouteId(sessionParam)}
          listPath={pathname}
          onChanged={sessions.refresh}
        />
      )}
    </>
  )
}

/**
 * Attendance for one course offering the signed-in faculty member teaches, at /attendance/:courseOfferingId:
 * its sessions, a form for a new one, and ?session= for one session's roster. The backend checks the teaching
 * assignment on every request; no LMS course is involved. Accounts without the faculty role see an explanation
 * instead, and no request is made.
 */
export function CourseAttendancePage() {
  const { account } = useAuth()
  const { courseOfferingId } = useParams()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const parsedId = parseRouteId(courseOfferingId)

  if (!isFaculty) {
    return (
      <AttendanceState>
        <AccessDeniedState title="This page is for faculty accounts." message={ASK_THE_COLLEGE} />
      </AttendanceState>
    )
  }
  if (parsedId === null) {
    return <CourseUnavailable />
  }
  return <CourseAttendance courseOfferingId={parsedId} />
}
