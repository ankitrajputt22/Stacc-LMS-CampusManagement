import { useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { finalizeAttendanceSession, saveAttendanceRecords } from '../api/attendanceApi'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import type { AttendanceSessionDetail, AttendanceStatus } from '../types/attendance'

const STATUS_WORDS: Record<AttendanceStatus, string> = {
  UNMARKED: 'Not marked',
  PRESENT: 'Present',
  ABSENT: 'Absent',
}

type Draft = Record<number, AttendanceStatus>

function draftOf(session: AttendanceSessionDetail): Draft {
  return Object.fromEntries(session.records.map((record) => [record.attendanceRecordId, record.status]))
}

function refusal(status: number | null, apiMessage: string | null, action: 'save' | 'finalize'): string {
  switch (status) {
    case 400:
      return apiMessage ?? (action === 'save' ? 'Couldn’t save attendance.' : 'Couldn’t finalize attendance.')
    case 403:
      return 'You don’t have access to attendance. If your access changed recently, sign out and sign in again.'
    case 404:
      return 'This session is no longer available.'
    default:
      return action === 'save'
        ? 'Couldn’t save attendance. Check your connection and try again. Your marks are still here.'
        : 'Couldn’t finalize attendance. Check your connection and try again.'
  }
}

type AttendanceRosterProps = {
  session: AttendanceSessionDetail
  /** The session as the backend now holds it, after a save or a finalization. */
  onChanged: (session: AttendanceSessionDetail) => void
  /** After finalizing, so the session list behind this view is up to date. */
  onFinalized: () => void
}

/**
 * A session's roster: one Present/Absent choice per student, as real radio buttons. A student nobody has marked
 * shows "Not marked"; nothing is ever assumed absent. Choices stay on this page until **Save attendance** is
 * pressed, which can happen part of the way. An open session is finalized once every student is saved as present
 * or absent; afterwards each row can still be corrected between present and absent. The backend checks access
 * and every rule again, and a 403 never signs the user out.
 */
export function AttendanceRoster({ session, onChanged, onFinalized }: AttendanceRosterProps) {
  const id = useId()
  const busyRef = useRef(false)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const [draft, setDraft] = useState<Draft>(() => draftOf(session))
  const [busy, setBusy] = useState<'save' | 'finalize' | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const finalized = session.status === 'FINALIZED'

  const changed = session.records.filter((record) => draft[record.attendanceRecordId] !== record.status)
  const savedUnmarked = session.records.filter((record) => record.status === 'UNMARKED').length
  const canFinalize = !finalized && savedUnmarked === 0 && changed.length === 0

  useEffect(() => {
    if (confirming) {
      confirmRef.current?.focus()
    }
  }, [confirming])

  function choose(recordId: number, status: AttendanceStatus) {
    setDraft((current) => ({ ...current, [recordId]: status }))
    setAnnouncement('')
  }

  function markAllPresent() {
    setDraft(Object.fromEntries(session.records.map((record) => [record.attendanceRecordId, 'PRESENT'])))
    setAnnouncement('Every student is marked present on this page. Save attendance to keep it.')
  }

  async function save() {
    if (busyRef.current) {
      return
    }
    setError(null)
    if (changed.length === 0) {
      setAnnouncement('There are no changes to save.')
      return
    }
    busyRef.current = true
    setBusy('save')
    try {
      const saved = await saveAttendanceRecords(
        session.sessionId,
        changed.map((record) => ({ attendanceRecordId: record.attendanceRecordId, status: draft[record.attendanceRecordId] })),
      )
      setDraft(draftOf(saved))
      onChanged(saved)
      setAnnouncement(finalized ? 'Corrections saved. Students can see them now.' : 'Attendance saved.')
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null, 'save'))
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  async function finalize() {
    if (busyRef.current) {
      return
    }
    setConfirming(false)
    setError(null)
    busyRef.current = true
    setBusy('finalize')
    try {
      const result = await finalizeAttendanceSession(session.sessionId)
      onChanged({ ...session, status: result.status, finalizedAt: result.finalizedAt })
      onFinalized()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null, 'finalize'))
      }
    } finally {
      busyRef.current = false
      setBusy(null)
    }
  }

  const finalizeReason = finalized
    ? null
    : changed.length > 0
      ? 'Save attendance before finalizing it.'
      : savedUnmarked > 0
        ? 'Mark every student before finalizing attendance.'
        : null

  return (
    <section aria-labelledby={`${id}-heading`} aria-busy={busy !== null} className="mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
          Students
        </h2>
        {!finalized && session.records.length > 0 && (
          <ActionButton onClick={markAllPresent}>Mark all present</ActionButton>
        )}
      </div>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        {finalized
          ? 'Corrections are visible to students after saving.'
          : 'The students enrolled when this session was created. Nothing is saved until you press Save attendance.'}
      </p>

      <p role="status" className="sr-only">
        {announcement}
      </p>

      {session.records.length === 0 ? (
        <p className="mt-3 rounded-panel border border-line bg-surface px-4 py-6 text-sm text-ink-muted">
          No students were enrolled in this course offering when the session was created.
        </p>
      ) : (
        <div className="mt-3 rounded-panel border border-line bg-surface">
          <div aria-hidden="true" className="hidden border-b border-line px-5 py-2 text-xs font-semibold text-ink-muted sm:grid sm:grid-cols-[minmax(0,1fr)_auto]">
            <span>Student ID</span>
            <span>Attendance</span>
          </div>
          <ul aria-label="Students" className="divide-y divide-line">
            {session.records.map((record) => {
              const value = draft[record.attendanceRecordId]
              const isChanged = value !== record.status
              const name = `${id}-record-${record.attendanceRecordId}`
              return (
                <li key={record.attendanceRecordId} className="grid gap-2 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:px-5">
                  <div className="min-w-0">
                    <p className="text-xs text-ink-muted sm:hidden">Student ID</p>
                    <p className="flex flex-wrap items-center gap-2 text-sm font-medium break-all text-ink">
                      {record.studentLoginId}
                      {value === 'UNMARKED' && (
                        <span className="inline-block shrink-0 rounded-sm border border-dashed border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink-muted">
                          Not marked
                        </span>
                      )}
                      {isChanged && (
                        <span className="text-xs font-normal text-ink-muted">Not saved</span>
                      )}
                    </p>
                  </div>
                  <fieldset className="min-w-0">
                    <legend className="text-xs text-ink-muted sm:sr-only">
                      Attendance<span className="sr-only"> for {record.studentLoginId}</span>
                    </legend>
                    <div className="mt-1 flex flex-wrap gap-2 sm:mt-0">
                      {(['PRESENT', 'ABSENT'] as const).map((option) => (
                        <label
                          key={option}
                          className={`inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-control border px-3 text-sm font-medium has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-stacc-primary sm:min-h-9 ${
                            value === option ? 'border-stacc-primary bg-surface-muted text-ink' : 'border-line-strong text-ink'
                          }`}
                        >
                          <input
                            type="radio"
                            name={name}
                            value={option}
                            checked={value === option}
                            onChange={() => choose(record.attendanceRecordId, option)}
                            className="size-4 accent-stacc-primary focus-visible:outline-none"
                          />
                          {STATUS_WORDS[option]}
                        </label>
                      ))}
                    </div>
                  </fieldset>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <div role="alert">
        {error && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {error}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
        {session.records.length > 0 && (
          <Button onClick={() => void save()} aria-disabled={busy !== null || undefined}>
            {busy === 'save' ? 'Saving…' : finalized ? 'Save corrections' : 'Save attendance'}
          </Button>
        )}
        {changed.length > 0 && (
          <p className="text-sm text-ink-muted">
            {changed.length === 1 ? '1 change not saved yet.' : `${changed.length} changes not saved yet.`}
          </p>
        )}
      </div>

      {!finalized && (
        <div className="mt-6 border-t border-line pt-4">
          {confirming ? (
            <div role="group" aria-labelledby={`${id}-confirm`} className="rounded-control border border-line-strong bg-surface-muted p-3">
              <p id={`${id}-confirm`} className="text-sm font-semibold text-ink">
                Finalize attendance?
              </p>
              <p className="mt-1 text-sm leading-6 text-ink">
                Students will be able to view this attendance once finalized. You can still correct Present and Absent
                afterwards.
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  ref={confirmRef}
                  type="button"
                  onClick={() => void finalize()}
                  className="inline-flex min-h-11 items-center rounded-control border border-transparent bg-stacc-primary px-3 text-sm font-medium text-white hover:bg-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8"
                >
                  Finalize
                </button>
                <ActionButton onClick={() => setConfirming(false)}>Cancel</ActionButton>
              </div>
            </div>
          ) : (
            <>
              <Button
                variant="secondary"
                aria-disabled={!canFinalize || busy !== null || undefined}
                aria-describedby={finalizeReason ? `${id}-finalize-reason` : undefined}
                onClick={() => {
                  if (canFinalize && busy === null) {
                    setConfirming(true)
                  }
                }}
                className={canFinalize ? '' : 'cursor-not-allowed text-ink-muted'}
              >
                {busy === 'finalize' ? 'Finalizing…' : 'Finalize session'}
              </Button>
              {finalizeReason && (
                <p id={`${id}-finalize-reason`} className="mt-2 text-sm text-ink-muted">
                  {finalizeReason}
                </p>
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}
