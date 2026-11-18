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
import { RubricView } from '../courses/RubricView'
import { ButtonLink, Card, PageHeader } from '../ui'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { courseBanner } from '../courses/courseFactsLine'
import { useLmsCourse } from '../courses/useLmsCourse'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function coursePath(lmsCourseId: number | null): string {
  return lmsCourseId === null ? COURSES.to : `/courses/${lmsCourseId}`
}

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

function Fact({ icon, term, children }: { icon: NavIconName; term: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 gap-2.5 py-2">
      <NavIcon name={icon} className="mt-0.5 size-5 shrink-0 text-ink-muted" />
      <div className="min-w-0">
        <dt className="text-sm font-semibold text-ink">{term}</dt>
        <dd className="flex flex-wrap items-center gap-2 text-sm break-words text-ink">{children}</dd>
      </div>
    </div>
  )
}

function AssignmentDetail({ assignment }: { assignment: LmsCourseAssignmentDetail }) {
  const [now] = useState(() => Date.now())
  const course = useLmsCourse(assignment.lmsCourseId)

  return (
    <div>
      <title>{`${assignment.title} · ${assignment.courseName} · Stacc`}</title>
      {course.status === 'loaded' ? (
        <div className="mb-5">
          <p className="text-2xl font-bold break-words text-portal-navy sm:text-3xl">{courseBanner(course.data).name}</p>
          <p className="mt-1 break-words text-ink">{courseBanner(course.data).facts}</p>
        </div>
      ) : (
        <p className="mb-5 text-2xl font-bold break-words text-portal-navy sm:text-3xl">{`${assignment.courseCode} ${assignment.courseName}`}</p>
      )}

      <section aria-labelledby="assignment-title" className="rounded-panel border border-line bg-surface px-5 py-4">
        <h1 id="assignment-title" className="flex items-center gap-3 text-3xl font-bold tracking-tight break-words text-portal-navy">
          <span aria-hidden="true" className="h-9 w-1.5 shrink-0 rounded bg-teal-600" />
          {assignment.title}
        </h1>
        <dl className="mt-3 flex flex-wrap gap-x-8 gap-y-1">
          <Fact icon="calendar" term="Due">
            <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
            {isPast(assignment.dueAt, now) && <PastDueLabel />}
          </Fact>
          <Fact icon="exams" term="Maximum">
            {marksText(assignment.maxMarks)}
          </Fact>
          {assignment.availableFrom && (
            <Fact icon="calendar" term="Opens">
              <time dateTime={assignment.availableFrom}>{formatDateTime(assignment.availableFrom)}</time>
            </Fact>
          )}
          <Fact icon="attendance" term="Late work">
            {!assignment.allowLateSubmissions
              ? 'Not accepted after the due date'
              : assignment.availableUntil
                ? `Accepted until ${formatDateTime(assignment.availableUntil)}, marked late`
                : 'Accepted, marked late'}
          </Fact>
          <Fact icon="todo" term="Attempts">
            {assignment.maxAttempts === null ? 'Unlimited' : String(assignment.maxAttempts)}
          </Fact>
        </dl>
        {(assignment.outcomes.length > 0 || assignment.bloomLevel) && (
          <dl className="mt-3 grid gap-3 border-t border-line pt-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            {assignment.outcomes.length > 0 && (
              <div>
                <dt className="text-sm font-semibold text-ink">Course outcomes</dt>
                <dd className="text-sm text-ink">
                  {assignment.outcomes.map((outcome) => (
                    <span key={outcome.id} className="block">
                      <span className="font-semibold">{outcome.code}</span> {outcome.description}
                    </span>
                  ))}
                </dd>
              </div>
            )}
            {assignment.bloomLevel && (
              <div>
                <dt className="text-sm font-semibold text-ink">Bloom level</dt>
                <dd className="text-sm text-ink">{bloomLabel(assignment.bloomLevel)}</dd>
              </div>
            )}
          </dl>
        )}
        <p className="mt-2 text-xs text-ink-muted">
          Published <time dateTime={assignment.publishedAt}>{formatDateTime(assignment.publishedAt)}</time>
        </p>
      </section>

      <Card title="Instructions" className="mt-6">
        <div>
          <FormattedText
            text={assignment.instructions}
            format={assignment.instructionsFormat}
            className="text-sm leading-6 text-ink"
            headingLevel={3}
          />
        </div>
      </Card>

      {assignment.rubric && (
        <Card title="Rubric" description={`How this assignment is graded: ${assignment.rubric.title}.`} className="mt-6">
          <RubricView rubric={assignment.rubric} />
        </Card>
      )}

      <AssignmentSubmission
        lmsCourseId={assignment.lmsCourseId}
        assignmentId={assignment.id}
        dueAt={assignment.dueAt}
        submissionWindow={assignment.submissionWindow}
        availableFrom={assignment.availableFrom}
        maxAttempts={assignment.maxAttempts}
      />

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
