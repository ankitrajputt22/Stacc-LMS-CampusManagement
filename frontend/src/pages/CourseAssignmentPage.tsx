import { type ReactNode, useState } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { AssignmentSubmission } from '../courses/AssignmentSubmission'
import { parseCourseId, parseRouteId } from '../courses/courseRoute'
import { formatDateTime, isPast } from '../courses/dates'
import { marksText } from '../courses/marks'
import { PastDueLabel } from '../courses/PastDueLabel'
import { useCourseAssignment } from '../courses/useCourseAssignment'
import type { LmsCourseAssignmentDetail } from '../types/lms'
import { bloomLabel } from '../courses/bloomText'
import { FormattedText } from '../formatting/FormattedText'
import { ButtonLink, Card, PageHeader } from '../ui'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function coursePath(lmsCourseId: number | null): string {
  return lmsCourseId === null ? COURSES.to : `/courses/${lmsCourseId}`
}

/** A page state with the breadcrumb above it and a heading for screen readers. */
function AssignmentState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>Assignment · Stacc</title>
      <PageHeader
        breadcrumb={<Breadcrumb parent={lmsCourseId === null ? COURSES : [COURSES, { to: coursePath(lmsCourseId), label: 'Course' }]} />}
        title="Assignment"
      />
      <StatePanel>{children}</StatePanel>
    </>
  )
}

function BackToCourse({ lmsCourseId }: { lmsCourseId: number | null }) {
  return (
    <ButtonLink to={coursePath(lmsCourseId)} variant="secondary">
      {lmsCourseId === null ? 'Back to Courses' : 'Back to Course'}
    </ButtonLink>
  )
}

function AssignmentUnavailable({ lmsCourseId }: { lmsCourseId: number | null }) {
  return (
    <>
      <title>Assignment unavailable · Stacc</title>
      <PageHeader
        breadcrumb={<Breadcrumb parent={COURSES} />}
        title="Assignment unavailable"
        description="This assignment could not be found or is not available to your account."
        actions={<BackToCourse lmsCourseId={lmsCourseId} />}
      />
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

/**
 * One published assignment: what to do, by when, and for how many marks, then the student's own submission.
 * Only what the backend sent.
 */
function AssignmentDetail({ assignment }: { assignment: LmsCourseAssignmentDetail }) {
  const [now] = useState(() => Date.now())

  return (
    <div className="max-w-3xl">
      <title>{`${assignment.title} · ${assignment.courseName} · Stacc`}</title>
      <PageHeader
        breadcrumb={
          <Breadcrumb
            parent={[COURSES, { to: coursePath(assignment.lmsCourseId), label: assignment.courseName }]}
            current={assignment.title}
          />
        }
        eyebrow={
          <>
            {assignment.courseCode}
            <span aria-hidden="true"> · </span>
            <span className="sr-only">, </span>
            {assignment.courseName}
          </>
        }
        title={assignment.title}
      />

      <dl className="divide-y divide-line rounded-panel border border-line bg-surface">
        <InformationRow term="Due">
          <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
          {isPast(assignment.dueAt, now) && <PastDueLabel />}
        </InformationRow>
        <InformationRow term="Maximum marks">{marksText(assignment.maxMarks)}</InformationRow>
        <InformationRow term="Published">
          <time dateTime={assignment.publishedAt}>{formatDateTime(assignment.publishedAt)}</time>
        </InformationRow>
        {assignment.outcomes.length > 0 && (
          <InformationRow term="Course outcomes">
            <span>
              {assignment.outcomes.map((outcome) => (
                <span key={outcome.id} className="block">
                  <span className="font-semibold">{outcome.code}</span> {outcome.description}
                </span>
              ))}
            </span>
          </InformationRow>
        )}
        {assignment.bloomLevel && <InformationRow term="Bloom level">{bloomLabel(assignment.bloomLevel)}</InformationRow>}
      </dl>

      <Card title="Instructions" className="mt-6">
        <div>
          {/* Shown as its format says; never as HTML. */}
          <FormattedText
            text={assignment.instructions}
            format={assignment.instructionsFormat}
            className="text-sm leading-6 text-ink"
            headingLevel={3}
          />
        </div>
      </Card>

      <AssignmentSubmission lmsCourseId={assignment.lmsCourseId} assignmentId={assignment.id} dueAt={assignment.dueAt} />

      <div className="mt-6">
        <BackToCourse lmsCourseId={assignment.lmsCourseId} />
      </div>
    </div>
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
