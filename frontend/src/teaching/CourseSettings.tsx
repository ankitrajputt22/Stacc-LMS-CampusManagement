import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { archiveLmsCourse, publishLmsCourse, syncStudentMemberships } from '../api/lmsCourseManagementApi'
import { ActionButton } from '../components/ActionButton'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { usePendingFocus } from '../components/usePendingFocus'
import type { LmsFacultyCourseContent } from '../types/lms'
import { lifecycleRefusal, synchronizedText } from './lmsCourseText'
import { StatusTag } from './StatusTag'

type CourseSettingsProps = {
  course: LmsFacultyCourseContent
  /** Reloads the course after its status changed. */
  reload: () => void
}

const SYNC_FAILED = 'Couldn’t synchronize student access. Check your connection and try again.'
const PUBLISH_FAILED = 'Couldn’t publish the course. Check your connection and try again.'
const ARCHIVE_FAILED = 'Couldn’t archive the course. Check your connection and try again.'

/**
 * The LMS course's own lifecycle: its status, Sync student access, Publish course for a draft, and Archive course.
 * Publishing and archiving are confirmed first. An archived course can no longer be opened here, so archiving
 * returns to /teaching, which shows it as archived. Students and enrollments are never edited here: student access
 * comes from official enrollment on the backend.
 */
export function CourseSettings({ course, reload }: CourseSettingsProps) {
  const navigate = useNavigate()
  const sectionRef = useRef<HTMLElement>(null)
  const syncingRef = useRef(false)
  const [syncing, setSyncing] = useState(false)
  const [confirming, setConfirming] = useState<'publish' | 'archive' | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const focusLater = usePendingFocus(sectionRef)
  const draft = course.lmsCourseStatus === 'DRAFT'
  const courseName = `${course.courseCode} · ${course.courseName}`

  async function sync() {
    if (syncingRef.current) {
      return
    }
    syncingRef.current = true
    setSyncing(true)
    setMessage('')
    setError(null)
    try {
      const synchronized = await syncStudentMemberships(course.lmsCourseId)
      setMessage(synchronizedText(synchronized.synchronizedCount))
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(lifecycleRefusal(status, getApiError(failure)?.message ?? null, SYNC_FAILED))
      }
    } finally {
      syncingRef.current = false
      setSyncing(false)
    }
  }

  function ask(action: 'publish' | 'archive') {
    setMessage('')
    setError(null)
    setConfirming(action)
  }

  return (
    <section
      ref={sectionRef}
      aria-labelledby="course-settings-heading"
      className="mt-6 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5"
    >
      <h2 id="course-settings-heading" className="text-lg font-semibold text-ink">
        Course settings
      </h2>
      <p className="mt-2 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
        <span aria-hidden="true">LMS status:</span>
        <StatusTag status={course.lmsCourseStatus} label="LMS status" />
        <span>
          {draft
            ? 'Students can’t open this course until it is published.'
            : 'Students with valid enrollment and LMS membership can open this course.'}
        </span>
      </p>
      <p id="sync-help" className="mt-2 text-sm leading-6 text-ink-muted">
        Synchronizes LMS access for currently eligible students enrolled in this course offering.
      </p>

      <p role="status" className={message ? 'mt-2 text-sm leading-6 text-ink' : 'sr-only'}>
        {message}
      </p>
      <div role="alert">
        {error && (
          <p className="mt-2 rounded-control border border-danger/40 bg-danger/5 px-3 py-2 text-sm leading-5 text-danger">{error}</p>
        )}
      </div>

      {confirming === null && (
        <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <ActionButton focusKey="sync" aria-describedby="sync-help" aria-disabled={syncing || undefined} onClick={sync}>
            {syncing ? 'Synchronizing…' : 'Sync student access'}
          </ActionButton>
          {draft && (
            <ActionButton focusKey="publish" onClick={() => ask('publish')}>
              Publish course
            </ActionButton>
          )}
          <ActionButton focusKey="archive" onClick={() => ask('archive')}>
            Archive course
          </ActionButton>
        </div>
      )}
      {confirming === 'publish' && (
        <StatusConfirmation
          question="Publish LMS course?"
          detail="Students with valid enrollment and LMS membership will be able to access the course."
          confirmLabel="Publish course"
          busyLabel="Publishing…"
          onConfirm={async () => {
            const published = await publishLmsCourse(course.lmsCourseId)
            setConfirming(null)
            setMessage(`Course published. ${synchronizedText(published.synchronizedCount)}`)
            reload()
            focusLater('sync')
          }}
          onCancel={() => {
            setConfirming(null)
            focusLater('publish')
          }}
          describeRefusal={(status, apiMessage) => lifecycleRefusal(status, apiMessage, PUBLISH_FAILED)}
        />
      )}
      {confirming === 'archive' && (
        <StatusConfirmation
          question="Archive LMS course?"
          detail="Students will lose normal LMS access, but course history will be preserved. Archiving cannot be undone, and this page will no longer open the course."
          confirmLabel="Archive course"
          busyLabel="Archiving…"
          onConfirm={async () => {
            await archiveLmsCourse(course.lmsCourseId)
            navigate('/teaching', { state: { archivedCourse: courseName } })
          }}
          onCancel={() => {
            setConfirming(null)
            focusLater('archive')
          }}
          describeRefusal={(status, apiMessage) => lifecycleRefusal(status, apiMessage, ARCHIVE_FAILED)}
        />
      )}
    </section>
  )
}
