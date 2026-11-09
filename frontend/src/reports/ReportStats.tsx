import type { ReactNode } from 'react'
import { formatCount } from './reportFormat'

/**
 * A compact list of labelled counts. Each number keeps its label as text (a definition list), so nothing depends on
 * position, colour, or a chart.
 */
export function ReportStats({ items, label }: { items: { label: string; value: number }[]; label?: string }) {
  return (
    <dl aria-label={label} className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-4">
      {items.map((item) => (
        <div key={item.label} className="min-w-0">
          <dt className="text-xs font-medium break-words text-ink-muted">{item.label}</dt>
          <dd className="text-lg font-semibold text-ink tabular-nums">{formatCount(item.value)}</dd>
        </div>
      ))}
    </dl>
  )
}

/** A titled report section with a restrained border. */
export function ReportSection({ id, title, children, note }: { id: string; title: string; children: ReactNode; note?: string }) {
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <h3 id={id} className="text-sm font-semibold text-ink">
        {title}
      </h3>
      {note && <p className="mt-0.5 text-xs text-ink-muted">{note}</p>}
      <div className="mt-3">{children}</div>
    </section>
  )
}
