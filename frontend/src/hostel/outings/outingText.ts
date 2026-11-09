import type { OutingKind } from '../../types/outings'

export const KIND_LABEL: Record<OutingKind, string> = { GATE_PASS: 'Gate pass', LEAVE: 'Leave' }

const STATUS: Record<string, string> = {
  PENDING: 'Waiting for approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

export function outingStatusLabel(status: string): string {
  return STATUS[status] ?? status
}

/** A datetime-local value (browser time) as an ISO moment with its zone, or null when empty or invalid. */
export function localToIso(value: string): string | null {
  if (value === '') {
    return null
  }
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? null : time.toISOString()
}
