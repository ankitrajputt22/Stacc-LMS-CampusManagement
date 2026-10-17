import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
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
import { ASK_THE_COLLEGE, RESULTS_DENIED, SIGN_IN_AGAIN, backLinkClasses } from '../results/resultText'
import { useCourseResults } from '../results/useResults'
import type { CourseResultRoster } from '../types/results'

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
      <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Course results unavailable</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        This course offering could not be found, or it is not one you currently teach.
      </p>
      <Link to={RESULTS.to} className={`mt-6 ${backLinkClasses}`}>
        Back to Results
      </Link>
    </>
  )
}

function Roster({ roster }: { roster: CourseResultRoster }) {
  return (
    <>
      <title>{`${roster.courseName} · Results · Stacc`}</title>
      <Breadcrumb parent={RESULTS} current={roster.courseName} />
      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">{roster.courseCode}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">{roster.courseName}</h1>
        <p className="mt-2 text-sm leading-6 break-words text-ink-muted">
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
        </p>
      </header>

      <section aria-labelledby="course-results-heading" className="mt-8 max-w-4xl">
        <h2 id="course-results-heading" className="text-base font-semibold text-ink">
          Students
        </h2>
        <p className="mt-1 mb-3 text-sm leading-6 text-ink-muted">
          The official enrollments of this course offering, in student ID order. Saving keeps a result as a draft;
          students see a result only once it is published.
        </p>
        {roster.students.length === 0 ? (
          <StatePanel>
            <EmptyState title="No students enrolled." description="Official enrollments of this course offering will appear here." />
          </StatePanel>
        ) : (
          <ul aria-label="Students" className="divide-y divide-line rounded-panel border border-line bg-surface">
            {roster.students.map((student) => (
              <li key={student.courseEnrollmentId}>
                <ResultRow courseOfferingId={roster.courseOfferingId} student={student} />
              </li>
            ))}
          </ul>
        )}
        <Link to={RESULTS.to} className={`mt-6 ${backLinkClasses}`}>
          Back to Results
        </Link>
      </section>
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
