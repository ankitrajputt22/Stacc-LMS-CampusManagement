import type { AttendanceSessionStatus } from '../types/attendance'
import { Badge } from '../ui'

const LABELS: Record<AttendanceSessionStatus, string> = {
  OPEN: 'Open',
  FINALIZED: 'Finalized',
}

export function SessionStatusTag({ status }: { status: AttendanceSessionStatus }) {
  return (
    <Badge tone={status === 'OPEN' ? 'warning' : 'success'}>
      <span className="sr-only">Session status: </span>
      {LABELS[status] ?? status}
    </Badge>
  )
}
