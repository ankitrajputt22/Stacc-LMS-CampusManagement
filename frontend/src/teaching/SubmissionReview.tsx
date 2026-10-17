import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { downloadFacultySubmissionAttachment } from '../api/lmsGradingApi'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { AttemptFiles, GradeSummary, TimingTag } from '../courses/AttemptParts'
import { formatDateTime } from '../courses/dates'
import { formatMarks } from '../courses/marks'
import { useFileDownload } from '../courses/useFileDownload'
import type { LmsFacultySubmissionAttempt, LmsFacultySubmissionDetail, LmsSubmissionGrade } from '../types/lms'
import { GradeForm } from './GradeForm'
import { useFacultySubmissionDetail } from './useFacultySubmissions'

export const GRADING_DENIED = 'You don’t have access to grade this assignment.'
export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

export const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control text-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

type AttemptReviewProps = {
  attempt: LmsFacultySubmissionAttempt
  isLatest: boolean
  /** The assignment's maximum now. A first grade is out of this; a regrade keeps its own maximum. */
  maxMarks: number
  downloads: ReturnType<typeof useFileDownload>
  onSaved: (attempt: LmsFacultySubmissionAttempt, grade: LmsSubmissionGrade) => void
}

/** One attempt as the student handed it in, its current grade, and the form to grade it. */
function AttemptReview({ attempt, isLatest, maxMarks, downloads, onSaved }: AttemptReviewProps) {
  const headingId = `review-attempt-${attempt.attemptId}-heading`
  const outOf = attempt.grade?.maxMarksSnapshot ?? maxMarks

  return (
    <article aria-labelledby={headingId} className="mt-4 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h3 id={headingId} className="text-sm font-semibold text-ink">
          Attempt {attempt.attemptNumber}
          {isLatest && <span className="sr-only">, latest</span>}
        </h3>
        <TimingTag late={attempt.late} />
        {isLatest && (
          <span className="text-xs text-ink-muted" aria-hidden="true">
            Latest
          </span>
        )}
      </div>
      <p className="mt-0.5 text-xs text-ink-muted">
        Submitted <time dateTime={attempt.submittedAt}>{formatDateTime(attempt.submittedAt)}</time>
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Due then <time dateTime={attempt.dueAtSnapshot}>{formatDateTime(attempt.dueAtSnapshot)}</time>
      </p>

      {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
      {attempt.textContent && (
        <p className="mt-3 text-sm leading-6 break-words whitespace-pre-line text-ink">{attempt.textContent}</p>
      )}
      <AttemptFiles attemptNumber={attempt.attemptNumber} files={attempt.attachments} downloads={downloads} />

      <div className="mt-4">
        <GradeSummary grade={attempt.grade} />
      </div>
      {attempt.grade && attempt.grade.maxMarksSnapshot !== maxMarks && (
        <p className="mt-2 text-sm leading-6 text-ink-muted">
          This attempt was first graded out of {formatMarks(attempt.grade.maxMarksSnapshot)}, so it stays out of{' '}
          {formatMarks(attempt.grade.maxMarksSnapshot)} although the assignment’s maximum is now {formatMarks(maxMarks)}.
        </p>
      )}
      {/* A new form for each attempt, so typed marks never carry over to another attempt. */}
      <GradeForm key={attempt.attemptId} attempt={attempt} outOf={outOf} onSaved={(grade) => onSaved(attempt, grade)} />
    </article>
  )
}

type AttemptPickerProps = {
  attempts: LmsFacultySubmissionAttempt[]
  selectedId: number
  onSelect: (attemptId: number) => void
}

/** A button for each attempt, newest first. Each attempt is graded on its own. */
function AttemptPicker({ attempts, selectedId, onSelect }: AttemptPickerProps) {
  return (
    <div role="group" aria-label="Choose an attempt" className="mt-3 flex flex-wrap gap-2">
      {attempts.map((attempt, index) => {
        const selected = attempt.attemptId === selectedId
        return (
          <button
            key={attempt.attemptId}
            type="button"
            aria-pressed={selected}
            onClick={() => onSelect(attempt.attemptId)}
            className={`inline-flex min-h-11 items-center gap-1.5 rounded-control border px-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8 ${
              selected
                ? 'border-stacc-primary bg-stacc-primary text-white'
                : 'border-line-strong bg-surface text-ink hover:border-slate-400 hover:bg-surface-muted'
            }`}
          >
            Attempt {attempt.attemptNumber}
            {index === 0 && <span className="font-normal"> (latest)</span>}
            <span className="sr-only">, {attempt.grade ? 'graded' : 'not graded yet'}</span>
          </button>
        )
      })}
    </div>
  )
}

function Reviewed({
  detail,
  downloads,
  onSaved,
}: {
  detail: LmsFacultySubmissionDetail
  downloads: ReturnType<typeof useFileDownload>
  onSaved: (attempt: LmsFacultySubmissionAttempt, grade: LmsSubmissionGrade) => void
}) {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const latest = detail.attempts[0]
  // The latest attempt is reviewed first. A choice that is no longer listed falls back to it.
  const selected = detail.attempts.find((attempt) => attempt.attemptId === selectedId) ?? latest
  const count = detail.attempts.length

  return (
    <>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        {count === 1 ? '1 attempt.' : `${count} attempts, newest first.`} Each attempt is graded on its own, and the
        student sees every saved grade.
      </p>
      {count > 1 && <AttemptPicker attempts={detail.attempts} selectedId={selected.attemptId} onSelect={setSelectedId} />}
      <AttemptReview
        attempt={selected}
        isLatest={selected.attemptId === latest.attemptId}
        maxMarks={detail.maxMarks}
        downloads={downloads}
        onSaved={onSaved}
      />
    </>
  )
}

type SubmissionReviewProps = {
  assignmentId: number
  /** Null when the address cannot name a submission: nothing is asked, and it reads as unavailable. */
  submissionId: number | null
  /** The list of submissions, to go back to. */
  listPath: string
  /** Called after a grade is saved, so the list behind this view is up to date. */
  onGraded: () => void
}

/**
 * One student's submission for faculty review: every attempt with its text and files, its grade, and a form
 * to grade it. Files download through the API. The backend checks access and the grading permission on every
 * request; what is shown is only what it sent.
 */
export function SubmissionReview({ assignmentId, submissionId, listPath, onGraded }: SubmissionReviewProps) {
  const detail = useFacultySubmissionDetail(assignmentId, submissionId)
  const downloads = useFileDownload(downloadFacultySubmissionAttachment)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [announcement, setAnnouncement] = useState('')
  const loaded = detail.status === 'loaded'

  // Opening a submission moves focus to it, so keyboard and screen reader users start there.
  useEffect(() => {
    if (loaded) {
      headingRef.current?.focus()
    }
  }, [loaded, submissionId])

  function saved(attempt: LmsFacultySubmissionAttempt, grade: LmsSubmissionGrade) {
    setAnnouncement(
      `Grade saved for attempt ${attempt.attemptNumber}: ${formatMarks(grade.marksAwarded)} out of ${formatMarks(grade.maxMarksSnapshot)}. The student can see it now.`,
    )
    detail.refresh()
    onGraded()
  }

  const back = (
    <Link to={listPath} state={{ fromSubmission: submissionId }} className={`mt-6 ${backLinkClasses}`}>
      Back to submissions
    </Link>
  )

  return (
    <section aria-labelledby="submission-review-heading" className="mt-8 max-w-3xl">
      <h2
        ref={headingRef}
        id="submission-review-heading"
        tabIndex={-1}
        className="text-base font-semibold break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
      >
        {loaded ? `Submission by ${detail.data.studentLoginId}` : 'Submission'}
      </h2>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {detail.status === 'loading' && (
        <div className="mt-3">
          <StatePanel>
            <LoadingState label="Loading submission…" />
          </StatePanel>
        </div>
      )}
      {detail.status === 'notFound' && (
        <p className="mt-2 text-sm leading-6 text-ink-muted">
          This submission could not be found, or it is not available to your account.
        </p>
      )}
      {detail.status === 'denied' && (
        <div className="mt-3">
          <StatePanel>
            <AccessDeniedState title={GRADING_DENIED} message={SIGN_IN_AGAIN} />
          </StatePanel>
        </div>
      )}
      {detail.status === 'failed' && (
        <div className="mt-3">
          <StatePanel>
            <ErrorState
              title="Couldn’t load this submission"
              message="Check your connection and try again."
              onRetry={detail.retry}
            />
          </StatePanel>
        </div>
      )}
      {detail.status === 'loaded' && <Reviewed detail={detail.data} downloads={downloads} onSaved={saved} />}

      {back}
    </section>
  )
}
