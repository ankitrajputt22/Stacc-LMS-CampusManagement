import type { ReactNode } from 'react'
import { Link } from 'react-router'

type DashboardSectionProps = {
  id: string
  title: string
  /** 2 on a dashboard of its own; 3 when it sits under a dashboard heading of an account with several roles. */
  level: 2 | 3
  /** The full page for this section, such as /courses. */
  link?: { to: string; label: string }
  children: ReactNode
}

const linkClasses =
  'inline-flex min-h-11 items-center rounded-control text-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

/** One dashboard section: a heading with an optional link to the full page, then its preview. */
export function DashboardSection({ id, title, level, link, children }: DashboardSectionProps) {
  const Heading = level === 2 ? 'h2' : 'h3'
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <Heading id={id} className="text-base font-semibold text-ink">
          {title}
        </Heading>
        {link && (
          <Link to={link.to} className={linkClasses}>
            {link.label}
          </Link>
        )}
      </div>
      <div className="mt-3">{children}</div>
    </section>
  )
}

/** A short, quiet empty line inside a section. */
export function SectionEmpty({ children }: { children: ReactNode }) {
  return <p className="text-sm leading-6 text-ink-muted">{children}</p>
}

/** "Showing 5 of 8." under a preview that is shorter than the full count. */
export function PreviewNote({ shown, total }: { shown: number; total: number }) {
  return shown < total ? <p className="mt-3 text-xs text-ink-muted">{`Showing ${shown} of ${total}.`}</p> : null
}
