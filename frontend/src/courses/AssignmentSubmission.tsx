import { useEffect, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { downloadSubmissionAttachment, submitAssignment } from '../api/lmsSubmissionsApi'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import type { LmsSubmissionAttempt } from '../types/lms'
import { formatDateTime, isPast } from './dates'
import { fileTypeLabel, formatFileSize } from './materials'
import { SubmissionForm, type SubmitOutcome } from './SubmissionForm'
import { useFileDownload } from './useFileDownload'
import { useMySubmissions } from './useMySubmissions'

type AssignmentSubmissionProps = {
  lmsCourseId: number
  assignmentId: number
  /** The assignment's current deadline, for the warning before submitting. Past attempts use their own result. */
  dueAt: string
}

const NOT_CHANGED = 'Your previous submissions have not been changed.'

function refusal(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
      return apiMessage ? `${apiMessage} ${NOT_CHANGED}` : `Couldn’t submit your assignment. ${NOT_CHANGED}`
    case 403:
      return 'You don’t have access to submit this assignment. If your access changed recently, sign out and sign in again.'
    case 404:
      return 'This assignment is no longer available for submission.'
    case 413:
      return `The files are larger than the upload limit. ${NOT_CHANGED}`
    case 503:
      return `${apiMessage ?? 'File storage is not available right now. Try again later.'} ${NOT_CHANGED}`
    default:
      return `Couldn’t submit your assignment. ${NOT_CHANGED}`
  }
}

/** On time or late, as the backend decided it for this attempt. Words, never colour alone. */
function TimingTag({ late }: { late: boolean }) {
  return (
    <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
      {late ? 'Late' : 'On time'}
    </span>
  )
}

type AttemptItemProps = {
  attempt: LmsSubmissionAttempt
  isLatest: boolean
  downloads: ReturnType<typeof useFileDownload>
}

