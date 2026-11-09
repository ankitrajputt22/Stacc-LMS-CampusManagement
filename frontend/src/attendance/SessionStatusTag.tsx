import type { AttendanceSessionStatus } from '../types/attendance'
import { Badge } from '../ui'

const LABELS: Record<AttendanceSessionStatus, string> = {
  OPEN: 'Open',
  FINALIZED: 'Finalized',
}

/** A session's status as a badge: amber while open, green once students can see it. The word always says it. */
export function SessionStatusTag({ status }: { status: AttendanceSessionStatus }) {
  return (
    <Badge tone={status === 'OPEN' ? 'warning' : 'success'}>
      <span className="sr-only">Session status: </span>
      {LABELS[status] ?? status}
    </Badge>
  )
}
