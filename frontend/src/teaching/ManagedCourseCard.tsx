import { Badge, buttonClasses } from '../ui'
import { useQueryClient } from '@tanstack/react-query'
import { useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { createLmsCourse, publishLmsCourse } from '../api/lmsCourseManagementApi'
import { queryKeys } from '../api/queryKeys'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { usePendingFocus } from '../components/usePendingFocus'
import type { LmsCourseManagement, LmsManagedCourse } from '../types/lms'
import { enrolledText, lifecycleRefusal, lmsStatusWord } from './lmsCourseText'

type ManagedCourseCardProps = {
  course: LmsManagedCourse
  onChanged: (change: LmsCourseManagement) => void
}

const CREATE_FAILED = 'Couldn’t create the LMS course. Check your connection and try again.'
const PUBLISH_FAILED = 'Couldn’t publish the course. Check your connection and try again.'

export function ManagedCourseCard({ course, onChanged }: ManagedCourseCardProps) {
  const id = useId()
  const queryClient = useQueryClient()
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
      setMessage(`LMS course created as a draft. ${enrolledText(created.currentStudentCount)}`.trim())
      onChanged(created)
      void queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
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

  const statusTone = status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'info' : 'neutral'
  const credits = Number(course.credits)
  return (
    <article
      ref={cardRef}
      aria-labelledby={`${id}-name`}
      className={`rounded-panel border border-line border-l-[6px] bg-surface px-4 py-3 ${status === 'ARCHIVED' ? 'border-l-slate-400' : 'border-l-teal-600'}`}
    >
      <div className="grid items-center gap-x-5 gap-y-2 md:grid-cols-[minmax(10rem,1.4fr)_auto_auto_minmax(8rem,1fr)_auto_auto] md:divide-x md:divide-line">
        <div className="min-w-0">
          <p className="text-lg font-bold break-words text-ink">{course.courseCode}</p>
          <h3 id={`${id}-name`} className="text-[0.95rem] break-words text-ink">{course.courseName}</h3>
        </div>
        <p className="text-ink md:pl-5">{`${Number.isInteger(credits) ? credits : credits.toFixed(2)} ${credits === 1 ? 'credit' : 'credits'}`}</p>
        <p className="text-ink md:pl-5">{course.sectionCode ? `Section ${course.sectionCode}` : 'Whole semester'}</p>
        <p className="text-ink md:pl-5">{course.teachingRole === 'CO_FACULTY' ? 'Co-faculty' : 'Primary faculty'}</p>
        <p className="text-ink md:pl-5">{`Enrolled ${course.enrolledCount}`}</p>
        <p className="md:pl-5">
          <Badge tone={statusTone}>{lmsStatusWord(status)}</Badge>
        </p>
      </div>
      {status === 'DRAFT' && (
        <p className="mt-2 text-sm leading-6 text-ink-muted">Students can’t open it until it is published.</p>
      )}
      {status === 'ARCHIVED' && (
        <p className="mt-2 text-sm leading-6 text-ink-muted">
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
        <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
          {status === null && (
            <Button variant="secondary" aria-disabled={creating || undefined} onClick={create}>
              {creating ? 'Creating LMS course…' : 'Create LMS course'}
              <span className="sr-only">{spokenName}</span>
            </Button>
          )}
          {course.lmsCourseId !== null && (
            <Link to={`/teaching/${course.lmsCourseId}`} data-focus-key="open" className={buttonClasses(status === 'DRAFT' ? 'secondary' : 'primary', 'sm')}>
              Open<span className="sr-only"> LMS course</span><span className="sr-only">{spokenName}</span>
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
          detail="Students officially enrolled in this course offering will be able to open the course."
          confirmLabel="Publish course"
          busyLabel="Publishing…"
          onConfirm={async () => {
            const published = await publishLmsCourse(course.lmsCourseId as number)
            setConfirmingPublish(false)
            setMessage(`Course published. ${enrolledText(published.currentStudentCount)}`.trim())
            onChanged(published)
            void queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
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
