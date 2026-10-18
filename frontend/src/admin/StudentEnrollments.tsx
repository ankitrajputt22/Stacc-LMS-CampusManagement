import { useEffect, useMemo, useRef, useState } from 'react'
import { getSemesters } from '../api/adminAcademicApi'
import {
  createSemesterEnrollment,
  getStudentSemesterEnrollments,
  updateSemesterEnrollmentStatus,
} from '../api/adminEnrollmentApi'
import { useApiRequest } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import type { AdminSemesterEnrollment, EnrollmentStatus } from '../types/admin'
import { CourseEnrollments } from './CourseEnrollments'
import { ENROLLMENT_DENIED, semesterContext, semesterLabel } from './enrollmentText'
import { MasterForm } from './MasterForm'
import { MasterStatus } from './MasterList'
import { SIGN_IN_AGAIN } from './MasterSection'
import { StatusConfirmation } from './StatusConfirmation'
import { usePendingFocus } from './usePendingFocus'

type Ending = { semesterEnrollmentId: number; status: Exclude<EnrollmentStatus, 'ENROLLED'> }

type StudentEnrollmentsProps = {
  studentProfileId: number
  onBack: () => void
}

/**
 * One student's official enrollments: a short summary, then every semester enrollment, newest first. An enrolled
 * semester enrollment can have its courses managed, or be completed or withdrawn after a confirmation, which the
 * backend refuses while a course is still enrolled. An ended one is read-only. The student can be enrolled in another
 * semester of their own program. Nothing about the student's account or profile can be changed here.
 */
