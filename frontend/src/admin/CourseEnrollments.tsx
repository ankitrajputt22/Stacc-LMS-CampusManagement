import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import {
  createCourseEnrollment,
  getCourseOfferings,
  getSemesterCourseEnrollments,
  updateCourseEnrollmentStatus,
} from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { usePendingFocus } from '../components/usePendingFocus'
import { creditsText } from '../courses/credits'
import type { AdminCourseEnrollment, AdminSemesterEnrollment, EnrollmentStatus } from '../types/admin'
import { Button, Card } from '../ui'
import { ENROLLMENT_DENIED, offeringLabel, refusalText, semesterContext } from './enrollmentText'
import { MasterForm } from './MasterForm'
import { CodeName, MasterStatus } from './MasterParts'
import { SIGN_IN_AGAIN } from './MasterSection'

type Ending = { courseEnrollmentId: number; status: Exclude<EnrollmentStatus, 'ENROLLED' | 'DROPPED'> }

/**
 * The course enrollments of one semester enrollment. While it is enrolled, a course can be added from the offerings
 * of the same semester, which the backend filters, and each enrolled course can be completed or withdrawn after a
 * confirmation. An ended semester enrollment's courses are read-only. LMS access follows the official enrollment
 * live, whenever the offering's LMS course is published, so there is nothing to provision here.
 */
