import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { updateAttendanceSession } from '../api/attendanceApi'
import { ActionButton } from '../components/ActionButton'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import type { AttendanceSessionDetail } from '../types/attendance'
import { AttendanceRoster } from './AttendanceRoster'
import { ATTENDANCE_DENIED, SIGN_IN_AGAIN, backLinkClasses } from './attendanceText'
import { SessionForm } from './SessionForm'
import { SessionStatusTag } from './SessionStatusTag'
import { useAttendanceSession } from './useAttendance'

type SessionViewProps = {
  /** The course offering of the page. A session of another offering reads as unavailable here. */
  courseOfferingId: number
  /** Null when the address cannot name a session: nothing is asked, and it reads as unavailable. */
  sessionId: number | null
  /** The list of sessions, to go back to. */
  listPath: string
  /** After a change, so the session list behind this view is up to date. */
  onChanged: () => void
}

/**
 * One session: when it was held, its topic and status, and its roster to mark, finalize, or correct. What is
 * shown is only what the backend sent, and after a change the page shows the backend's answer without reloading.
 */
export function SessionView({ courseOfferingId, sessionId, listPath, onChanged }: SessionViewProps) {
  const detail = useAttendanceSession(sessionId)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [latest, setLatest] = useState<AttendanceSessionDetail | null>(null)
  const [editing, setEditing] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  // The address names the offering too, so a session of another offering is never shown under this one.
  const belongsHere = detail.status === 'loaded' && detail.data.courseOfferingId === courseOfferingId
  const loaded = belongsHere
  const session = latest ?? (belongsHere ? detail.data : null)

  // Opening a session moves focus to it, so keyboard and screen reader users start there.
  useEffect(() => {
    if (loaded) {
      headingRef.current?.focus()
    }
  }, [loaded])

  const back = (
    <Link to={listPath} state={{ fromSession: sessionId }} className={`mt-6 ${backLinkClasses}`}>
      Back to sessions
    </Link>
  )

  if (session === null) {
    return (
      <section aria-labelledby="attendance-session-heading" className="mt-8 max-w-3xl">
        <h2 id="attendance-session-heading" className="text-base font-semibold text-ink">
          Session
        </h2>
        <div className="mt-3">
          {detail.status === 'loading' && (
            <StatePanel>
              <LoadingState label="Loading attendance…" />
            </StatePanel>
          )}
          {(detail.status === 'notFound' || (detail.status === 'loaded' && !belongsHere)) && (
            <p className="text-sm leading-6 text-ink-muted">
              Attendance session unavailable. It could not be found, or it is not available to your account.
            </p>
          )}
          {detail.status === 'denied' && (
            <StatePanel>
              <AccessDeniedState title={ATTENDANCE_DENIED} message={SIGN_IN_AGAIN} />
            </StatePanel>
          )}
          {detail.status === 'failed' && (
            <StatePanel>
              <ErrorState title="Couldn’t load attendance." message="Check your connection and try again." onRetry={detail.retry} />
            </StatePanel>
          )}
        </div>
        {back}
      </section>
    )
  }

  return (
    <section aria-labelledby="attendance-session-heading" className="mt-8 max-w-3xl">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h2
          ref={headingRef}
          id="attendance-session-heading"
          tabIndex={-1}
          className="text-base font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          Session of <time dateTime={session.heldAt}>{formatDateTime(session.heldAt)}</time>
        </h2>
        <SessionStatusTag status={session.status} />
      </div>
      {session.topic && <p className="mt-1 text-sm leading-6 break-words text-ink">{session.topic}</p>}
      <p className="mt-1 text-xs text-ink-muted">
        {session.finalizedAt ? (
          <>
            Finalized <time dateTime={session.finalizedAt}>{formatDateTime(session.finalizedAt)}</time>. Students can see
            it.
          </>
        ) : (
          'Open. Students cannot see it until it is finalized.'
        )}
      </p>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {session.status === 'OPEN' && (
        <div className="mt-3">
          {editing ? (
            <SessionForm
              heading="Edit the date and topic"
              initial={session}
              submitLabel="Save session"
              onSubmit={async (input) => {
                const updated = await updateAttendanceSession(session.sessionId, input)
                setLatest({ ...session, heldAt: updated.heldAt, topic: updated.topic })
                setEditing(false)
                setAnnouncement('Session saved.')
                onChanged()
              }}
              onCancel={() => setEditing(false)}
            />
          ) : (
            <ActionButton onClick={() => setEditing(true)}>Edit date and topic</ActionButton>
          )}
        </div>
      )}

      <AttendanceRoster
        key={`${session.sessionId}-${session.status}`}
        session={session}
        onChanged={setLatest}
        onFinalized={() => {
          // The roster is replaced by its finalized form, so focus and the news move to the session heading.
          setAnnouncement('Attendance finalized. Students can see it now.')
          headingRef.current?.focus()
          onChanged()
        }}
      />

      {back}
    </section>
  )
}
