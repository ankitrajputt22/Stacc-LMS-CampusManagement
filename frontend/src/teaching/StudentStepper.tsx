import type { To } from 'react-router'
import { ButtonLink } from '../ui'

export type StepperEntry = { key: number; label: string; to: To }

/**
 * Previous and Next links through the students on a grading screen, in the order the list shows them, with the
 * position. It only moves between records the list already returned; the backend checks each one again.
 */
export function StudentStepper({ entries, currentKey, what }: { entries: StepperEntry[]; currentKey: number | null; what: string }) {
  const index = entries.findIndex((entry) => entry.key === currentKey)
  if (index < 0 || entries.length < 2) {
    return null
  }
  const previous = index > 0 ? entries[index - 1] : null
  const next = index < entries.length - 1 ? entries[index + 1] : null
  return (
    <nav aria-label={`Move between ${what}`} className="flex flex-wrap items-center gap-2">
      {previous && (
        <ButtonLink to={previous.to} variant="secondary" size="sm">
          Previous<span className="sr-only">{`: ${previous.label}`}</span>
        </ButtonLink>
      )}
      <span className="text-sm text-ink-muted">
        {index + 1} of {entries.length}
      </span>
      {next && (
        <ButtonLink to={next.to} variant="secondary" size="sm">
          Next<span className="sr-only">{`: ${next.label}`}</span>
        </ButtonLink>
      )}
    </nav>
  )
}