export function CourseEnrollments({ semesterEnrollment }: { semesterEnrollment: AdminSemesterEnrollment }) {
  const queryClient = useQueryClient()
  const sectionRef = useRef<HTMLDivElement>(null)
  const semesterEnrollmentId = semesterEnrollment.semesterEnrollmentId
  const editable = semesterEnrollment.status === 'ENROLLED'
  const request = useApiQuery(queryKeys.admin.courseEnrollments(semesterEnrollmentId), (signal) =>
    getSemesterCourseEnrollments(semesterEnrollmentId, signal),
  )
  const offeringFilters = { semesterId: semesterEnrollment.semesterId }
  const offerings = useApiQuery(editable ? queryKeys.admin.offerings(offeringFilters) : null, (signal) =>
    getCourseOfferings(offeringFilters, signal),
  )
  const [adding, setAdding] = useState(false)
  const [ending, setEnding] = useState<Ending | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const rows = request.status === 'loaded' ? request.data.courseEnrollments : null
  const focusLater = usePendingFocus(sectionRef)
  const context = semesterContext(semesterEnrollment)

  const taken = new Set((rows ?? []).map((row) => row.courseOfferingId))
  // Whole-semester offerings, and those of the student's own section; another section's offerings are not theirs.
  const choices =
    offerings.status === 'loaded'
      ? offerings.data.filter(
          (offering) =>
            !taken.has(offering.courseOfferingId) &&
            (offering.sectionId === null || offering.sectionId === semesterEnrollment.sectionId),
        )
      : null
  const blocked =
    offerings.status === 'denied'
      ? ENROLLMENT_DENIED
      : offerings.status === 'failed' || offerings.status === 'notFound'
        ? 'Couldn’t load this semester’s course offerings.'
        : choices === null
          ? 'Loading course offerings…'
          : offerings.status === 'loaded' && offerings.data.length === 0
            ? 'No course offerings in this semester yet. Add one under Course Offerings.'
            : choices.length === 0
              ? semesterEnrollment.sectionId === null
              ? 'Every course offering of this semester open to the student is added. Place the student in a section to see that section’s offerings.'
              : 'Every course offering of this semester open to the student is already added.'
              : null

  function changed(message: string) {
    setAnnouncement(message)
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.academic })
  }

  function endingFor(row: AdminCourseEnrollment) {
    return ending?.courseEnrollmentId === row.courseEnrollmentId ? ending : null
  }

  // The empty state's button has its own key, so focus after the first course waits for the button above the list.
  const addButton = (focusKey: string) => (
    <Button
      data-focus-key={focusKey}
      aria-disabled={blocked ? true : undefined}
      aria-describedby={blocked ? 'add-course-blocked' : undefined}
      onClick={() => {
        if (!blocked) {
          setAnnouncement('')
          setAdding(true)
        }
      }}
    >
      Add course
    </Button>
  )

  return (
    <div ref={sectionRef} id="course-enrollments-panel" className="space-y-4">
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {adding && choices !== null && (
          <MasterForm
            heading={`Add a course in ${context}`}
            note="Only this semester’s course offerings are listed. The course enrollment starts as enrolled."
            deniedMessage={ENROLLMENT_DENIED}
            fields={[
              { name: 'courseOfferingId', label: 'Course offering', kind: 'select', choose: 'Choose a course offering',
                options: choices.map((offering) => ({ value: String(offering.courseOfferingId), label: offeringLabel(offering) })) },
            ]}
            initial={{ courseOfferingId: '' }}
            submitLabel="Add course"
            onSubmit={async (values) => {
              const course = await createCourseEnrollment(semesterEnrollmentId, Number(values.courseOfferingId))
              setAdding(false)
              changed(`${course.courseCode} added to ${context}.`)
              focusLater('add-course')
            }}
            onCancel={() => {
              setAdding(false)
              focusLater(rows?.length === 0 ? 'add-course-empty' : 'add-course')
            }}
          />
      )}

      <Card
        title={<span tabIndex={-1} data-focus-key="course-enrollments" className="outline-none">Courses in {context}</span>}
        description={
          editable ? (
            rows !== null && blocked ? <span id="add-course-blocked">{blocked}</span> : undefined
          ) : (
            'This semester enrollment has ended, so its courses are read-only.'
          )
        }
        action={editable && rows !== null && rows.length > 0 && !adding ? addButton('add-course') : undefined}
        padding="none"
      >
        {request.status === 'loading' && <LoadingState label="Loading course enrollments…" />}
        {request.status === 'denied' && <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />}
        {(request.status === 'failed' || request.status === 'notFound') && (
          <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
        )}
        {rows !== null && rows.length === 0 && !adding && (
          <EmptyState title="No course enrollments yet." action={editable && !blocked ? addButton('add-course-empty') : undefined} />
        )}
        {rows !== null && rows.length > 0 && (
          <ul aria-label={`Course enrollments in ${context}`} className="divide-y divide-line">
            {rows.map((row) => {
              const confirming = endingFor(row)
              const name = `${row.courseCode} in ${context}`
              return (
                <li key={row.courseEnrollmentId} className="px-4 py-3 sm:px-5">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <p tabIndex={-1} data-focus-key={`course-${row.courseEnrollmentId}`} className="text-sm break-words text-ink">
                        <CodeName code={row.courseCode} name={row.courseName} />
                      </p>
                      <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
                        <span>{creditsText(row.credits)}</span>
                        <MasterStatus status={row.status} />
                      </p>
                    </div>
                    {editable && row.status === 'ENROLLED' && confirming === null && (
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          data-focus-key={`complete-course-${row.courseEnrollmentId}`}
                          onClick={() => setEnding({ courseEnrollmentId: row.courseEnrollmentId, status: 'COMPLETED' })}
                        >
                          Complete<span className="sr-only"> {name}</span>
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          data-focus-key={`withdraw-course-${row.courseEnrollmentId}`}
                          onClick={() => setEnding({ courseEnrollmentId: row.courseEnrollmentId, status: 'WITHDRAWN' })}
                        >
                          Withdraw<span className="sr-only"> from {name}</span>
                        </Button>
                      </div>
                    )}
                  </div>
                  {confirming !== null && (
                    <StatusConfirmation
                      question={confirming.status === 'COMPLETED' ? 'Complete this course enrollment?' : 'Withdraw from this course?'}
                      detail={`${row.courseCode} in ${context} will be marked ${
                        confirming.status === 'COMPLETED' ? 'completed' : 'withdrawn'
                      }. This cannot be undone, and the student loses LMS access to the course. Nothing is deleted.`}
                      confirmLabel={confirming.status === 'COMPLETED' ? 'Complete course enrollment' : 'Withdraw from course'}
                      onConfirm={async () => {
                        await updateCourseEnrollmentStatus(row.courseEnrollmentId, confirming.status)
                        setEnding(null)
                        changed(`${name} marked ${confirming.status === 'COMPLETED' ? 'completed' : 'withdrawn'}.`)
                        focusLater(`course-${row.courseEnrollmentId}`)
                      }}
                      onCancel={() => {
                        setEnding(null)
                        focusLater(`${confirming.status === 'COMPLETED' ? 'complete' : 'withdraw'}-course-${row.courseEnrollmentId}`)
                      }}
                      describeRefusal={refusalText}
                    />
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </Card>
    </div>
  )
}
