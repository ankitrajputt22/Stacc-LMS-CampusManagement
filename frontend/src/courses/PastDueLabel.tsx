/**
 * A quiet "Past due" label for a deadline that has passed. It only describes the date: it says nothing
 * about any student's work, and the assignment stays available.
 */
export function PastDueLabel() {
  return (
    <span className="inline-block shrink-0 rounded-sm border border-line px-1.5 py-0.5 text-xs font-medium text-ink-muted">
      Past due
    </span>
  )
}
