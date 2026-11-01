import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { getSections, getSemesters } from '../api/adminAcademicApi'
import {
  changeSemesterEnrollmentSection,
  createSemesterEnrollment,
  getStudentSemesterEnrollments,
  updateSemesterEnrollmentStatus,
} from '../api/adminEnrollmentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { usePendingFocus } from '../components/usePendingFocus'
import type { AdminSemesterEnrollment, EnrollmentStatus } from '../types/admin'
import { Button, Card } from '../ui'
import { CourseEnrollments } from './CourseEnrollments'
import { ENROLLMENT_DENIED, refusalText, semesterContext, semesterLabel } from './enrollmentText'
import { MasterForm } from './MasterForm'
import { MasterStatus } from './MasterParts'
import { SIGN_IN_AGAIN } from './MasterSection'

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
  const queryClient = useQueryClient()
  const sectionRef = useRef<HTMLDivElement>(null)
  const request = useApiQuery(queryKeys.admin.studentEnrollments(studentProfileId), (signal) =>
    getStudentSemesterEnrollments(studentProfileId, signal),
  )
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const [adding, setAdding] = useState(false)
  const [ending, setEnding] = useState<Ending | null>(null)
  const [coursesFor, setCoursesFor] = useState<number | null>(null)
  const [placing, setPlacing] = useState<AdminSemesterEnrollment | null>(null)
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

  function changed(message: string) {
    setAnnouncement(message)
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.academic })
  }

  // As with the masters, the empty state's button has its own key, so focus after the first enrollment waits for the
  // button above the list.
  const enrollButton = (focusKey: string) => (
    <Button
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

  return (
    <div ref={sectionRef} className="space-y-4">
      <Button variant="ghost" size="sm" onClick={onBack}>
        <span aria-hidden="true">←</span> Choose another student
      </Button>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {request.status === 'loading' && (
        <StatePanel>
          <LoadingState label="Loading enrollments…" />
        </StatePanel>
      )}
      {request.status === 'denied' && (
        <StatePanel>
          <AccessDeniedState title={ENROLLMENT_DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )}
      {request.status === 'notFound' && (
        <StatePanel>
          <EmptyState title="Student not found." description="Search again and choose a student from the results." />
        </StatePanel>
      )}
      {request.status === 'failed' && (
        <StatePanel>
          <ErrorState title="Couldn’t load enrollments." message="Check your connection and try again." onRetry={request.retry} />
        </StatePanel>
      )}

      {student !== null && rows !== null && (
        <>
          <section aria-labelledby="student-heading" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
            <h2 id="student-heading" tabIndex={-1} data-focus-key="student" className="text-base font-semibold break-all text-ink outline-none">
              Student {student.loginId}
            </h2>
            <dl className="mt-2 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-ink-muted">Program</dt>
                <dd className="break-words text-ink">
                  {student.programCode} · {student.programName}
                </dd>
              </div>
              <div>
                <dt className="text-ink-muted">Admission session</dt>
                <dd className="text-ink">{student.admissionSessionCode}</dd>
              </div>
              <div>
                <dt className="text-ink-muted">Student profile</dt>
                <dd>
                  <MasterStatus status={student.studentProfileStatus} />
                </dd>
              </div>
            </dl>
          </section>

          {placing !== null && (
            <SectionPlacementForm
              enrollment={placing}
              studentLoginId={student.loginId}
              onDone={(message) => {
                const id = placing.semesterEnrollmentId
                setPlacing(null)
                changed(message)
                focusLater(`section-${id}`)
              }}
              onCancel={() => {
                const id = placing.semesterEnrollmentId
                setPlacing(null)
                focusLater(`section-${id}`)
              }}
            />
          )}

          {adding && choices !== null && (
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
                changed(`${student.loginId} enrolled in ${semesterContext(created)}.`)
                focusLater('enroll-semester')
              }}
              onCancel={() => {
                setAdding(false)
                focusLater(rows.length === 0 ? 'enroll-semester-empty' : 'enroll-semester')
              }}
            />
          )}

          <Card
            title="Semester enrollments"
            description={
              blocked ? (
                <span id="enroll-semester-blocked">{blocked}</span>
              ) : (
                'Newest first. Complete or withdraw a semester only after its courses have ended.'
              )
            }
            action={rows.length > 0 && !adding ? enrollButton('enroll-semester') : undefined}
            padding="none"
          >
            {rows.length === 0 && !adding && (
              <EmptyState title="No semester enrollments yet." action={blocked ? undefined : enrollButton('enroll-semester-empty')} />
            )}
            {rows.length > 0 && (
              <ul aria-label="Semester enrollments" className="divide-y divide-line">
                {rows.map((row) => (
                  <SemesterEnrollmentRow
                    key={row.semesterEnrollmentId}
                    row={row}
                    expanded={coursesFor === row.semesterEnrollmentId}
                    ending={ending?.semesterEnrollmentId === row.semesterEnrollmentId ? ending.status : null}
                    onToggleCourses={() => setCoursesFor((current) => (current === row.semesterEnrollmentId ? null : row.semesterEnrollmentId))}
                    onChangeSection={() => {
                      setAnnouncement('')
                      setEnding(null)
                      setPlacing(row)
                    }}
                    onEnd={(status) => setEnding({ semesterEnrollmentId: row.semesterEnrollmentId, status })}
                    onCancelEnd={(status) => {
                      setEnding(null)
                      focusLater(`${status === 'COMPLETED' ? 'complete' : 'withdraw'}-semester-${row.semesterEnrollmentId}`)
                    }}
                    onConfirmEnd={async (status) => {
                      await updateSemesterEnrollmentStatus(row.semesterEnrollmentId, status)
                      setEnding(null)
                      changed(`${semesterContext(row)} marked ${status === 'COMPLETED' ? 'completed' : 'withdrawn'}.`)
                      focusLater(`semester-${row.semesterEnrollmentId}`)
                    }}
                  />
                ))}
              </ul>
            )}
          </Card>

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
  onChangeSection: () => void
  onEnd: (status: Ending['status']) => void
  onCancelEnd: (status: Ending['status']) => void
  onConfirmEnd: (status: Ending['status']) => Promise<void>
}

function SemesterEnrollmentRow({
  row,
  expanded,
  ending,
  onToggleCourses,
  onChangeSection,
  onEnd,
  onCancelEnd,
  onConfirmEnd,
}: SemesterEnrollmentRowProps) {
  const context = semesterContext(row)
  const enrolled = row.status === 'ENROLLED'
  return (
    <li className="px-4 py-3 sm:px-5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2">
            <span tabIndex={-1} data-focus-key={`semester-${row.semesterEnrollmentId}`} className="text-sm font-medium text-ink outline-none">
              Semester {row.semesterNumber}
            </span>
            <MasterStatus status={row.status} />
          </p>
          <p className="mt-0.5 text-sm text-ink-muted">
            {row.academicSessionCode} · {row.programCode} · {row.sectionCode ? `Section ${row.sectionCode}` : 'No section'}
          </p>
        </div>
        {ending === null && (
          <div className="flex flex-wrap gap-2">
            <Button
              variant={expanded ? 'primary' : 'secondary'}
              size="sm"
              aria-expanded={expanded}
              aria-controls={expanded ? 'course-enrollments-panel' : undefined}
              onClick={onToggleCourses}
            >
              {enrolled ? 'Manage courses' : 'View courses'}
              <span className="sr-only"> for {context}</span>
            </Button>
            {enrolled && (
              <>
                <Button variant="secondary" size="sm" data-focus-key={`section-${row.semesterEnrollmentId}`} onClick={onChangeSection}>
                  {row.sectionCode ? 'Change section' : 'Place in section'}
                  <span className="sr-only"> for {context}</span>
                </Button>
                <Button variant="secondary" size="sm" data-focus-key={`complete-semester-${row.semesterEnrollmentId}`} onClick={() => onEnd('COMPLETED')}>
                  Complete<span className="sr-only"> {context}</span>
                </Button>
                <Button variant="secondary" size="sm" data-focus-key={`withdraw-semester-${row.semesterEnrollmentId}`} onClick={() => onEnd('WITHDRAWN')}>
                  Withdraw<span className="sr-only"> from {context}</span>
                </Button>
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
          describeRefusal={refusalText}
        />
      )}
    </li>
  )
}

type SectionPlacementFormProps = {
  enrollment: AdminSemesterEnrollment
  studentLoginId: string
  onDone: (announcement: string) => void
  onCancel: () => void
}

/**
 * Chooses the student's section among the active sections of their semester, or none. A full section is marked as such;
 * the backend refuses it anyway, under the section's lock, so the last place is never taken twice.
 */
function SectionPlacementForm({ enrollment, studentLoginId, onDone, onCancel }: SectionPlacementFormProps) {
  const sections = useApiQuery(queryKeys.admin.sections(enrollment.semesterId), (signal) =>
    getSections(enrollment.semesterId, signal),
  )
  const context = semesterContext(enrollment)
  if (sections.status !== 'loaded') {
    return (
      <StatePanel>
        {sections.status === 'loading' ? (
          <LoadingState label="Loading sections…" />
        ) : (
          <ErrorState title="Couldn’t load sections." message="Check your connection and try again." onRetry={sections.retry} />
        )}
      </StatePanel>
    )
  }
  const choices = sections.data.filter((section) => section.status === 'ACTIVE' || section.id === enrollment.sectionId)
  return (
    <MasterForm
      heading={`Section of ${studentLoginId} in ${context}`}
      note={
        choices.length === 0
          ? 'This semester has no active sections yet. Add them under Sections.'
          : 'Only this semester’s active sections are listed.'
      }
      deniedMessage={ENROLLMENT_DENIED}
      fields={[
        {
          name: 'sectionId',
          label: 'Section',
          kind: 'select',
          optional: true,
          choose: 'No section',
          options: choices.map((section) => {
            const full = section.capacity !== null && section.enrolledCount >= section.capacity && section.id !== enrollment.sectionId
            return {
              value: String(section.id),
              label: `${section.code}${section.name ? ` · ${section.name}` : ''} (${
                section.capacity === null ? `${section.enrolledCount} enrolled` : `${section.enrolledCount} of ${section.capacity}`
              }${full ? ', full' : ''})`,
            }
          }),
        },
      ]}
      initial={{ sectionId: enrollment.sectionId === null ? '' : String(enrollment.sectionId) }}
      submitLabel="Save section"
      onSubmit={async (values) => {
        const saved = await changeSemesterEnrollmentSection(
          enrollment.semesterEnrollmentId,
          values.sectionId === '' ? null : Number(values.sectionId),
        )
        onDone(saved.sectionCode ? `${studentLoginId} placed in section ${saved.sectionCode}.` : `${studentLoginId} has no section now.`)
      }}
      onCancel={onCancel}
    />
  )
}