export function StudentEnrollments({ studentProfileId, onBack }: StudentEnrollmentsProps) {
  const sectionRef = useRef<HTMLDivElement>(null)
  const load = useMemo(() => (signal: AbortSignal) => getStudentSemesterEnrollments(studentProfileId, signal), [studentProfileId])
  const request = useApiRequest(load)
  const semesters = useApiRequest(getSemesters)
  const [adding, setAdding] = useState(false)
  const [ending, setEnding] = useState<Ending | null>(null)
  const [coursesFor, setCoursesFor] = useState<number | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const data = request.status === 'loaded' ? request.data : null
  const focusLater = usePendingFocus(sectionRef)
  const student = data?.student ?? null
  const rows = data?.semesterEnrollments ?? null
  const managed = rows?.find((row) => row.semesterEnrollmentId === coursesFor) ?? null

  const enrolledSemesters = new Set((rows ?? []).map((row) => row.semesterId))
  const choices =
    semesters.status === 'loaded' && student !== null
      ? semesters.data.filter((semester) => semester.programId === student.programId && !enrolledSemesters.has(semester.id))
      : null
  const blocked =
    student?.studentProfileStatus === 'INACTIVE'
      ? 'This student profile is inactive, so it cannot be enrolled in a semester.'
      : semesters.status === 'denied' || semesters.status === 'failed' || semesters.status === 'notFound'
        ? 'Couldn’t load semesters.'
        : choices === null
          ? 'Loading semesters…'
          : choices.length === 0
            ? `No other semesters of ${student?.programCode ?? 'this program'} to enroll in.`
            : null

  // Once the student has loaded, focus moves to their name, because the button that chose them is gone.
  useEffect(() => {
    focusLater('student')
  }, [focusLater])

  // As with the masters, the empty state's button has its own key, so focus after the first enrollment waits for the
  // button above the list.
  const enrollButton = (focusKey: string) => (
    <Button
      variant="secondary"
      data-focus-key={focusKey}
      aria-disabled={blocked ? true : undefined}
      aria-describedby={blocked ? 'enroll-semester-blocked' : undefined}
      onClick={() => {
        if (!blocked) {
          setAnnouncement('')
          setAdding(true)
        }
      }}
    >
      Enroll in semester
    </Button>
  )

  function endingLabel(status: Ending['status']) {
    return status === 'COMPLETED' ? 'completed' : 'withdrawn'
  }

  return (
    <div ref={sectionRef}>
      <ActionButton onClick={onBack} className="mt-4">
        <span aria-hidden="true">←&nbsp;</span>Choose another student
      </ActionButton>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {request.status === 'loading' && (
        <div className="mt-4">
          <StatePanel>
            <LoadingState label="Loading enrollments…" />
          </StatePanel>
        </div>
      )}
      {request.status === 'denied' && (
        <div className="mt-4">
          <StatePanel>
            <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />
          </StatePanel>
        </div>
      )}
      {request.status === 'notFound' && (
        <div className="mt-4">
          <StatePanel>
            <EmptyState title="Student not found." description="Search again and choose a student from the results." />
          </StatePanel>
        </div>
      )}
      {request.status === 'failed' && (
        <div className="mt-4">
          <StatePanel>
            <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
          </StatePanel>
        </div>
      )}

      {student !== null && rows !== null && (
        <>
          <section aria-labelledby="student-heading" className="mt-4 rounded-panel border border-line bg-surface px-4 py-3">
            <h3 id="student-heading" tabIndex={-1} data-focus-key="student" className="text-base font-semibold break-all text-ink">
              Student {student.loginId}
            </h3>
            <dl className="mt-2 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-xs text-ink-muted">Program</dt>
                <dd className="break-words text-ink">
                  {student.programCode} · {student.programName}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-ink-muted">Admission session</dt>
                <dd className="text-ink">{student.admissionSessionCode}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-muted">Student profile</dt>
                <dd>
                  <MasterStatus status={student.studentProfileStatus} />
                </dd>
              </div>
            </dl>
          </section>

          <section aria-labelledby="semester-enrollments-heading" className="mt-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 id="semester-enrollments-heading" className="text-base font-semibold text-ink">
                Semester enrollments
              </h3>
              {rows.length > 0 && !adding && <div className="w-full sm:w-auto">{enrollButton('enroll-semester')}</div>}
            </div>
            {blocked && (
              <p id="enroll-semester-blocked" className="mt-1 text-sm text-ink-muted">
                {blocked}
              </p>
            )}

            {adding && choices !== null && (
              <div className="mt-3">
                <MasterForm
                  heading={`Enroll ${student.loginId} in a semester`}
                  note={`Only semesters of ${student.programCode} are listed. The enrollment starts as enrolled and gives no course access by itself.`}
                  deniedMessage={ENROLLMENT_DENIED}
                  fields={[
                    { name: 'semesterId', label: 'Semester', kind: 'select', choose: 'Choose a semester',
                      options: choices.map((semester) => ({
                        value: String(semester.id),
                        label: `${semesterLabel(semester, false)}${semester.status === 'CLOSED' ? ' (closed)' : ''}`,
                      })) },
                  ]}
                  initial={{ semesterId: '' }}
                  submitLabel="Enroll in semester"
                  onSubmit={async (values) => {
                    const created = await createSemesterEnrollment(studentProfileId, Number(values.semesterId))
                    setAdding(false)
                    setAnnouncement(`${student.loginId} enrolled in ${semesterContext(created)}.`)
                    request.refresh()
                    focusLater('enroll-semester')
                  }}
                  onCancel={() => {
                    setAdding(false)
                    focusLater(rows.length === 0 ? 'enroll-semester-empty' : 'enroll-semester')
                  }}
                />
              </div>
            )}

            <div className="mt-4">
              {rows.length === 0 && !adding && (
                <StatePanel>
                  <EmptyState title="No semester enrollments yet." action={blocked ? undefined : enrollButton('enroll-semester-empty')} />
                </StatePanel>
              )}
              {rows.length > 0 && (
                <ul aria-label="Semester enrollments" className="divide-y divide-line rounded-panel border border-line bg-surface">
                  {rows.map((row) => (
                    <SemesterEnrollmentRow
                      key={row.semesterEnrollmentId}
                      row={row}
                      expanded={coursesFor === row.semesterEnrollmentId}
                      ending={ending?.semesterEnrollmentId === row.semesterEnrollmentId ? ending.status : null}
                      onToggleCourses={() => setCoursesFor((current) => (current === row.semesterEnrollmentId ? null : row.semesterEnrollmentId))}
                      onEnd={(status) => setEnding({ semesterEnrollmentId: row.semesterEnrollmentId, status })}
                      onCancelEnd={(status) => {
                        setEnding(null)
                        focusLater(`${status === 'COMPLETED' ? 'complete' : 'withdraw'}-semester-${row.semesterEnrollmentId}`)
                      }}
                      onConfirmEnd={async (status) => {
                        await updateSemesterEnrollmentStatus(row.semesterEnrollmentId, status)
                        setEnding(null)
                        setAnnouncement(`${semesterContext(row)} marked ${endingLabel(status)}.`)
                        request.refresh()
                        focusLater(`semester-${row.semesterEnrollmentId}`)
                      }}
                    />
                  ))}
                </ul>
              )}
            </div>
          </section>

          {managed !== null && <CourseEnrollments key={managed.semesterEnrollmentId} semesterEnrollment={managed} />}
        </>
      )}
    </div>
  )
}

