import type { ReactNode } from 'react'
import { formatCount } from './reportFormat'

export function ReportStats({ items, label }: { items: { label: string; value: number }[]; label?: string }) {
  return (
    <dl aria-label={label} className="divide-y divide-line">
      {items.map((item) => (
        <div key={item.label} className="flex items-baseline justify-between gap-4 px-2 py-2.5">
          <dt className="min-w-0 text-[0.95rem] break-words text-ink">{item.label}</dt>
          <dd className="text-xl font-bold text-ink tabular-nums">{formatCount(item.value)}</dd>
        </div>
      ))}
    </dl>
  )
}

export function ReportSubheading({ children }: { children: ReactNode }) {
  return <h4 className="rounded-sm bg-sky-50 px-2 py-1.5 text-base font-bold text-ink">{children}</h4>
}

export function ReportSection({ id, title, children, note }: { id: string; title: string; children: ReactNode; note?: string }) {
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <h3 id={id} className="text-xl font-bold text-ink">
        {title}
      </h3>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
      {note && <p className="mt-2 text-sm text-ink-muted">{note}</p>}
      <div className="mt-3">{children}</div>
    </section>
  )
}
