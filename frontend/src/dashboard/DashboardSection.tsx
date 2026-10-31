import type { ReactNode } from 'react'
import { Card } from '../ui/Card'

type DashboardSectionProps = {
  id: string
  title: string
  /** 2 on a dashboard of its own; 3 when it sits under a dashboard heading of an account with several roles. */
  level: 2 | 3
  /** The full page for this section, such as /courses. */
  link?: { to: string; label: string }
  children: ReactNode
}

/** One dashboard section: a Card with a heading and an optional link to the full page, then its preview. */
export function DashboardSection({ id, title, level, link, children }: DashboardSectionProps) {
  return (
    <div id={id}>
      <Card title={title} level={level} action={link}>
        {children}
      </Card>
    </div>
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
