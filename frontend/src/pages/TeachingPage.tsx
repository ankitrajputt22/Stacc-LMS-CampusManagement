import { useState } from 'react'
import { useLocation } from 'react-router'
import { getManagedTeachingCourses } from '../api/lmsCourseManagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { groupByTerm } from '../courses/terms'
import { ManagedCourseCard } from '../teaching/ManagedCourseCard'
import type { LmsManagedCourse } from '../types/lms'
import { PageHeader } from '../ui'

const NOT_FACULTY = 'This page is for faculty accounts.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

type LmsState = Pick<LmsManagedCourse, 'lmsCourseId' | 'lmsCourseStatus'>

function ManagedCourses() {
  const courses = useApiQuery(queryKeys.lms.managedCourses(), getManagedTeachingCourses)
  const [changes, setChanges] = useState<Record<number, LmsState>>({})

  if (courses.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading teaching courses…" />
      </StatePanel>
    )
  }

  if (courses.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title="You don’t have access to manage teaching courses." message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  }

  if (courses.status === 'failed' || courses.status === 'notFound') {
    return (
      <StatePanel>
        <ErrorState title="Couldn’t load teaching courses." message="Check your connection and try again." onRetry={courses.retry} />
      </StatePanel>
    )
  }

  if (courses.data.length === 0) {
    return (
      <StatePanel>
        <EmptyState title="No teaching courses available." />
      </StatePanel>
    )
  }

  return (
    <div className="space-y-8">
      {groupByTerm(courses.data).map((term) => (
        <section key={term.key} aria-labelledby={`term-${term.key}`} className="rounded-panel border border-line bg-surface p-4 sm:p-5">
          <h2 id={`term-${term.key}`} className="flex items-center gap-3 text-2xl font-bold text-ink">
            <span aria-hidden="true" className="h-8 w-1.5 rounded bg-teal-600" />
            <span>
              {term.heading}
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, program </span>
              {term.programCode}
            </span>
          </h2>
          <ul aria-label={`Teaching courses of ${term.heading}`} className="mt-4 space-y-3">
            {term.items.map((course) => (
              <li key={course.courseOfferingId}>
                <ManagedCourseCard
                  course={{ ...course, ...changes[course.courseOfferingId] }}
                  onChanged={(change) =>
                    setChanges((current) => ({
                      ...current,
                      [change.courseOfferingId]: { lmsCourseId: change.lmsCourseId, lmsCourseStatus: change.lmsCourseStatus },
                    }))
                  }
                />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

export function TeachingPage() {
  const { account } = useAuth()
  const location = useLocation()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const archivedCourse = (location.state as { archivedCourse?: string } | null)?.archivedCourse

  return (
    <>
      <title>Teaching · Stacc</title>
      <PageHeader
        title="My Courses"
        description={isFaculty ? 'Courses you teach, by term, with their LMS courses.' : undefined}
      />
      <p role="status" className={archivedCourse ? 'mt-4 rounded-control border border-line-strong bg-surface-muted px-3 py-2.5 text-sm leading-5 text-ink' : 'sr-only'}>
        {archivedCourse ? `${archivedCourse} was archived. Its content and history are kept.` : ''}
      </p>

      <div className="mt-4">
        {isFaculty ? (
          <ManagedCourses />
        ) : (
          <StatePanel>
            <AccessDeniedState title={NOT_FACULTY} message={ASK_THE_COLLEGE} />
          </StatePanel>
        )}
      </div>
    </>
  )
}
