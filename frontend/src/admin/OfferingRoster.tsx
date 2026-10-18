import { useMemo } from 'react'
import { Link } from 'react-router'
import { getCourseOfferingEnrollments } from '../api/adminEnrollmentApi'
import { useApiRequest } from '../api/useApiRequest'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText } from '../courses/credits'
import { ENROLLMENT_DENIED, semesterContext } from './enrollmentText'
import { MasterStatus } from './MasterList'
import { SIGN_IN_AGAIN } from './MasterSection'
import { statusWord } from './masterStatus'

/**
 * The roster of one course offering, read-only: the offering for context, then each student's login ID and
 * enrollment status as the admin API sends them. No names or other account details are shown, and nothing can be
 * changed here.
 */
export function OfferingRoster({ courseOfferingId }: { courseOfferingId: number | null }) {
  const load = useMemo(
    () => (courseOfferingId === null ? null : (signal: AbortSignal) => getCourseOfferingEnrollments(courseOfferingId, signal)),
    [courseOfferingId],
  )
  const request = useApiRequest(load)
  const roster = request.status === 'loaded' ? request.data : null
  const offering = roster?.courseOffering

  return (
    <section aria-labelledby="roster-heading" className="mt-6">
      <Link
        to="/admin/academic/offerings"
        className="inline-flex min-h-11 items-center text-sm font-medium text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8"
      >
        <span aria-hidden="true">←&nbsp;</span>Back to course offerings
      </Link>
      <h2 id="roster-heading" className="mt-2 text-lg font-semibold break-words text-ink">
        {offering ? `${offering.courseCode} · ${offering.courseName}` : 'Offering roster'}
      </h2>
      {offering && (
        <p className="mt-1 text-sm leading-6 text-ink-muted">
          {semesterContext(offering)} · {offering.programCode} · {creditsText(offering.credits)} ·{' '}
          {statusWord(offering.status)}
        </p>
      )}

      <div className="mt-4">
        {request.status === 'loading' && (
          <StatePanel>
            <LoadingState label="Loading enrolled students…" />
          </StatePanel>
        )}
        {request.status === 'denied' && (
          <StatePanel>
            <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />
          </StatePanel>
        )}
        {request.status === 'notFound' && (
          <StatePanel>
            <EmptyState title="Course offering not found." description="It may not exist. Choose an offering from the list." />
          </StatePanel>
        )}
        {request.status === 'failed' && (
          <StatePanel>
            <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
          </StatePanel>
        )}
        {roster !== null && roster.enrollments.length === 0 && (
          <StatePanel>
            <EmptyState title="No students enrolled in this offering." />
          </StatePanel>
        )}
        {roster !== null && roster.enrollments.length > 0 && (
          <>
            <p className="mb-2 text-sm text-ink-muted">
              {roster.enrollments.length === 1 ? '1 enrollment' : `${roster.enrollments.length} enrollments`}, by student ID.
            </p>
            <ul aria-label="Enrolled students" className="divide-y divide-line rounded-panel border border-line bg-surface">
              {roster.enrollments.map((entry) => (
                <li key={entry.courseEnrollmentId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                  <span className="text-sm font-medium break-all text-ink">{entry.studentLoginId}</span>
                  <MasterStatus status={entry.status} />
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  )
}
