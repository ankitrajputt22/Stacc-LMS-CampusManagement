import { useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { createLmsCourse, publishLmsCourse } from '../api/lmsCourseManagementApi'
import { ActionButton } from '../components/ActionButton'
import { actionLinkClasses } from '../components/actionStyles'
import { Button } from '../components/Button'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { usePendingFocus } from '../components/usePendingFocus'
import { CourseFacts } from '../courses/CourseFacts'
import type { LmsCourseManagement, LmsManagedCourse } from '../types/lms'
import { lifecycleRefusal, lmsStatusWord, synchronizedText } from './lmsCourseText'

type ManagedCourseCardProps = {
  course: LmsManagedCourse
  /** Called with the LMS course after it was created or published, so the card updates without a reload. */
  onChanged: (change: LmsCourseManagement) => void
}

const CREATE_FAILED = 'Couldn’t create the LMS course. Check your connection and try again.'
const PUBLISH_FAILED = 'Couldn’t publish the course. Check your connection and try again.'

/**
 * One course offering the faculty member teaches, with its LMS state as the backend sent it. Without an LMS course
 * it offers Create LMS course; a draft can be opened or published after a confirmation; a published one can be
 * opened; an archived one is history only and offers nothing, because the faculty pages no longer open it. The
 * backend checks the teaching assignment on every request.
 */
export function ManagedCourseCard({ course, onChanged }: ManagedCourseCardProps) {
  const id = useId()
  const cardRef = useRef<HTMLElement>(null)
  const busyRef = useRef(false)
  const [creating, setCreating] = useState(false)
  const [confirmingPublish, setConfirmingPublish] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const focusLater = usePendingFocus(cardRef)
  const status = course.lmsCourseStatus
  const spokenName = ` ${course.courseCode}`

  async function create() {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    setCreating(true)
    setError(null)
    setMessage('')
    try {
      const created = await createLmsCourse(course.courseOfferingId)
      setMessage(`LMS course created as a draft. ${synchronizedText(created.synchronizedCount)}`)
      onChanged(created)
      // The Create button is gone; focus moves to the new course's Open link.
      focusLater('open')
    } catch (failure) {
      const httpStatus = getHttpStatus(failure)
      if (httpStatus !== 401) {
        setError(lifecycleRefusal(httpStatus, getApiError(failure)?.message ?? null, CREATE_FAILED))
      }
    } finally {
      busyRef.current = false
      setCreating(false)
    }
  }

  return (
    <article
      ref={cardRef}
      aria-labelledby={`${id}-name`}
      className="h-full rounded-panel border border-line bg-surface px-4 py-3.5 sm:px-5 sm:py-4"
    >
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
      <h2 id={`${id}-name`} className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {course.courseName}
      </h2>
      <CourseFacts course={course} />

      <dl className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        <dt className="text-ink-muted">LMS course</dt>
        <dd>
          <span className="inline-block rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
            {lmsStatusWord(status)}
          </span>
        </dd>
      </dl>
      {status === 'DRAFT' && (
        <p className="mt-1 text-sm leading-6 text-ink-muted">Students can’t open it until it is published.</p>
      )}
      {status === 'ARCHIVED' && (
        <p className="mt-1 text-sm leading-6 text-ink-muted">
          This LMS course is archived. Its content and history are kept, but it can no longer be opened.
        </p>
      )}

      <p role="status" className={message ? 'mt-2 text-sm leading-6 text-ink' : 'sr-only'}>
        {message}
      </p>
      <div role="alert">
        {error && (
          <p className="mt-2 rounded-control border border-danger/40 bg-danger/5 px-3 py-2 text-sm leading-5 text-danger">{error}</p>
        )}
      </div>

      {!confirmingPublish && status !== 'ARCHIVED' && (
        <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          {status === null && (
            <Button variant="secondary" aria-disabled={creating || undefined} onClick={create}>
              {creating ? 'Creating LMS course…' : 'Create LMS course'}
              <span className="sr-only">{spokenName}</span>
            </Button>
          )}
          {course.lmsCourseId !== null && (
            // The route uses the LMS course ID, never an offering or teaching assignment ID.
            <Link to={`/teaching/${course.lmsCourseId}`} data-focus-key="open" className={actionLinkClasses}>
              Open LMS course<span className="sr-only">{spokenName}</span>
            </Link>
          )}
          {status === 'DRAFT' && (
            <ActionButton
              focusKey="publish"
              onClick={() => {
                setError(null)
                setMessage('')
                setConfirmingPublish(true)
              }}
            >
              Publish<span className="sr-only">{spokenName}</span>
            </ActionButton>
          )}
        </div>
      )}
      {confirmingPublish && course.lmsCourseId !== null && (
        <StatusConfirmation
          question="Publish LMS course?"
          detail="Students with valid enrollment and LMS membership will be able to access the course."
          confirmLabel="Publish course"
          busyLabel="Publishing…"
          onConfirm={async () => {
            const published = await publishLmsCourse(course.lmsCourseId as number)
            setConfirmingPublish(false)
            setMessage(`Course published. ${synchronizedText(published.synchronizedCount)}`)
            onChanged(published)
            focusLater('open')
          }}
          onCancel={() => {
            setConfirmingPublish(false)
            focusLater('publish')
          }}
          describeRefusal={(httpStatus, apiMessage) => lifecycleRefusal(httpStatus, apiMessage, PUBLISH_FAILED)}
        />
      )}
    </article>
  )
}
