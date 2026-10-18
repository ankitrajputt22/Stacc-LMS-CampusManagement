import { useState } from 'react'
import { useLocation } from 'react-router'
import { getManagedTeachingCourses } from '../api/lmsCourseManagementApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { ManagedCourseCard } from '../teaching/ManagedCourseCard'
import type { LmsManagedCourse } from '../types/lms'

const NOT_FACULTY = 'This page is for faculty accounts.'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'
// A token issued before the account got the permission does not carry it until the next sign-in.
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

type LmsState = Pick<LmsManagedCourse, 'lmsCourseId' | 'lmsCourseStatus'>

function ManagedCourses() {
  const courses = useApiRequest(getManagedTeachingCourses)
  // Changes made on this page, shown at once without asking the backend again.
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
    <ul aria-label="Your teaching courses" className="grid gap-3 lg:grid-cols-2">
      {courses.data.map((course) => (
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
  )
}

/**
 * Every course offering the signed-in faculty member officially teaches, with its LMS course: create a missing one,
 * open or publish a draft, open a published one, or see that one is archived. Accounts without the faculty role see an
 * explanation instead and no request is made. That check is for convenience only: the backend decides which
 * offerings, if any, a faculty member manages.
 */
export function TeachingPage() {
  const { account } = useAuth()
  const location = useLocation()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  // Set by the course page after it archived a course, which it can then no longer show.
  const archivedCourse = (location.state as { archivedCourse?: string } | null)?.archivedCourse

  return (
    <>
      <title>Teaching · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Teaching</h1>
      {isFaculty && (
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          The course offerings you are assigned to teach, with their LMS courses.
        </p>
      )}
      <p role="status" className={archivedCourse ? 'mt-4 rounded-control border border-line-strong bg-surface-muted px-3 py-2.5 text-sm leading-5 text-ink' : 'sr-only'}>
        {archivedCourse ? `${archivedCourse} was archived. Its content and history are kept.` : ''}
      </p>

      <div className="mt-6">
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
