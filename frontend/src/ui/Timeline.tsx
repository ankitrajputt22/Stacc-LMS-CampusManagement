import type { ReactNode } from 'react'
import { formatDateTime } from '../courses/dates'

export type TimelineTone = 'done' | 'current' | 'waiting' | 'danger' | 'neutral'

export type TimelineEntry = {
  key: string | number
  /** What happened, as words. */
  title: ReactNode
  /** A status word beside the title, never only a colour. */
  badge?: ReactNode
  /** Who did it, by login ID. */
  actor?: string | null
  /** When, as an ISO moment. */
  at?: string | null
  /** More detail, such as a comment, shown as given. */
  detail?: ReactNode
  tone?: TimelineTone
}

const DOT: Record<TimelineTone, string> = {
  done: 'border-success bg-success',
  current: 'border-stacc-primary bg-surface',
  waiting: 'border-line-strong bg-surface',
  danger: 'border-danger bg-danger',
  neutral: 'border-line-strong bg-line-strong',
}

/**
 * The one way Stacc shows a history or a sequence of steps: a vertical line with one marker per entry, oldest first,
 * each with its words, who, and when. Colour only repeats what the words say.
 */
export function Timeline({ entries, label, empty = 'Nothing yet.' }: { entries: TimelineEntry[]; label: string; empty?: string }) {
  if (entries.length === 0) {
    return <p className="text-sm text-ink-muted">{empty}</p>
  }
  return (
    <ol aria-label={label} className="relative space-y-4 border-l border-line pl-5">
      {entries.map((entry) => (
        <li key={entry.key} className="relative text-sm">
          <span
            aria-hidden="true"
            className={`absolute top-1 -left-[1.65rem] size-3 rounded-full border-2 ${DOT[entry.tone ?? 'neutral']}`}
          />
          <div className="flex flex-wrap items-center gap-2">
            <span className="break-words font-medium text-ink">{entry.title}</span>
            {entry.badge}
          </div>
          {(entry.actor || entry.at) && (
            <p className="mt-0.5 text-xs text-ink-muted">
              {entry.actor}
              {entry.actor && entry.at && ' · '}
              {entry.at && <time dateTime={entry.at}>{formatDateTime(entry.at)}</time>}
            </p>
          )}
          {entry.detail && <div className="mt-1">{entry.detail}</div>}
        </li>
      ))}
    </ol>
  )
}
