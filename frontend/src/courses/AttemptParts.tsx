import { ActionButton } from '../components/ActionButton'
import type { LmsSubmissionAttachment, LmsSubmissionGrade } from '../types/lms'
import { formatDateTime } from './dates'
import { formatMarks } from './marks'
import { fileTypeLabel, formatFileSize } from './materials'
import type { useFileDownload } from './useFileDownload'

/** On time or late, as the backend decided it for one attempt. Words, never colour alone. */
export function TimingTag({ late }: { late: boolean }) {
  return (
    <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
      {late ? 'Late' : 'On time'}
    </span>
  )
}

type AttemptFilesProps = {
  attemptNumber: number
  files: LmsSubmissionAttachment[]
  downloads: ReturnType<typeof useFileDownload>
}

/**
 * The files of one attempt, each with its name, type, size, and Download. Downloads go through the API with
 * the signed-in user's token, never to storage. A failure is shown under that file only.
 */
export function AttemptFiles({ attemptNumber, files, downloads }: AttemptFilesProps) {
  if (files.length === 0) {
    return null
  }
  return (
    <ul aria-label={`Files of attempt ${attemptNumber}`} className="mt-2 divide-y divide-line rounded-control border border-line">
      {files.map((file) => {
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
  )
}

/**
 * One attempt's grade: the marks out of the maximum it was graded out of, the feedback as plain text when
 * there is any, and when it was saved. Without a grade it says so plainly.
 */
export function GradeSummary({ grade }: { grade: LmsSubmissionGrade | null }) {
  if (grade === null) {
    return <p className="mt-2 text-sm text-ink-muted">Not graded yet</p>
  }
  return (
    <div className="mt-2 rounded-control border border-line bg-surface-muted px-3 py-2">
      <p className="text-sm text-ink">
        <span className="font-semibold">Grade</span> {formatMarks(grade.marksAwarded)} / {formatMarks(grade.maxMarksSnapshot)}
      </p>
      {grade.feedback && (
        <div className="mt-1.5">
          <p className="text-xs font-semibold text-ink-muted">Feedback</p>
          {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
          <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">{grade.feedback}</p>
        </div>
      )}
      <p className="mt-1 text-xs text-ink-muted">
        Graded <time dateTime={grade.gradedAt}>{formatDateTime(grade.gradedAt)}</time>
      </p>
    </div>
  )
}
