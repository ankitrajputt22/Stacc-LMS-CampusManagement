import { Link } from 'react-router'
import { formatMarks } from '../courses/marks'
import { courseColor } from './courseColors'

export type PlannerItem = {
  key: string
  /** The course the colour and label come from. */
  colorId: number
  courseCode: string
  courseName: string
  title: string
  to: string
  dueAt: string
  maxMarks: number
  /** A short word beside the item, such as Draft, when it matters. */
  tag?: string
}

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })

function dayKey(time: Date): string {
  return `${time.getFullYear()}-${time.getMonth()}-${time.getDate()}`
}

function AssignmentGlyph({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" className="mt-1 size-5 shrink-0" fill="none" stroke={color} strokeWidth="1.6" strokeLinejoin="round">
      <path d="M5 3.5h10l4 4v13H5z" />
      <path d="M8.5 11h7M8.5 14.5h7M8.5 18h4" />
    </svg>
  )
}

/**
 * Items with a deadline, planner style: one heading per local day in time order, then each item with its course's
 * colour tag, the course and kind, the title linking to its page, and the marks and local due time. Only what the
 * backend sent; days without items are not shown.
 */
export function PlannerList({ items, headingLevel }: { items: PlannerItem[]; headingLevel: 3 | 4 }) {
  const Heading = headingLevel === 3 ? 'h3' : 'h4'
  const days: { key: string; label: string; items: PlannerItem[] }[] = []
  for (const item of [...items].sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt))) {
    const due = new Date(item.dueAt)
    const key = dayKey(due)
    const last = days.at(-1)
    if (last && last.key === key) {
      last.items.push(item)
    } else {
      days.push({ key, label: dayFormat.format(due), items: [item] })
    }
  }

  return (
    <div className="space-y-5">
      {days.map((day) => (
        <div key={day.key}>
          <Heading className="border-b border-line pb-1.5 text-base font-semibold text-ink">{day.label}</Heading>
          <ul className="divide-y divide-line">
            {day.items.map((item) => {
              const color = courseColor(item.colorId)
              return (
                <li key={item.key} className="flex gap-3 py-3">
                  <div
                    aria-hidden="true"
                    className="hidden w-40 shrink-0 flex-col overflow-hidden border bg-surface sm:flex"
                    style={{ borderColor: color }}
                  >
                    <span className="px-2 py-1.5 text-center text-[0.7rem] leading-4 font-semibold break-words uppercase" style={{ color }}>
                      {item.courseCode}: {item.courseName}
                    </span>
                    <span className="mt-auto h-3" style={{ backgroundColor: color }} />
                  </div>
                  <span aria-hidden="true" className="w-1 shrink-0 rounded-full sm:hidden" style={{ backgroundColor: color }} />
                  <AssignmentGlyph color={color} />
                  <div className="min-w-0 flex-1">
                    <p className="text-[0.7rem] leading-4 tracking-[0.02em] break-words text-ink-muted uppercase">
                      {item.courseCode}: {item.courseName} assignment
                      {item.tag && <span className="ml-2 rounded-control border border-line-strong px-1.5 normal-case">{item.tag}</span>}
                    </p>
                    <Link
                      to={item.to}
                      className="text-base break-words text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
                    >
                      {item.title}
                    </Link>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-xl leading-6 text-ink">
                      {formatMarks(item.maxMarks)}
                      <span className="ml-1 text-[0.7rem] text-ink-muted uppercase">{item.maxMarks === 1 ? 'mark' : 'marks'}</span>
                    </p>
                    <p className="text-[0.7rem] text-ink-muted uppercase">
                      Due: <time dateTime={item.dueAt}>{timeFormat.format(new Date(item.dueAt))}</time>
                    </p>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </div>
  )
}
