import { Link } from 'react-router'
import type { CalendarMonth } from './calendarDates'
import { localDayOf } from './calendarDates'
import type { MyCalendarResponse, PersonalCalendarEvent } from '../types/calendar'

type DayItem = { key: string; label: string; to?: string; tone: 'event' | 'cancelled' | 'deadline' | 'personal' }

const TONES: Record<DayItem['tone'], string> = {
  event: 'border-sky-200 bg-sky-50 text-ink',
  cancelled: 'border-line bg-surface-muted text-ink-muted line-through',
  deadline: 'border-amber-200 bg-amber-50 text-ink',
  personal: 'border-emerald-200 bg-emerald-50 text-ink',
}
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function iso(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** Every item of each day of the month: events on each day they cover, deadlines on their local day. */
function itemsByDay(
  month: CalendarMonth,
  calendar: MyCalendarResponse,
  personal: PersonalCalendarEvent[],
): Map<string, DayItem[]> {
  const days = new Map<string, DayItem[]>()
  const last = new Date(month.year, month.month, 0).getDate()
  for (let day = 1; day <= last; day += 1) {
    days.set(iso(month.year, month.month, day), [])
  }
  const add = (day: string, item: DayItem) => days.get(day)?.push(item)
  const spread = (start: string, end: string, item: DayItem) => {
    for (const day of days.keys()) {
      if (day >= start && day <= end) {
        add(day, item)
      }
    }
  }
  calendar.academicEvents.forEach((event) =>
    spread(event.startDate, event.endDate, {
      key: `event-${event.eventId}`,
      label: event.status === 'CANCELLED' ? `Cancelled: ${event.title}` : event.title,
      tone: event.status === 'CANCELLED' ? 'cancelled' : 'event',
    }),
  )
  calendar.studentAssignmentDeadlines.forEach((deadline) =>
    add(localDayOf(deadline.dueAt), {
      key: `due-${deadline.assignmentId}`,
      label: `Due: ${deadline.title}`,
      to: `/courses/${deadline.lmsCourseId}/assignments/${deadline.assignmentId}`,
      tone: 'deadline',
    }),
  )
  calendar.facultyAssignmentDeadlines.forEach((deadline) =>
    add(localDayOf(deadline.dueAt), {
      key: `teaching-${deadline.assignmentId}`,
      label: `Due: ${deadline.title}`,
      to: `/teaching/${deadline.lmsCourseId}/assignments`,
      tone: 'deadline',
    }),
  )
  personal.forEach((event) => spread(event.startDate, event.endDate, { key: `personal-${event.eventId}`, label: event.title, tone: 'personal' }))
  return days
}

/** The month as a grid of weeks, Monday first. Each day lists its items; the agenda view gives the full details. */
export function MonthGrid({
  month,
  calendar,
  personal,
}: {
  month: CalendarMonth
  calendar: MyCalendarResponse
  personal: PersonalCalendarEvent[]
}) {
  const days = itemsByDay(month, calendar, personal)
  const firstWeekday = (new Date(month.year, month.month - 1, 1).getDay() + 6) % 7
  const cells: (string | null)[] = [...Array<null>(firstWeekday).fill(null), ...days.keys()]
  while (cells.length % 7 !== 0) {
    cells.push(null)
  }
  const today = localDayOf(new Date().toISOString())

  return (
    <div className="overflow-hidden rounded-panel border border-line bg-surface">
      <div aria-hidden="true" className="hidden grid-cols-7 border-b border-line bg-surface-muted text-xs font-semibold text-ink-muted sm:grid">
        {WEEKDAYS.map((day) => (
          <div key={day} className="px-2 py-1.5">
            {day}
          </div>
        ))}
      </div>
      <ol className="grid grid-cols-1 sm:grid-cols-7">
        {cells.map((day, index) =>
          day === null ? (
            <li key={`blank-${index}`} aria-hidden="true" className="hidden min-h-24 border-b border-line bg-surface-muted/50 sm:block sm:border-r" />
          ) : (
            <li key={day} className={`min-h-24 border-b border-line p-1.5 sm:border-r ${(days.get(day) ?? []).length === 0 ? 'hidden sm:block' : ''}`}>
              <p className={`text-xs font-semibold ${day === today ? 'text-stacc-primary' : 'text-ink-muted'}`}>
                <time dateTime={day}>
                  <span className="sm:hidden">{new Date(`${day}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}</span>
                  <span className="hidden sm:inline">{Number(day.slice(8))}</span>
                </time>
                {day === today && <span className="sr-only"> (today)</span>}
              </p>
              <ul className="mt-1 space-y-1">
                {(days.get(day) ?? []).map((item) => (
                  <li key={item.key} className={`truncate rounded-sm border px-1 py-0.5 text-xs ${TONES[item.tone]}`} title={item.label}>
                    {item.to ? (
                      <Link to={item.to} className="underline-offset-2 hover:underline">
                        {item.label}
                      </Link>
                    ) : (
                      item.label
                    )}
                  </li>
                ))}
              </ul>
            </li>
          ),
        )}
      </ol>
    </div>
  )
}
