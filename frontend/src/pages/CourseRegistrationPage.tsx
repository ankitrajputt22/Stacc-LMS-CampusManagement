import { useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { queryKeys } from '../api/queryKeys'
import { dropCourse, getMyRegistration, registerForCourses } from '../api/registrationApi'
import { useApiQuery } from '../api/useApiQuery'
import { courseTypeWord } from '../admin/catalogText'
import { statusTone, statusWord } from '../admin/masterStatus'
import { useAuth } from '../auth/AuthContext'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { creditsText, formatCredits } from '../courses/credits'
import { formatDateTime } from '../courses/dates'
import type { EligibleOffering, RegisteredCourse, RegistrationTerm } from '../types/registration'
import { Alert, Badge, Button, Card, PageHeader } from '../ui'

const DENIED = 'You don’t have access to course registration.'
const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again.'

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return `${DENIED} ${SIGN_IN_AGAIN}`
  }
  if (status === 400 || status === 404 || status === 409) {
    return apiMessage ?? 'That could not be done. Reload the page and try again.'
  }
  return 'Couldn’t reach Stacc. Check your connection and try again.'
}

/** "1 Jul 2026, 9:00 am to 15 Jul 2026, 5:00 pm", or that no period is set. */
function periodText(term: RegistrationTerm): string {
  if (term.registrationOpensAt === null || term.registrationClosesAt === null) {
    return 'Not set'
  }
  return `${formatDateTime(term.registrationOpensAt)} to ${formatDateTime(term.registrationClosesAt)}`
}

function seatsText(offering: EligibleOffering): string {
  if (offering.capacity === null || offering.availableSeats === null) {
    return 'No seat limit'
  }
  return `${offering.availableSeats} of ${offering.capacity} seats free`
}

function Details({ items }: { items: (string | null)[] }) {
  const shown = items.filter((item): item is string => item !== null && item !== '')
  return (
    <p className="mt-0.5 text-sm text-ink-muted">
      {shown.map((item, index) => (
        <span key={item}>
          {index > 0 && (
            <>
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, </span>
            </>
          )}
          {item}
        </span>
      ))}
    </p>
  )
}

