import type { LifecycleStatus } from '../types/admin'
import type { BadgeTone } from '../ui'

const STATUS_WORDS: Record<string, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  PLANNED: 'Planned',
  CLOSED: 'Closed',
  ENROLLED: 'Enrolled',
  COMPLETED: 'Completed',
  WITHDRAWN: 'Withdrawn',
  DROPPED: 'Dropped',
}

export function statusWord(status: string): string {
  return STATUS_WORDS[status] ?? status
}

const STATUS_TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  ENROLLED: 'success',
  PLANNED: 'info',
}

export function statusTone(status: string): BadgeTone {
  return STATUS_TONES[status] ?? 'neutral'
}

export function statusOptions(statuses: readonly string[]) {
  return statuses.map((status) => ({ value: status, label: statusWord(status) }))
}

export function lifecycleOptions(current: LifecycleStatus) {
  const allowed: Record<LifecycleStatus, LifecycleStatus[]> = {
    PLANNED: ['PLANNED', 'ACTIVE', 'CLOSED'],
    ACTIVE: ['ACTIVE', 'CLOSED'],
    CLOSED: ['CLOSED'],
  }
  return statusOptions(allowed[current])
}
