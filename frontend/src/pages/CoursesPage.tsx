import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { CourseSummary } from '../courses/CourseSummary'
import { useMyCourses } from '../courses/useMyCourses'

const NOT_A_STUDENT = 'Courses are for student accounts.'
const NO_STUDENT_ACCESS = 'You don’t have access to student courses.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

/** The page states share one quiet frame, so the page does not jump between them. */
function StatePanel({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-panel border border-line bg-surface">{children}</div>
  )
}

function MyCourses() {
  const courses = useMyCourses()

  if (courses.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading your courses…" />
      </StatePanel>
    )
  }

  if (courses.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title={NO_STUDENT_ACCESS} message={ASK_THE_COLLEGE} />
      </StatePanel>
    )
  }

  if (courses.status === 'failed') {
    return (
      <StatePanel>
        <ErrorState
          title="Couldn’t load your courses"
          message="Check your connection and try again."
          onRetry={courses.retry}
        />
      </StatePanel>
    )
  }

  if (courses.courses.length === 0) {
    return (
      <StatePanel>
        <EmptyState
          title="No courses available"
          description="Your current LMS courses will appear here when your academic enrollment and LMS access are available."
        />
      </StatePanel>
    )
  }

  return (
    <ul aria-label="Your courses" className="grid gap-3 lg:grid-cols-2">
      {courses.courses.map((course) => (
        <li key={course.lmsCourseId}>
          <CourseSummary course={course} />
        </li>
      ))}
    </ul>
  )
}

/**
 * The signed-in student's current LMS courses. Accounts without the student role see an
 * explanation instead and no request is made. That check is for convenience only: the backend
 * decides which courses, if any, a student may see.
 */
export function CoursesPage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])

  return (
    <>
      <title>Courses · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Courses</h1>
      {isStudent && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Your current LMS courses.
        </p>
      )}

      <div className="mt-6">
        {isStudent ? (
          <MyCourses />
        ) : (
          <StatePanel>
            <AccessDeniedState title={NOT_A_STUDENT} message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
