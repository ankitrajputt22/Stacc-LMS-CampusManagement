import { type ReactNode, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId, parseRouteId } from '../courses/courseRoute'
import { formatDateTime, isPast } from '../courses/dates'
import { marksText } from '../courses/marks'
import { PastDueLabel } from '../courses/PastDueLabel'
import { useCourseAssignment } from '../courses/useCourseAssignment'
import type { LmsCourseAssignmentDetail } from '../types/lms'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function coursePath(lmsCourseId: number | null): string {
  return lmsCourseId === null ? COURSES.to : `/courses/${lmsCourseId}`
}

/** A page state with the breadcrumb above it and a heading for screen readers. */
function AssignmentState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>Assignment · Stacc</title>
      <Breadcrumb parent={lmsCourseId === null ? COURSES : [COURSES, { to: coursePath(lmsCourseId), label: 'Course' }]} />
      <h1 className="sr-only">Assignment</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function BackToCourse({ lmsCourseId }: { lmsCourseId: number | null }) {
  return (
    <Link to={coursePath(lmsCourseId)} className={`mt-6 text-sm ${backLinkClasses}`}>
      {lmsCourseId === null ? 'Back to Courses' : 'Back to Course'}
    </Link>
  )
}

function AssignmentUnavailable({ lmsCourseId }: { lmsCourseId: number | null }) {
  return (
    <>
      <title>Assignment unavailable · Stacc</title>
      <Breadcrumb parent={COURSES} />
      <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Assignment unavailable</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        This assignment could not be found or is not available to your account.
      </p>
      <BackToCourse lmsCourseId={lmsCourseId} />
    </>
  )
}

function InformationRow({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 px-4 py-3 sm:grid-cols-[11rem_minmax(0,1fr)] sm:gap-4 sm:px-5">
      <dt className="text-sm text-ink-muted">{term}</dt>
      <dd className="flex flex-wrap items-center gap-2 text-sm break-words text-ink">{children}</dd>
    </div>
  )
}

/** One published assignment: what to do, by when, and for how many marks. Only what the backend sent. */
function AssignmentDetail({ assignment }: { assignment: LmsCourseAssignmentDetail }) {
  const [now] = useState(() => Date.now())

  return (
    <>
      <title>{`${assignment.title} · ${assignment.courseName} · Stacc`}</title>
      <Breadcrumb
        parent={[COURSES, { to: coursePath(assignment.lmsCourseId), label: assignment.courseName }]}
        current={assignment.title}
      />

      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
          {assignment.courseCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {assignment.courseName}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">{assignment.title}</h1>
      </header>

      <dl className="mt-6 max-w-2xl divide-y divide-line rounded-panel border border-line bg-surface">
        <InformationRow term="Due">
          <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
          {isPast(assignment.dueAt, now) && <PastDueLabel />}
        </InformationRow>
        <InformationRow term="Maximum marks">{marksText(assignment.maxMarks)}</InformationRow>
        <InformationRow term="Published">
          <time dateTime={assignment.publishedAt}>{formatDateTime(assignment.publishedAt)}</time>
        </InformationRow>
      </dl>

      <section aria-labelledby="assignment-instructions-heading" className="mt-8 max-w-2xl">
        <h2 id="assignment-instructions-heading" className="text-base font-semibold text-ink">
          Instructions
        </h2>
        <div className="mt-3 rounded-panel border border-line bg-surface px-4 py-3 sm:px-5">
          {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
          <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">{assignment.instructions}</p>
        </div>
      </section>

      <BackToCourse lmsCourseId={assignment.lmsCourseId} />
    </>
  )
}

function StudentAssignment({ lmsCourseId, assignmentId }: { lmsCourseId: number | null; assignmentId: number | null }) {
  const assignment = useCourseAssignment(lmsCourseId, assignmentId)

  switch (assignment.status) {
    case 'loading':
      return (
        <AssignmentState lmsCourseId={lmsCourseId}>
          <LoadingState label="Loading assignment…" />
        </AssignmentState>
      )
    case 'notFound':
      return <AssignmentUnavailable lmsCourseId={lmsCourseId} />
    case 'denied':
      return (
        <AssignmentState lmsCourseId={lmsCourseId}>
          <AccessDeniedState title="You don’t have access to this assignment." message={ASK_THE_COLLEGE} />
        </AssignmentState>
      )
    case 'failed':
      return (
        <AssignmentState lmsCourseId={lmsCourseId}>
          <ErrorState
            title="Couldn’t load this assignment"
            message="Check your connection and try again."
            onRetry={assignment.retry}
          />
        </AssignmentState>
      )
    case 'loaded':
      return <AssignmentDetail assignment={assignment.data} />
  }
}

/**
 * One published assignment for the signed-in student, at /courses/:lmsCourseId/assignments/:assignmentId.
 * Arriving from the course page proves nothing: the backend checks the course and the assignment again on
 * every request. Accounts without the student role see an explanation instead, and no request is made.
 */
export function CourseAssignmentPage() {
  const { account } = useAuth()
  const { lmsCourseId, assignmentId } = useParams()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const courseId = parseCourseId(lmsCourseId)

  if (!isStudent) {
    return (
      <AssignmentState lmsCourseId={courseId}>
        <AccessDeniedState title="Courses are for student accounts." message={ASK_THE_COLLEGE} />
      </AssignmentState>
    )
  }

  return <StudentAssignment lmsCourseId={courseId} assignmentId={parseRouteId(assignmentId)} />
}
