import type { ReactNode } from 'react'
import { NavLink } from 'react-router'

export type CourseNavItem = { to: string; label: string; end?: boolean }

const itemClasses =
  'flex min-h-11 items-center rounded-control px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary md:min-h-9'
const idle = 'text-ink hover:bg-surface-muted'
const current = 'bg-surface-muted font-semibold text-portal-navy border-l-[3px] border-portal-navy'

function Items({ items }: { items: readonly CourseNavItem[] }) {
  return (
    <ul className="space-y-0.5">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink to={item.to} end={item.end} className={({ isActive }) => `${itemClasses} ${isActive ? current : idle}`}>
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

/**
 * The course's own navigation, the same on every page of a course: a left column on wide screens and a "Course menu"
 * that opens on phones, so content always gets the full width there. It lists only what Stacc has; it is never access
 * control, since the backend checks every request.
 */
export function CourseNav({ title, items }: { title: ReactNode; items: readonly CourseNavItem[] }) {
  return (
    <>
      <details className="mb-4 rounded-panel border border-line bg-surface md:hidden">
        <summary className="flex min-h-11 cursor-pointer items-center px-4 text-sm font-semibold text-ink">
          Course menu<span className="sr-only">: {title}</span>
        </summary>
        <nav aria-label="Course navigation" className="border-t border-line p-2">
          <Items items={items} />
        </nav>
      </details>
      <div className="hidden md:block">
        <p className="mb-2 px-3 text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">{title}</p>
        <nav aria-label="Course navigation">
          <Items items={items} />
        </nav>
      </div>
    </>
  )
}

/** The page frame of a course: its navigation beside the content on wide screens, above it on phones. */
export function CourseFrame({ nav, children }: { nav: ReactNode; children: ReactNode }) {
  return (
    <div className="md:grid md:grid-cols-[11rem_minmax(0,1fr)] md:gap-8 lg:grid-cols-[12rem_minmax(0,1fr)]">
      <aside className="md:sticky md:top-4 md:self-start">{nav}</aside>
      <div className="min-w-0">{children}</div>
    </div>
  )
}
