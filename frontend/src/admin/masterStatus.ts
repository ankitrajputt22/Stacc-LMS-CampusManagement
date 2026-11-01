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
}

/** A record's status as a word, such as "Active" or "Closed". */
export function statusWord(status: string): string {
  return STATUS_WORDS[status] ?? status
}

// Colour only helps: the word always says it. Green for what is in use now, blue for what is still to come, and
// neutral for what has ended.
const STATUS_TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  ENROLLED: 'success',
  PLANNED: 'info',
}

/** The badge tone of a record's status. */
export function statusTone(status: string): BadgeTone {
  return STATUS_TONES[status] ?? 'neutral'
}

/** Status choices for a select, as words. */
export function statusOptions(statuses: readonly string[]) {
  return statuses.map((status) => ({ value: status, label: statusWord(status) }))
}

/** The statuses a planned, active, or closed record may take next: it moves forward only, never back. */
export function lifecycleOptions(current: LifecycleStatus) {
  const allowed: Record<LifecycleStatus, LifecycleStatus[]> = {
    PLANNED: ['PLANNED', 'ACTIVE', 'CLOSED'],
    ACTIVE: ['ACTIVE', 'CLOSED'],
    CLOSED: ['CLOSED'],
  }
  return statusOptions(allowed[current])
}
