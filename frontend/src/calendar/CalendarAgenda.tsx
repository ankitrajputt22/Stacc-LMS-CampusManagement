import { Link } from 'react-router'
import { actionLinkClasses } from '../components/actionStyles'
import { marksText } from '../courses/marks'
import type {
  AcademicCalendarEventSummary,
  FacultyAssignmentDeadline,
  MyCalendarResponse,
  StudentAssignmentDeadline,
} from '../types/calendar'
import { dateRangeText, dayHeading, localDayOf, timeText, todayIsoDate } from './calendarDates'
import { categoryLabel, scopeText, tagClasses } from './calendarText'

type AgendaItem =
  | { kind: 'event'; key: string; event: AcademicCalendarEventSummary }
  | { kind: 'student'; key: string; dueAt: number; deadline: StudentAssignmentDeadline }
  | { kind: 'faculty'; key: string; dueAt: number; deadline: FacultyAssignmentDeadline }

type Day = { date: string; items: AgendaItem[] }

const itemLinkClasses =
  'font-semibold break-words text-ink underline-offset-4 hover:text-stacc-primary hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

/**
 * The month's items under one heading per day. An event over several days is listed once, on its first day in the
 * month, with its whole date range; a deadline is listed on the day it falls on in the browser's time zone. Within a
 * day, events come first in the backend's order, then deadlines by time.
 */
function agendaDays(calendar: MyCalendarResponse): Day[] {
  const days = new Map<string, AgendaItem[]>()
  const add = (date: string, item: AgendaItem) => days.set(date, [...(days.get(date) ?? []), item])

  for (const event of calendar.academicEvents) {
    // An event that began before the month is listed on the month's first day.
    add(event.startDate < calendar.fromDate ? calendar.fromDate : event.startDate, {
      kind: 'event',
      key: `event-${event.eventId}`,
      event,
    })
  }
  for (const deadline of calendar.studentAssignmentDeadlines) {
    add(localDayOf(deadline.dueAt), {
      kind: 'student',
      key: `student-${deadline.assignmentId}`,
      dueAt: Date.parse(deadline.dueAt),
      deadline,
    })
  }
  for (const deadline of calendar.facultyAssignmentDeadlines) {
    add(localDayOf(deadline.dueAt), {
      kind: 'faculty',
      key: `faculty-${deadline.assignmentId}`,
      dueAt: Date.parse(deadline.dueAt),
      deadline,
    })
  }

  // Events first, then deadlines by time. The sort is stable, so events keep the backend's order and a student
  // deadline stays before a teaching one due at the same moment.
  const compare = (a: AgendaItem, b: AgendaItem) => {
    if (a.kind === 'event' || b.kind === 'event') {
      return (a.kind === 'event' ? 0 : 1) - (b.kind === 'event' ? 0 : 1)
    }
    return a.dueAt - b.dueAt
  }
  return [...days.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, items]) => ({ date, items: [...items].sort(compare) }))
}

function Separator() {
  return (
    <>
      <span aria-hidden="true"> · </span>
      <span className="sr-only">, </span>
    </>
  )
}

function EventItem({ event }: { event: AcademicCalendarEventSummary }) {
  const cancelled = event.status === 'CANCELLED'
  return (
    <li className={`border-l-2 py-1 pl-3 ${cancelled ? 'border-line-strong' : 'border-stacc-primary'}`}>
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className={tagClasses}>{categoryLabel(event.category)}</span>
        {/* Cancelled is said in words, not only by colour or a line through the title. */}
        {cancelled && <span className={`${tagClasses} border-ink-muted`}>Cancelled</span>}
        <span className={`text-sm font-semibold break-words ${cancelled ? 'text-ink-muted' : 'text-ink'}`}>{event.title}</span>
      </p>
      <p className="mt-0.5 text-xs leading-5 break-words text-ink-muted">
        {dateRangeText(event.startDate, event.endDate)}
        <Separator />
        {scopeText(event)}
      </p>
      {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
      {event.description && (
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{event.description}</p>
      )}
    </li>
  )
}

function StudentDeadlineItem({ deadline, label }: { deadline: StudentAssignmentDeadline; label: string }) {
  return (
    <li className="border-l-2 border-line-strong py-1 pl-3">
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className={tagClasses}>{label}</span>
        <Link to={`/courses/${deadline.lmsCourseId}/assignments/${deadline.assignmentId}`} className={`text-sm ${itemLinkClasses}`}>
          {deadline.title}
          <span className="sr-only">, {deadline.courseName}</span>
        </Link>
      </p>
      <p className="mt-0.5 flex flex-wrap gap-x-2 text-xs leading-5 text-ink-muted">
        <span>{deadline.courseCode}</span>
        <span>
          Due <time dateTime={deadline.dueAt}>{timeText(deadline.dueAt)}</time>
        </span>
        <span>{marksText(deadline.maxMarks)}</span>
      </p>
    </li>
  )
}

function FacultyDeadlineItem({ deadline }: { deadline: FacultyAssignmentDeadline }) {
  return (
    <li className="flex flex-col gap-2 border-l-2 border-line-strong py-1 pl-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className={tagClasses}>Teaching deadline</span>
          <span className={tagClasses}>{deadline.status === 'DRAFT' ? 'Draft' : 'Published'}</span>
          <span className="text-sm font-semibold break-words text-ink">{deadline.title}</span>
        </p>
        <p className="mt-0.5 flex flex-wrap gap-x-2 text-xs leading-5 text-ink-muted">
          <span>{deadline.courseCode}</span>
          <span>
            Due <time dateTime={deadline.dueAt}>{timeText(deadline.dueAt)}</time>
          </span>
          <span>{marksText(deadline.maxMarks)}</span>
        </p>
      </div>
      {/* The teaching course page is where assignments are managed; the calendar is not a second editor. */}
      <Link to={`/teaching/${deadline.lmsCourseId}`} className={`${actionLinkClasses} self-start`}>
        Open teaching course
        <span className="sr-only">
          {' '}
          {deadline.courseCode} for {deadline.title}
        </span>
      </Link>
    </li>
  )
}

type CalendarAgendaProps = {
  calendar: MyCalendarResponse
  /** Whether student deadlines need telling apart from teaching ones, for an account that is both. */
  alsoTeaches: boolean
}

/** The agenda of one month: a heading per day with its events and deadlines, one column on every screen size. */
export function CalendarAgenda({ calendar, alsoTeaches }: CalendarAgendaProps) {
  const today = todayIsoDate()
  return (
    <div className="space-y-4">
      {agendaDays(calendar).map((day) => (
        <section
          key={day.date}
          aria-labelledby={`day-${day.date}`}
          className="rounded-panel border border-line bg-surface px-4 py-3 sm:px-5"
        >
          <h3 id={`day-${day.date}`} className="flex flex-wrap items-center gap-2 text-sm font-semibold text-ink">
            <time dateTime={day.date}>{dayHeading(day.date)}</time>
            {day.date === today && <span className={tagClasses}>Today</span>}
          </h3>
          <ul className="mt-2 space-y-3">
            {day.items.map((item) =>
              item.kind === 'event' ? (
                <EventItem key={item.key} event={item.event} />
              ) : item.kind === 'student' ? (
                <StudentDeadlineItem
                  key={item.key}
                  deadline={item.deadline}
                  label={alsoTeaches ? 'Student deadline' : 'Assignment due'}
                />
              ) : (
                <FacultyDeadlineItem key={item.key} deadline={item.deadline} />
              ),
            )}
          </ul>
        </section>
      ))}
    </div>
  )
}
