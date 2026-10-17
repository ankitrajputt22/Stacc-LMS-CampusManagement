import { type ReactNode, useEffect, useRef } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { TimingTag } from '../courses/AttemptParts'
import { parseCourseId, parseRouteId } from '../courses/courseRoute'
import { formatDateTime } from '../courses/dates'
import { formatMarks, marksText } from '../courses/marks'
import { ASSIGNMENTS_SECTION_ID } from '../teaching/editorState'
import { backLinkClasses, GRADING_DENIED, SIGN_IN_AGAIN, SubmissionReview } from '../teaching/SubmissionReview'
import { useFacultySubmissions } from '../teaching/useFacultySubmissions'
import { useMyFacultyAssignments } from '../teaching/useMyFacultyAssignments'
import type { LmsFacultyAssignmentOverview, LmsFacultySubmissionSummary } from '../types/lms'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function coursePath(lmsCourseId: number): string {
  return `/teaching/${lmsCourseId}`
}

/** A page state with the breadcrumb above it and a heading for screen readers. */
function SubmissionsState({ children }: { children: ReactNode }) {
  return (
    <>
      <title>Submissions · Stacc</title>
      <Breadcrumb parent={TEACHING} />
      <h1 className="sr-only">Submissions</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function AssignmentUnavailable({ lmsCourseId }: { lmsCourseId: number | null }) {
  return (
    <>
      <title>Assignment unavailable · Stacc</title>
      <Breadcrumb parent={TEACHING} />
      <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Assignment unavailable</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        This assignment could not be found, is not published, or is not one you currently teach.
      </p>
      <Link to={lmsCourseId === null ? TEACHING.to : coursePath(lmsCourseId)} className={`mt-6 ${backLinkClasses}`}>
        {lmsCourseId === null ? 'Back to Teaching' : 'Back to course'}
      </Link>
    </>
  )
}

function SubmissionRow({ submission }: { submission: LmsFacultySubmissionSummary }) {
  const attempts = submission.attemptCount === 1 ? '1 attempt' : `${submission.attemptCount} attempts`
  return (
    <li className="group relative px-4 py-3 first:rounded-t-panel last:rounded-b-panel hover:bg-surface-muted has-[a:focus-visible]:outline-2 has-[a:focus-visible]:-outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h3 className="text-sm font-semibold break-all text-ink">
          {/* The click area covers the whole row. */}
          <Link
            to={{ search: `?submission=${submission.submissionId}` }}
            data-submission-id={submission.submissionId}
            className="after:absolute after:inset-0 focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
          >
            {submission.studentLoginId}
            <span className="sr-only">, review submission</span>
          </Link>
        </h3>
        <span className="sr-only">Latest attempt: </span>
        <TimingTag late={submission.latestLate} />
        <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
          {submission.latestGraded && submission.latestMarksAwarded !== null && submission.latestMaxMarksSnapshot !== null
            ? `Graded ${formatMarks(submission.latestMarksAwarded)} / ${formatMarks(submission.latestMaxMarksSnapshot)}`
            : 'Not graded'}
        </span>
      </div>
      <p className="mt-0.5 text-sm text-ink-muted">
        {attempts}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Latest submitted <time dateTime={submission.latestSubmittedAt}>{formatDateTime(submission.latestSubmittedAt)}</time>
      </p>
    </li>
  )
}

function SubmissionList({ submissions }: { submissions: LmsFacultySubmissionSummary[] }) {
  const listRef = useRef<HTMLOListElement>(null)
  const { state } = useLocation()
  const fromSubmission = (state as { fromSubmission?: unknown } | null)?.fromSubmission

  // Coming back from a submission, focus returns to its row.
  useEffect(() => {
    if (typeof fromSubmission === 'number') {
      listRef.current?.querySelector<HTMLElement>(`[data-submission-id="${fromSubmission}"]`)?.focus()
    }
  }, [fromSubmission])

  if (submissions.length === 0) {
    return (
      <StatePanel>
        <EmptyState title="No submissions yet." description="No students have submitted this assignment." />
      </StatePanel>
    )
  }

  return (
    <ol ref={listRef} aria-label="Submissions" className="divide-y divide-line rounded-panel border border-line bg-surface">
      {submissions.map((submission) => (
        <SubmissionRow key={submission.submissionId} submission={submission} />
      ))}
    </ol>
  )
}

type LoadedProps = {
  assignment: LmsFacultyAssignmentOverview
  submissions: LmsFacultySubmissionSummary[]
  /** Which submission is open: null for the list, or { id: null } for an address that cannot be one. */
  open: { id: number | null } | null
  onGraded: () => void
}

function AssignmentSubmissions({ assignment, submissions, open, onGraded }: LoadedProps) {
  const { pathname } = useLocation()
  const courseCrumb = { to: coursePath(assignment.lmsCourseId), label: assignment.courseName }

  return (
    <>
      <title>{`Submissions · ${assignment.title} · Stacc`}</title>
      <Breadcrumb
        parent={open === null ? [TEACHING, courseCrumb] : [TEACHING, courseCrumb, { to: pathname, label: assignment.title }]}
        current={open === null ? assignment.title : 'Submission'}
      />

      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
          {assignment.courseCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {assignment.courseName}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">{assignment.title}</h1>
        <p className="mt-2 text-sm text-ink-muted">
          Due <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {marksText(assignment.maxMarks)}
        </p>
      </header>

      {open === null ? (
        <section aria-labelledby="submissions-heading" className="mt-8 max-w-3xl">
          <h2 id="submissions-heading" className="text-base font-semibold text-ink">
            Submissions
          </h2>
          <p className="mt-1 mb-3 text-sm leading-6 text-ink-muted">
            Each student’s latest attempt, newest first. Open a submission to review every attempt and grade it.
          </p>
          <SubmissionList submissions={submissions} />
          <Link to={`${coursePath(assignment.lmsCourseId)}#${ASSIGNMENTS_SECTION_ID}`} className={`mt-6 ${backLinkClasses}`}>
            Back to course
          </Link>
        </section>
      ) : (
        // A new review for each submission, so nothing chosen or typed carries over to another student.
        <SubmissionReview
          key={open.id}
          assignmentId={assignment.assignmentId}
          submissionId={open.id}
          listPath={pathname}
          onGraded={onGraded}
        />
      )}
    </>
  )
}

function FacultySubmissions({ lmsCourseId, assignmentId }: { lmsCourseId: number; assignmentId: number }) {
  const overview = useMyFacultyAssignments()
  const submissions = useFacultySubmissions(assignmentId)
  const [searchParams] = useSearchParams()
  const submissionParam = searchParams.get('submission')

  if (submissions.status === 'notFound') {
    return <AssignmentUnavailable lmsCourseId={lmsCourseId} />
  }
  if (submissions.status === 'denied' || overview.status === 'denied') {
    return (
      <SubmissionsState>
        <AccessDeniedState title={GRADING_DENIED} message={SIGN_IN_AGAIN} />
      </SubmissionsState>
    )
  }
  if (submissions.status === 'failed' || overview.status === 'failed' || overview.status === 'notFound') {
    return (
      <SubmissionsState>
        <ErrorState
          title="Couldn’t load submissions."
          message="Check your connection and try again."
          onRetry={() => {
            overview.retry()
            submissions.retry()
          }}
        />
      </SubmissionsState>
    )
  }
  if (submissions.status === 'loading' || overview.status === 'loading') {
    return (
      <SubmissionsState>
        <LoadingState label="Loading submissions…" />
      </SubmissionsState>
    )
  }

  // The assignment must be a published one of this course, as the backend lists them.
  const assignment = overview.data.find(
    (candidate) => candidate.assignmentId === assignmentId && candidate.lmsCourseId === lmsCourseId,
  )
  if (assignment === undefined || assignment.status !== 'PUBLISHED') {
    return <AssignmentUnavailable lmsCourseId={lmsCourseId} />
  }

  return (
    <AssignmentSubmissions
      assignment={assignment}
      submissions={submissions.data}
      open={submissionParam === null ? null : { id: parseRouteId(submissionParam) }}
      onGraded={submissions.refresh}
    />
  )
}

/**
 * The submissions for one published assignment, at /teaching/:lmsCourseId/assignments/:assignmentId/submissions,
 * where the faculty member reviews each student's attempts and grades them; ?submission= opens one. The backend
 * checks the teaching assignment and the grading permission on every request. Accounts without the faculty role
 * see an explanation instead, and no request is made.
 */
export function FacultyAssignmentSubmissionsPage() {
  const { account } = useAuth()
  const { lmsCourseId, assignmentId } = useParams()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const courseId = parseCourseId(lmsCourseId)
  const parsedAssignmentId = parseRouteId(assignmentId)

  if (!isFaculty) {
    return (
      <SubmissionsState>
        <AccessDeniedState title="This page is for faculty accounts." message={ASK_THE_COLLEGE} />
      </SubmissionsState>
    )
  }
  if (courseId === null || parsedAssignmentId === null) {
    return <AssignmentUnavailable lmsCourseId={courseId} />
  }

  return <FacultySubmissions lmsCourseId={courseId} assignmentId={parsedAssignmentId} />
}
