import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { CourseSummary } from '../courses/CourseSummary'
import { groupByTerm } from '../courses/terms'
import { useMyCourses } from '../courses/useMyCourses'
import { PageHeader } from '../ui'

const NOT_A_STUDENT = 'Courses are for student accounts.'
const NO_STUDENT_ACCESS = 'You don’t have access to student courses.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

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

  if (courses.status === 'failed' || courses.status === 'notFound') {
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

  if (courses.data.length === 0) {
    return (
      <StatePanel>
        <EmptyState
          title="No courses available"
          description="Your courses appear here once you are enrolled and your teachers publish them."
        />
      </StatePanel>
    )
  }

  return (
    <div className="space-y-8">
      {groupByTerm(courses.data).map((term) => (
        <section key={term.key} aria-labelledby={`term-${term.key}`}>
          <h2 id={`term-${term.key}`} className="text-lg font-semibold text-ink">
            {term.heading}
            <span className="ml-2 text-sm font-normal text-ink-muted">
              <span className="sr-only">, program </span>
              {term.programCode}
            </span>
          </h2>
          <ul aria-label={`Courses of ${term.heading}`} className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {term.items.map((course) => (
              <li key={course.lmsCourseId}>
                <CourseSummary course={course} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
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
      <PageHeader
        eyebrow="LMS"
        title="Courses"
        description={isStudent ? 'Your current courses, by term. You get each course while you are officially enrolled in it.' : undefined}
      />

      <div>
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
