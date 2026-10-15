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

/** A quiet status label. The status is a word, never only a colour. */
export function StatusTag({ status, label }: StatusTagProps) {
  return (
    <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
      <span className="sr-only">{label}: </span>
      {LABELS[status] ?? status}
    </span>
  )
}