type SemesterEnrollmentRowProps = {
  row: AdminSemesterEnrollment
  expanded: boolean
  ending: Ending['status'] | null
  onToggleCourses: () => void
  onEnd: (status: Ending['status']) => void
  onCancelEnd: (status: Ending['status']) => void
  onConfirmEnd: (status: Ending['status']) => Promise<void>
}

function SemesterEnrollmentRow({ row, expanded, ending, onToggleCourses, onEnd, onCancelEnd, onConfirmEnd }: SemesterEnrollmentRowProps) {
  const context = semesterContext(row)
  const enrolled = row.status === 'ENROLLED'
  return (
    <li className="px-4 py-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p tabIndex={-1} data-focus-key={`semester-${row.semesterEnrollmentId}`} className="text-sm font-medium text-ink">
            Semester {row.semesterNumber}
          </p>
          <p className="mt-0.5 text-sm text-ink-muted">
            {row.academicSessionCode} · {row.programCode}
          </p>
          <p className="mt-1">
            <MasterStatus status={row.status} />
          </p>
        </div>
        {ending === null && (
          <div className="flex flex-wrap gap-2">
            <ActionButton aria-expanded={expanded} aria-controls={expanded ? 'course-enrollments-panel' : undefined} onClick={onToggleCourses}>
              {enrolled ? 'Manage courses' : 'View courses'}
              <span className="sr-only"> for {context}</span>
            </ActionButton>
            {enrolled && (
              <>
                <ActionButton focusKey={`complete-semester-${row.semesterEnrollmentId}`} onClick={() => onEnd('COMPLETED')}>
                  Complete<span className="sr-only"> {context}</span>
                </ActionButton>
                <ActionButton focusKey={`withdraw-semester-${row.semesterEnrollmentId}`} onClick={() => onEnd('WITHDRAWN')}>
                  Withdraw<span className="sr-only"> from {context}</span>
                </ActionButton>
              </>
            )}
          </div>
        )}
      </div>
      {ending !== null && (
        <StatusConfirmation
          question={ending === 'COMPLETED' ? 'Complete semester enrollment?' : 'Withdraw from this semester?'}
          detail={`${context} will be marked ${ending === 'COMPLETED' ? 'completed' : 'withdrawn'}. This cannot be undone. Every course enrollment in it must be completed or withdrawn first; nothing changes them automatically.`}
          confirmLabel={ending === 'COMPLETED' ? 'Complete semester enrollment' : 'Withdraw from semester'}
          onConfirm={() => onConfirmEnd(ending)}
          onCancel={() => onCancelEnd(ending)}
        />
      )}
    </li>
  )
}
