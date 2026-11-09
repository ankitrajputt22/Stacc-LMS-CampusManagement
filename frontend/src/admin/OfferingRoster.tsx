import { getCourseOfferingEnrollments } from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { creditsText } from '../courses/credits'
import type { RosterEntry } from '../types/admin'
import { Card, DataTable } from '../ui'
import { ENROLLMENT_DENIED, semesterContext } from './enrollmentText'
import { MasterStatus } from './MasterParts'
import { SIGN_IN_AGAIN } from './MasterSection'
import { statusWord } from './masterStatus'

/**
 * The roster of one course offering, read-only: the offering for context, then each student's login ID and
 * enrollment status as the admin API sends them. No names or other account details are shown, and nothing can be
 * changed here.
 */
export function OfferingRoster({ courseOfferingId }: { courseOfferingId: number | null }) {
  const request = useApiQuery(
    courseOfferingId === null ? null : queryKeys.admin.offeringRoster(courseOfferingId),
    (signal) => getCourseOfferingEnrollments(courseOfferingId ?? 0, signal),
  )
  const roster = request.status === 'loaded' ? request.data : null
  const offering = roster?.courseOffering
  const count = roster?.enrollments.length ?? 0

  return (
    <div>
      <div className="mb-4">
        <Breadcrumb
          parent={{ to: '/admin/academic/offerings', label: 'Course offerings' }}
          current={offering ? offering.courseCode : 'Roster'}
        />
      </div>
      <Card
        title={offering ? `${offering.courseCode} · ${offering.courseName}` : 'Offering roster'}
        description={
          offering
            ? `${semesterContext(offering)} · ${offering.programCode} · ${creditsText(offering.credits)} · ${statusWord(offering.status)}`
            : undefined
        }
        action={roster !== null && count > 0 ? <p className="text-sm text-ink-muted">{count === 1 ? '1 enrollment' : `${count} enrollments`}</p> : undefined}
        padding="none"
      >
        {request.status === 'loading' && <LoadingState label="Loading enrolled students…" />}
        {request.status === 'denied' && <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />}
        {request.status === 'notFound' && (
          <EmptyState title="Course offering not found." description="It may not exist. Choose an offering from the list." />
        )}
        {request.status === 'failed' && (
          <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
        )}
        {roster !== null && (
          <DataTable<RosterEntry>
            caption="Enrolled students, by student ID"
            columns={[
              { key: 'student', header: 'Student ID', primary: true, cell: (entry) => <span className="break-all">{entry.studentLoginId}</span> },
              { key: 'status', header: 'Status', cell: (entry) => <MasterStatus status={entry.status} /> },
            ]}
            rows={roster.enrollments}
            rowKey={(entry) => entry.courseEnrollmentId}
            empty={<EmptyState title="No students enrolled in this offering." />}
          />
        )}
      </Card>
    </div>
  )
}
