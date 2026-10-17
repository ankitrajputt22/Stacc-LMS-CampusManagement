const STATUS_WORDS: Record<string, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  PLANNED: 'Planned',
  CLOSED: 'Closed',
}

/** A master's status as a word, such as "Active" or "Closed". */
export function statusWord(status: string): string {
  return STATUS_WORDS[status] ?? status
}

/** Status choices for a select, as words. */
export function statusOptions(statuses: readonly string[]) {
  return statuses.map((status) => ({ value: status, label: statusWord(status) }))
}
