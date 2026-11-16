import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'

export type CourseNavItem = { to: string; label: string; end?: boolean }

// One line icon per course section, by its label; a section without one shows none.
const ICONS: Record<string, NavIconName> = {
  Home: 'home',
  Modules: 'academics',
  Pages: 'documents',
  Assignments: 'assignments',
  Quizzes: 'support',
  Grades: 'reports',
  Gradebook: 'reports',
  Announcements: 'campus',
  Discussions: 'clubs',
  Messages: 'inbox',
  Files: 'projects',
  Syllabus: 'library',
  'AI Study Assistant': 'ai',
  'Teacher AI': 'ai',
  Rubrics: 'todo',
  Analytics: 'health',
  Outcomes: 'grievances',
  'Question bank': 'exams',
  'Exam papers': 'exams',
}

const itemClasses =
  'flex min-h-11 items-center gap-3 rounded-lg border-l-[3px] px-3 text-[0.95rem] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary md:min-h-10'
const idle = 'border-transparent text-ink hover:bg-surface-muted'
const current = 'border-portal-navy bg-surface-muted font-semibold text-portal-navy'

function Items({ items }: { items: readonly CourseNavItem[] }) {
  return (
    <ul className="space-y-0.5">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink to={item.to} end={item.end} className={({ isActive }) => `${itemClasses} ${isActive ? current : idle}`}>
            {ICONS[item.label] && <NavIcon name={ICONS[item.label]} className="size-5" />}
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

type CourseNavProps = {
  /** The course code, such as CS301. */
  title: ReactNode
  /** The course name under the code. */
  subtitle?: ReactNode
  /** The way back to the list of courses. */
  back?: { to: string; label: string }
  items: readonly CourseNavItem[]
}

/**
 * The course's own navigation, the same on every page of a course: a slim white column on wide screens and a "Course
 * menu" that opens on phones, so content always gets the full width there. It lists only what Stacc has; it is never
 * access control, since the backend checks every request.
 */
export function CourseNav({ title, subtitle, back, items }: CourseNavProps) {
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
      <div className="hidden rounded-panel border border-line bg-surface px-2 py-4 md:block">
        {back && (
          <Link to={back.to} className="mb-4 flex items-center gap-2 rounded-md px-3 text-sm text-ink hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
            <span aria-hidden="true">←</span>
            {back.label}
          </Link>
        )}
        <div className="mb-4 px-3">
          <p className="text-lg font-bold break-words text-ink">{title}</p>
          {subtitle && <p className="text-[0.95rem] break-words text-ink">{subtitle}</p>}
        </div>
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
    <div className="md:grid md:grid-cols-[13rem_minmax(0,1fr)] md:gap-6 lg:grid-cols-[14rem_minmax(0,1fr)]">
      <aside className="md:sticky md:top-20 md:self-start">{nav}</aside>
      <div className="min-w-0">{children}</div>
    </div>
  )
}
