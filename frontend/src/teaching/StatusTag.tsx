import type { LmsContentStatus } from '../types/lms'

const LABELS: Record<LmsContentStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
}

type StatusTagProps = {
  status: LmsContentStatus
  /** Read before the status by screen readers, such as "LMS status". */
  label: string
}

const TONES: Record<string, string> = {
  PUBLISHED: 'border-success-line bg-success-soft text-ink',
  DRAFT: 'border-amber-300 bg-amber-50 text-ink',
}

/** A small status label, tinted by state. The status is a word, never only a colour. */
export function StatusTag({ status, label }: StatusTagProps) {
  return (
    <span className={`inline-block shrink-0 rounded-sm border px-1.5 py-0.5 text-xs font-medium ${TONES[status] ?? 'border-line-strong text-ink'}`}>
      <span className="sr-only">{label}: </span>
      {LABELS[status] ?? status}
    </span>
  )
}
