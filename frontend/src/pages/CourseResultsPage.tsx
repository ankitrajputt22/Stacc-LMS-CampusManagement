import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseRouteId } from '../courses/courseRoute'
import { creditsText } from '../courses/credits'
import { ResultRow } from '../results/ResultRow'
import { ASK_THE_COLLEGE, RESULTS_DENIED, SIGN_IN_AGAIN } from '../results/resultText'
import { useCourseResults } from '../results/useResults'
import type { CourseResultRoster } from '../types/results'
import { ButtonLink, Card, PageHeader } from '../ui'

const RESULTS = { to: '/results', label: 'Results' }

/** A page state with the breadcrumb above it and a heading for screen readers. */
function ResultsState({ children }: { children: ReactNode }) {
  return (
    <>
      <title>Results · Stacc</title>
      <Breadcrumb parent={RESULTS} />
      <h1 className="sr-only">Course results</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function CourseUnavailable() {
  return (
    <>
      <title>Course results unavailable · Stacc</title>
      <Breadcrumb parent={RESULTS} />
      <div className="mt-4">
        <PageHeader
          title="Course results unavailable"
          description="This course offering could not be found, or it is not one you currently teach."
        />
      </div>
      <ButtonLink to={RESULTS.to} variant="secondary">
        Back to Results
      </ButtonLink>
    </>
  )
}

function Roster({ roster }: { roster: CourseResultRoster }) {
  return (
    <>
      <title>{`${roster.courseName} · Results · Stacc`}</title>
      <Breadcrumb parent={RESULTS} current={roster.courseName} />
      <div className="mt-4">
        <PageHeader
          eyebrow={roster.courseCode}
          title={roster.courseName}
          description={
            <>
              {creditsText(roster.credits)}
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, Program </span>
              {roster.programCode}
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, </span>
              Semester {roster.semesterNumber}
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, Academic session </span>
              {roster.academicSessionCode}
            </>
          }
        />
      </div>

      <div className="max-w-5xl space-y-4">
        <Card
          title="Students"
          description="The official enrollments of this course offering, in student ID order. Saving keeps a result as a draft; students see a result only once it is published."
          action={<p className="text-sm text-ink-muted">{roster.students.length === 1 ? '1 student' : `${roster.students.length} students`}</p>}
          padding="none"
        >
          {roster.students.length === 0 ? (
            <EmptyState title="No students enrolled." description="Official enrollments of this course offering will appear here." />
          ) : (
            <ul aria-label="Students" className="divide-y divide-line">
              {roster.students.map((student) => (
                <li key={student.courseEnrollmentId}>
                  <ResultRow courseOfferingId={roster.courseOfferingId} student={student} />
                </li>
              ))}
            </ul>
          )}
        </Card>
        <ButtonLink to={RESULTS.to} variant="ghost">
          <span aria-hidden="true">←</span> Back to Results
        </ButtonLink>
      </div>
    </>
  )
}

function CourseResults({ courseOfferingId }: { courseOfferingId: number }) {
  const roster = useCourseResults(courseOfferingId)

  switch (roster.status) {
    case 'loading':
      return (
        <ResultsState>
          <LoadingState label="Loading course results…" />
        </ResultsState>
      )
    case 'notFound':
      return <CourseUnavailable />
    case 'denied':
      return (
        <ResultsState>
          <AccessDeniedState title={RESULTS_DENIED} message={SIGN_IN_AGAIN} />
        </ResultsState>
      )
    case 'failed':
      return (
        <ResultsState>
          <ErrorState title="Couldn’t load results." message="Check your connection and try again." onRetry={roster.retry} />
        </ResultsState>
      )
    case 'loaded':
      return <Roster roster={roster.data} />
  }
}

/**
 * Official results for one course offering the signed-in faculty member teaches, at /results/:courseOfferingId.
 * The backend checks the teaching assignment on every request; no LMS course is involved, and nothing is calculated
 * from assignments or attendance. Accounts without the faculty role see an explanation instead, and no request is made.
 */
export function CourseResultsPage() {
  const { account } = useAuth()
  const { courseOfferingId } = useParams()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const parsedId = parseRouteId(courseOfferingId)

  if (!isFaculty) {
    return (
      <ResultsState>
        <AccessDeniedState title="This page is for faculty accounts." message={ASK_THE_COLLEGE} />
      </ResultsState>
    )
  }
  if (parsedId === null) {
    return <CourseUnavailable />
  }
  return <CourseResults courseOfferingId={parsedId} />
}
