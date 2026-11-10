import type { VisitStatus } from '../../types/hostel'

const STATUS: Record<VisitStatus, string> = { EXPECTED: 'Expected', ARRIVED: 'Inside', LEFT: 'Left', CANCELLED: 'Cancelled' }

export function visitStatusLabel(status: VisitStatus): string {
  return STATUS[status] ?? status
}

/** "16:00" from a "16:00:00" time. */
export function shortTime(time: string): string {
  return time.slice(0, 5)
}
