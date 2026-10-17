import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { CourseFacts } from '../courses/CourseFacts'
import { ASK_THE_COLLEGE, RESULTS_DENIED, SIGN_IN_AGAIN } from '../results/resultText'
import { useMyResultCourses } from '../results/useResults'
import type { ResultCourseOffering } from '../types/results'

function CourseOfferingCard({ course }: { course: ResultCourseOffering }) {
  return (
    <article className="group relative h-full rounded-panel border border-line bg-surface px-4 py-3.5 hover:border-slate-400 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5 sm:py-4">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
      <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">{course.courseName}</h2>
      <CourseFacts course={course} />
      {/* The route uses the course offering ID from the API. The click area covers the whole card. */}
      <Link
        to={`/results/${course.courseOfferingId}`}
        className="mt-2 inline-flex text-sm font-medium text-stacc-primary after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:underline group-hover:underline-offset-4"
      >
        Manage results<span className="sr-only"> for {course.courseName}</span>
      </Link>
    </article>
  )
}

function MyResultCourses() {
  const courses = useMyResultCourses()

  switch (courses.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading result courses…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={RESULTS_DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'failed':
    case 'notFound':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load results." message="Check your connection and try again." onRetry={courses.retry} />
        </StatePanel>
      )
    case 'loaded':
      if (courses.data.length === 0) {
        return (
          <StatePanel>
            <EmptyState
              title="No teaching courses available."
              description="Course offerings you are assigned to teach will appear here."
            />
          </StatePanel>
        )
      }
      return (
        <ul aria-label="Your teaching course offerings" className="grid gap-3 lg:grid-cols-2">
          {courses.data.map((course) => (
            <li key={course.courseOfferingId}>
              <CourseOfferingCard course={course} />
            </li>
          ))}
        </ul>
      )
  }
}

/**
 * The course offerings the signed-in faculty member teaches, to record official results for. Results are academic
 * data, so an offering is listed whether or not it has an LMS course. Accounts without the faculty role see an
 * explanation instead and no request is made; the backend decides everything else.
 */
export function ResultsPage() {
  const { account } = useAuth()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])

  return (
    <>
      <title>Results · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Results</h1>
      {isFaculty && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Record and publish official course results for the course offerings you teach.
        </p>
      )}
      <div className="mt-6">
        {isFaculty ? (
          <MyResultCourses />
        ) : (
          <StatePanel>
            <AccessDeniedState title="This page is for faculty accounts." message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
