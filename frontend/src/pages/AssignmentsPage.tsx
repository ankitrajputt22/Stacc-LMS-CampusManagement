import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime, isPast } from '../courses/dates'
import { marksText } from '../courses/marks'
import { PastDueLabel } from '../courses/PastDueLabel'
import { ASSIGNMENTS_SECTION_ID, submissionsPath } from '../teaching/editorState'
import { StatusTag } from '../teaching/StatusTag'
import { useMyFacultyAssignments } from '../teaching/useMyFacultyAssignments'
import type { LmsFacultyAssignmentOverview } from '../types/lms'

const NOT_FACULTY = 'This page is for faculty accounts.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'
// A token issued before the account got the permission does not carry it until the next sign-in.
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

function AssignmentRow({ assignment, now }: { assignment: LmsFacultyAssignmentOverview; now: number }) {
  return (
    <li className="group relative px-4 py-3 first:rounded-t-panel last:rounded-b-panel hover:bg-surface-muted has-[a:focus-visible]:outline-2 has-[a:focus-visible]:-outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {assignment.courseCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {assignment.courseName}
      </p>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1">
        <h2 className="text-sm font-semibold break-words text-ink">
          {/* The assignment is managed on its course page. The click area covers the whole row. */}
          <Link
            to={`/teaching/${assignment.lmsCourseId}#${ASSIGNMENTS_SECTION_ID}`}
            className="after:absolute after:inset-0 focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
          >
            {assignment.title}
            <span className="sr-only">, manage in {assignment.courseName}</span>
          </Link>
        </h2>
        <StatusTag status={assignment.status} label="Assignment status" />
        {isPast(assignment.dueAt, now) && <PastDueLabel />}
      </div>
      <p className="mt-0.5 text-sm text-ink-muted">
        Due <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {marksText(assignment.maxMarks)}
      </p>
      {assignment.status === 'PUBLISHED' && (
        // Above the row's stretched link, so it opens the submissions instead of the course.
        <Link
          to={submissionsPath(assignment.lmsCourseId, assignment.assignmentId)}
          className="relative z-10 mt-1 inline-flex min-h-11 items-center rounded-control text-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0"
        >
          Review submissions
          <span className="sr-only"> for {assignment.title}</span>
        </Link>
      )}
    </li>
  )
}

function MyAssignments() {
  const assignments = useMyFacultyAssignments()
  const [now] = useState(() => Date.now())

  if (assignments.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading your assignments…" />
      </StatePanel>
    )
  }

  if (assignments.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to faculty assignments." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  }

  if (assignments.status === 'failed' || assignments.status === 'notFound') {
    return (
      <StatePanel>
        <ErrorState
          title="Couldn’t load your assignments"
          message="Check your connection and try again."
          onRetry={assignments.retry}
        />
      </StatePanel>
    )
  }

  if (assignments.data.length === 0) {
    return (
      <StatePanel>
        <EmptyState
          title="No assignments yet."
          description="Assignments you create for your teaching courses will appear here."
        />
      </StatePanel>
    )
  }

  return (
    <ul aria-label="Your assignments" className="max-w-3xl divide-y divide-line rounded-panel border border-line bg-surface">
      {assignments.data.map((assignment) => (
        <AssignmentRow key={assignment.assignmentId} assignment={assignment} now={now} />
      ))}
    </ul>
  )
}

/**
 * The draft and published assignments of every course the signed-in faculty member teaches, earliest
 * deadline first. Each one is managed on its course page; there is no second editor here. Accounts without
 * the faculty role see an explanation instead and no request is made. The backend decides which courses count.
 */
export function AssignmentsPage() {
  const { account } = useAuth()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])

  return (
    <>
      <title>Assignments · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Assignments</h1>
      {isFaculty && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Assignments across the courses you teach, earliest deadline first. Open one to manage it on its course page.
        </p>
      )}

      <div className="mt-6">
        {isFaculty ? (
          <MyAssignments />
        ) : (
          <StatePanel>
            <AccessDeniedState title={NOT_FACULTY} message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