function AttemptItem({ attempt, isLatest, downloads }: AttemptItemProps) {
  const headingId = `attempt-${attempt.attemptId}-heading`
  return (
    <article aria-labelledby={headingId} className="px-4 py-3 sm:px-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h4
          id={headingId}
          tabIndex={-1}
          data-attempt-number={attempt.attemptNumber}
          className="text-sm font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          Attempt {attempt.attemptNumber}
          {isLatest && <span className="sr-only">, latest</span>}
        </h4>
        <TimingTag late={attempt.late} />
        {isLatest && <span className="text-xs text-ink-muted" aria-hidden="true">Latest</span>}
      </div>
      <p className="text-xs text-ink-muted">
        Submitted <time dateTime={attempt.submittedAt}>{formatDateTime(attempt.submittedAt)}</time>
      </p>
      {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
      {attempt.textContent && (
        <p className="mt-2 text-sm leading-6 break-words whitespace-pre-line text-ink">{attempt.textContent}</p>
      )}
      {attempt.attachments.length > 0 && (
        <ul aria-label={`Files of attempt ${attempt.attemptNumber}`} className="mt-2 divide-y divide-line rounded-control border border-line">
          {attempt.attachments.map((file) => {
            const isDownloading = downloads.downloadingId === file.attachmentId
            const failed = downloads.failure?.fileId === file.attachmentId ? downloads.failure.message : null
            const type = fileTypeLabel(file.originalFileName)
            return (
              <li key={file.attachmentId} className="px-3 py-2">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <p className="min-w-0 text-sm text-ink">
                    {/* Only the name may break anywhere; the type and size stay whole. */}
                    <span className="break-all">{file.originalFileName}</span>
                    <span className="whitespace-nowrap text-ink-muted">
                      {type && (
                        <>
                          <span aria-hidden="true"> · </span>
                          <span className="sr-only">, </span>
                          {type}
                        </>
                      )}
                      <span aria-hidden="true"> · </span>
                      <span className="sr-only">, </span>
                      {formatFileSize(file.sizeBytes)}
                    </span>
                  </p>
                  <ActionButton
                    className="shrink-0 self-start sm:self-auto"
                    aria-disabled={isDownloading || undefined}
                    onClick={() => void downloads.download({ id: file.attachmentId, originalFileName: file.originalFileName })}
                  >
                    {isDownloading ? 'Downloading…' : 'Download'}
                    <span className="sr-only"> {file.originalFileName}</span>
                  </ActionButton>
                </div>
                {failed && (
                  <p role="alert" className="mt-1 text-sm text-danger">
                    {failed}
                  </p>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </article>
  )
}

/**
 * The student's own submission for an assignment: the form for an attempt, and every earlier attempt with
 * its text, files, and whether it was on time. Each hand-in is a new attempt; nothing earlier is replaced.
 * This section loads on its own: if it fails, the assignment above stays as it is.
 */
export function AssignmentSubmission({ lmsCourseId, assignmentId, dueAt }: AssignmentSubmissionProps) {
  const history = useMySubmissions(lmsCourseId, assignmentId)
  const downloads = useFileDownload(downloadSubmissionAttachment)
  const sectionRef = useRef<HTMLElement>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const [focusAttempt, setFocusAttempt] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const attempts = history.status === 'loaded' ? history.data.attempts : null
  const latest = attempts?.[0] ?? null

  // After a new attempt, focus moves to it once the reloaded history shows it.
  useEffect(() => {
    if (focusAttempt === null) {
      return
    }
    const heading = sectionRef.current?.querySelector<HTMLElement>(`[data-attempt-number="${focusAttempt}"]`)
    if (heading) {
      heading.focus()
      setFocusAttempt(null)
    }
  }, [attempts, focusAttempt])

  async function submit(textContent: string, files: File[]): Promise<SubmitOutcome> {
    setAnnouncement('')
    try {
      const attempt = await submitAssignment(lmsCourseId, assignmentId, textContent, files)
      setFormOpen(false)
      setNow(Date.now())
      setAnnouncement(`Attempt ${attempt.attemptNumber} submitted${attempt.late ? ' and marked late' : ''}.`)
      setFocusAttempt(attempt.attemptNumber)
      history.refresh()
      return { ok: true }
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status === 401) {
        // The shared client has ended the session; the sign-in page is on its way.
        return { ok: false, message: null, fieldErrors: {} }
      }
      const apiError = getApiError(failure)
      const fieldErrors = Object.fromEntries((apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]))
      const message = Object.keys(fieldErrors).length > 0 && status === 400
        ? `Check the highlighted field. ${NOT_CHANGED}`
        : refusal(status, apiError?.message ?? null)
      return { ok: false, message, fieldErrors }
    }
  }

  const pastDue = isPast(dueAt, now)

  return (
    <section ref={sectionRef} aria-labelledby="your-submission-heading" className="mt-8 max-w-2xl border-t border-line pt-6">
      <h2 id="your-submission-heading" className="text-base font-semibold text-ink">
        Your submission
      </h2>

      <p role="status" className="sr-only">
        {announcement}
      </p>

      <div className="mt-3 space-y-4">
        {history.status === 'loading' && (
          <p role="status" className="text-sm text-ink-muted">
            Loading submission history…
          </p>
        )}

        {history.status === 'failed' && (
          <div role="alert" className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-ink">Couldn’t load your submission history.</p>
            <ActionButton onClick={history.retry}>Retry</ActionButton>
          </div>
        )}

        {history.status === 'denied' && (
          <p className="text-sm leading-6 text-ink-muted">
            You don’t have access to submit this assignment. If your access changed recently, sign out and sign in again.
          </p>
        )}

        {history.status === 'notFound' && (
          <p className="text-sm text-ink-muted">This assignment is not available for submission.</p>
        )}

        {attempts !== null && attempts.length === 0 && (
          <>
            <p className="text-sm text-ink-muted">No submission yet.</p>
            <SubmissionForm isResubmission={false} isPastDue={pastDue} onSubmit={submit} />
          </>
        )}

        {latest !== null && (
          <>
            <p className="text-sm leading-6 text-ink">
              Latest: attempt {latest.attemptNumber}, submitted{' '}
              <time dateTime={latest.submittedAt}>{formatDateTime(latest.submittedAt)}</time>,{' '}
              {latest.late ? 'late' : 'on time'}.
            </p>
            {formOpen ? (
              <SubmissionForm
                isResubmission
                isPastDue={pastDue}
                onSubmit={submit}
                onCancel={() => setFormOpen(false)}
              />
            ) : (
              <div className="w-full sm:w-auto">
                <Button variant="secondary" onClick={() => setFormOpen(true)}>
                  Submit another attempt
                </Button>
              </div>
            )}
          </>
        )}

        {attempts !== null && attempts.length > 0 && (
          <div>
            <h3 className="text-sm font-semibold text-ink">Submission history</h3>
            <p className="mt-0.5 text-xs text-ink-muted">Newest first. Earlier attempts are kept as they were.</p>
            <ol aria-label="Submission history" className="mt-2 divide-y divide-line rounded-panel border border-line bg-surface">
              {attempts.map((attempt, index) => (
                <li key={attempt.attemptId}>
                  <AttemptItem attempt={attempt} isLatest={index === 0} downloads={downloads} />
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>
    </section>
  )
}
