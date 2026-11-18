import type { ReactNode } from 'react'
import { Card } from '../ui/Card'

type DashboardSectionProps = {
  id: string
  title: string
  level: 2 | 3
  link?: { to: string; label: string }
  children: ReactNode
}

export function DashboardSection({ id, title, level, link, children }: DashboardSectionProps) {
  return (
    <div id={id}>
      <Card title={title} level={level} action={link}>
        {children}
      </Card>
    </div>
  )
}

export function SectionEmpty({ children }: { children: ReactNode }) {
  return <p className="text-sm leading-6 text-ink-muted">{children}</p>
}

export function PreviewNote({ shown, total }: { shown: number; total: number }) {
  return shown < total ? <p className="mt-3 text-xs text-ink-muted">{`Showing ${shown} of ${total}.`}</p> : null
}