/** One semester the student may register in: its period, the open offerings to choose from, and their courses. */
function TermCard({ term, profileActive }: { term: RegistrationTerm; profileActive: boolean }) {
  const queryClient = useQueryClient()
  const id = useId()
  const [selected, setSelected] = useState<number[]>([])
  const [reviewing, setReviewing] = useState(false)
  const [dropping, setDropping] = useState<RegisteredCourse | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const heading = `${term.academicSessionCode} · Semester ${term.semesterNumber}`
  const chosen = term.offerings.filter((offering) => selected.includes(offering.courseOfferingId))
  const chosenCredits = chosen.reduce((sum, offering) => sum + offering.credits, 0)
  const after = term.registeredCredits + chosenCredits
  const overLimit = term.maxCredits !== null && after > term.maxCredits

  function toggle(offeringId: number, on: boolean) {
    setReviewing(false)
    setNotice(null)
    setSelected((current) => (on ? [...current, offeringId] : current.filter((value) => value !== offeringId)))
  }

  async function refreshAll() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.registration.mine() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.lms.all }),
    ])
  }

  // A refusal is thrown to the confirmation, which shows the backend's message and keeps it open.
  async function confirmRegistration() {
    const result = await registerForCourses(selected)
    setSelected([])
    setReviewing(false)
    setNotice(
      `Registered for ${result.totalCourses} ${result.totalCourses === 1 ? 'course' : 'courses'} (${creditsText(result.totalCredits)}): ${result.registered
        .map((course) => course.courseCode)
        .join(', ')}.`,
    )
    await refreshAll()
  }

  async function confirmDrop(course: RegisteredCourse) {
    await dropCourse(course.courseEnrollmentId)
    setDropping(null)
    setNotice(`${course.courseCode} ${course.courseName} dropped.`)
    await refreshAll()
  }

  return (
    <Card title={heading} description={`${term.programCode} · ${term.programName}`}>
      <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-ink-muted">Section</dt>
          <dd className="font-medium text-ink">{term.sectionCode ?? 'Not placed in a section'}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Registration</dt>
          <dd className="font-medium text-ink">{periodText(term)}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Drop deadline</dt>
          <dd className="font-medium text-ink">
            {term.dropDeadline === null ? 'Dropping not allowed' : formatDateTime(term.dropDeadline)}
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Registered credits</dt>
          <dd className="font-medium text-ink">
            {formatCredits(term.registeredCredits)}
            {term.maxCredits !== null && ` of at most ${formatCredits(term.maxCredits)}`}
          </dd>
        </div>
      </dl>

      {!term.registrationOpen && profileActive && (
        <Alert tone="warning" className="mt-4">
          Registration for this semester is not open now. You can still see your courses below.
        </Alert>
      )}
      {notice && (
        <Alert tone="success" className="mt-4">
          <span role="status">{notice}</span>
        </Alert>
      )}

      <section aria-labelledby={`${id}-available`} className="mt-6">
        <h3 id={`${id}-available`} className="text-lg font-semibold text-ink">
          Available courses
        </h3>
        {term.offerings.length === 0 ? (
          <p className="mt-2 text-sm text-ink-muted">No course offerings are open to your semester and section.</p>
        ) : (
          <ul className="mt-2 divide-y divide-line rounded-control border border-line">
            {term.offerings.map((offering) => {
              const inputId = `${id}-offering-${offering.courseOfferingId}`
              return (
                <li key={offering.courseOfferingId} className="flex gap-3 px-4 py-3">
                  <input
                    id={inputId}
                    type="checkbox"
                    className="mt-1 size-4 shrink-0 accent-portal-navy"
                    checked={selected.includes(offering.courseOfferingId)}
                    disabled={!offering.canRegister || reviewing}
                    onChange={(event) => toggle(offering.courseOfferingId, event.target.checked)}
                  />
                  <div className="min-w-0 flex-1">
                    <label htmlFor={inputId} className="font-semibold break-words text-ink">
                      {offering.courseCode} {offering.courseName}
                    </label>
                    <Details
                      items={[
                        creditsText(offering.credits),
                        courseTypeWord(offering.courseType),
                        offering.sectionCode ? `Section ${offering.sectionCode}` : 'Whole semester',
                        offering.faculty.join(', '),
                        seatsText(offering),
                      ]}
                    />
                    {offering.reason && <p className="mt-1 text-sm text-ink-muted">{offering.reason}</p>}
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        {selected.length > 0 && (
          <div className="mt-4 rounded-control border border-line bg-surface-muted px-4 py-3">
            <h4 className="font-semibold text-ink">Registration summary</h4>
            <dl className="mt-2 grid gap-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-ink-muted">Selected courses</dt>
                <dd className="font-medium text-ink">{selected.length}</dd>
              </div>
              <div>
                <dt className="text-ink-muted">Selected credits</dt>
                <dd className="font-medium text-ink">{formatCredits(chosenCredits)}</dd>
              </div>
              <div>
                <dt className="text-ink-muted">Credits after registration</dt>
                <dd className="font-medium text-ink">
                  {formatCredits(after)}
                  {term.maxCredits !== null && ` of at most ${formatCredits(term.maxCredits)}`}
                </dd>
              </div>
            </dl>
            {overLimit && (
              <p className="mt-2 text-sm text-danger">This is over your credit limit. Choose fewer courses.</p>
            )}
            {reviewing ? (
              <StatusConfirmation
                question="Confirm your registration?"
                detail={`You are registering for ${selected.length} ${selected.length === 1 ? 'course' : 'courses'} with a total of ${creditsText(chosenCredits)}: ${chosen
                  .map((offering) => offering.courseCode)
                  .join(', ')}.`}
                confirmLabel="Confirm registration"
                busyLabel="Registering…"
                onConfirm={confirmRegistration}
                onCancel={() => setReviewing(false)}
                describeRefusal={refusal}
              />
            ) : (
              <Button className="mt-3" onClick={() => setReviewing(true)} disabled={overLimit}>
                Review registration
              </Button>
            )}
          </div>
        )}
      </section>

      <section aria-labelledby={`${id}-mine`} className="mt-6">
        <h3 id={`${id}-mine`} className="text-lg font-semibold text-ink">
          My registered courses
        </h3>
        {term.registered.length === 0 ? (
          <p className="mt-2 text-sm text-ink-muted">You have no courses in this semester yet.</p>
        ) : (
          <ul className="mt-2 divide-y divide-line rounded-control border border-line">
            {term.registered.map((course) => (
              <li key={course.courseEnrollmentId} className="px-4 py-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold break-words text-ink">
                      {course.courseCode} {course.courseName}
                    </p>
                    <Details
                      items={[
                        creditsText(course.credits),
                        course.sectionCode ? `Section ${course.sectionCode}` : 'Whole semester',
                        course.faculty.join(', '),
                        `Registered ${formatDateTime(course.registeredAt)}`,
                        course.droppedAt ? `Dropped ${formatDateTime(course.droppedAt)}` : null,
                      ]}
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={statusTone(course.status)}>{statusWord(course.status)}</Badge>
                    {course.canDrop && dropping?.courseEnrollmentId !== course.courseEnrollmentId && (
                      <Button variant="secondary" size="sm" onClick={() => setDropping(course)}>
                        Drop<span className="sr-only"> {course.courseCode}</span>
                      </Button>
                    )}
                  </div>
                </div>
                {dropping?.courseEnrollmentId === course.courseEnrollmentId && (
                  <StatusConfirmation
                    question={`Drop ${course.courseCode} ${course.courseName}?`}
                    detail="Are you sure you want to drop this course? It stays in your record as dropped, and you cannot register for it again."
                    confirmLabel="Drop course"
                    busyLabel="Dropping…"
                    onConfirm={() => confirmDrop(course)}
                    onCancel={() => setDropping(null)}
                    describeRefusal={refusal}
                  />
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </Card>
  )
}

function MyRegistration() {
  const registration = useApiQuery(queryKeys.registration.mine(), getMyRegistration)

  switch (registration.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading course registration…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'notFound':
      return (
        <StatePanel>
          <EmptyState title="No student profile." description="Contact the college administration to set up your academic profile." />
        </StatePanel>
      )
    case 'failed':
      return (
        <StatePanel>
          <ErrorState
            title="Couldn’t load course registration."
            message="Check your connection and try again."
            onRetry={registration.retry}
          />
        </StatePanel>
      )
    case 'loaded': {
      const { profileActive, terms } = registration.data
      return (
        <div className="space-y-4">
          {!profileActive && (
            <Alert tone="warning">Your student profile is inactive, so you cannot register or drop courses.</Alert>
          )}
          {terms.length === 0 ? (
            <StatePanel>
              <EmptyState
                title="No semester open for registration."
                description="You are not enrolled in a semester of the current session. Contact the college administration if you think you should be."
              />
            </StatePanel>
          ) : (
            terms.map((term) => (
              <TermCard
                key={`${term.academicSessionCode}|${term.programCode}|${term.semesterNumber}`}
                term={term}
                profileActive={profileActive}
              />
            ))
          )}
        </div>
      )
    }
  }
}

/**
 * Course registration at /academic/registration: the student's semester, the course offerings open to them, a summary
 * and confirmation before registering, and their registered courses with Drop while allowed. The backend takes the
 * student from the access token and checks every rule again.
 */
export function CourseRegistrationPage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])

  return (
    <div className="max-w-5xl">
      <title>Course Registration · Academic · Stacc</title>
      <PageHeader
        eyebrow="ERP · Academics"
        title="Course Registration"
        description={isStudent ? 'Choose your courses for the semester, review them, and confirm.' : undefined}
      />
      {isStudent ? (
        <MyRegistration />
      ) : (
        <StatePanel>
          <AccessDeniedState
            title="This page is for student accounts."
            message="If you think you should, contact the college administration."
          />
        </StatePanel>
      )}
    </div>
  )
}
