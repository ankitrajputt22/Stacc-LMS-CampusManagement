import type { AttendanceSessionStatus } from '../types/attendance'

const LABELS: Record<AttendanceSessionStatus, string> = {
  OPEN: 'Open',
  FINALIZED: 'Finalized',
}

/** A quiet session status label. The status is a word, never only a colour. */
export function SessionStatusTag({ status }: { status: AttendanceSessionStatus }) {
  return (
    <span className="inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink">
      <span className="sr-only">Session status: </span>
      {LABELS[status] ?? status}
    </span>
  )
}
