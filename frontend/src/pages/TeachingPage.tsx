import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { TeachingCourseSummary } from '../teaching/TeachingCourseSummary'
import { useMyTeachingCourses } from '../teaching/useMyTeachingCourses'

const NOT_FACULTY = 'This page is for faculty accounts.'
const NO_FACULTY_ACCESS = 'You don’t have access to faculty courses.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'
// A token issued before the account got the permission does not carry it until the next sign-in.
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

function MyTeachingCourses() {
  const courses = useMyTeachingCourses()

  if (courses.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading your teaching courses…" />
      </StatePanel>
    )
  }

  if (courses.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title={NO_FACULTY_ACCESS} message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  }

  if (courses.status === 'failed' || courses.status === 'notFound') {
    return (
      <StatePanel>
        <ErrorState
          title="Couldn’t load your teaching courses"
          message="Check your connection and try again."
          onRetry={courses.retry}
        />
      </StatePanel>
    )
  }

  if (courses.data.length === 0) {
    return (
      <StatePanel>
        <EmptyState
          title="No teaching courses available"
          description="Courses assigned to you will appear here when a teaching assignment and LMS course are available."
        />
      </StatePanel>
    )
  }

  return (
    <ul aria-label="Your teaching courses" className="grid gap-3 lg:grid-cols-2">
      {courses.data.map((course) => (
        <li key={course.lmsCourseId}>
          <TeachingCourseSummary course={course} />
        </li>
      ))}
    </ul>
  )
}

/**
 * The LMS courses the signed-in faculty member teaches. Accounts without the faculty role see an
 * explanation instead and no request is made. That check is for convenience only: the backend
 * decides which courses, if any, a faculty member may see.
 */
export function TeachingPage() {
  const { account } = useAuth()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])

  return (
    <>
      <title>Teaching · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Teaching</h1>
      {isFaculty && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Your assigned LMS courses.
        </p>
      )}

      <div className="mt-6">
        {isFaculty ? (
          <MyTeachingCourses />
        ) : (
          <StatePanel>
            <AccessDeniedState title={NOT_FACULTY} message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
