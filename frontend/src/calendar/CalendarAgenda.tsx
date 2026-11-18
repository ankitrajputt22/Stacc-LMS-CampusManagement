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
import { Badge } from '../ui'
import { categoryLabel, scopeText } from './calendarText'

type AgendaItem =
  | { kind: 'event'; key: string; event: AcademicCalendarEventSummary }
  | { kind: 'student'; key: string; dueAt: number; deadline: StudentAssignmentDeadline }
  | { kind: 'faculty'; key: string; dueAt: number; deadline: FacultyAssignmentDeadline }

type Day = { date: string; items: AgendaItem[] }

const itemLinkClasses =
  'font-semibold break-words text-ink underline-offset-4 hover:text-stacc-primary hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

function agendaDays(calendar: MyCalendarResponse): Day[] {
  const days = new Map<string, AgendaItem[]>()
  const add = (date: string, item: AgendaItem) => days.set(date, [...(days.get(date) ?? []), item])

  for (const event of calendar.academicEvents) {
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

const itemClasses = 'grid gap-2 rounded-control border border-line px-3 py-3 sm:grid-cols-[10rem_minmax(0,1fr)] sm:items-center sm:gap-4'

function EventItem({ event }: { event: AcademicCalendarEventSummary }) {
  const cancelled = event.status === 'CANCELLED'
  return (
    <li className={`${itemClasses} ${cancelled ? 'bg-surface-muted' : ''}`}>
      <span className="flex flex-wrap gap-1.5 sm:flex-col sm:items-start">
        <Badge tone="info">{categoryLabel(event.category)}</Badge>
        {cancelled && <Badge tone="neutral">Cancelled</Badge>}
      </span>
      <div className="min-w-0 sm:border-l sm:border-line sm:pl-4">
      <p className={`text-base font-bold break-words ${cancelled ? 'text-ink-muted' : 'text-ink'}`}>{event.title}</p>
      <p className="mt-0.5 text-sm leading-5 break-words text-ink">
        {dateRangeText(event.startDate, event.endDate)}
        <Separator />
        {scopeText(event)}
      </p>
      {event.description && (
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{event.description}</p>
      )}
      </div>
    </li>
  )
}

function StudentDeadlineItem({ deadline, label }: { deadline: StudentAssignmentDeadline; label: string }) {
  return (
    <li className={itemClasses}>
      <span>
        <Badge tone="warning">{label}</Badge>
      </span>
      <div className="min-w-0 sm:border-l sm:border-line sm:pl-4">
      <p>
        <Link to={`/courses/${deadline.lmsCourseId}/assignments/${deadline.assignmentId}`} className={`text-base ${itemLinkClasses}`}>
          {deadline.title}
          <span className="sr-only">, {deadline.courseName}</span>
        </Link>
      </p>
      <p className="mt-0.5 flex flex-wrap gap-x-3 text-sm leading-5 text-ink">
        <span>{deadline.courseCode}</span>
        <span>
          Due <time dateTime={deadline.dueAt}>{timeText(deadline.dueAt)}</time>
        </span>
        <span>{marksText(deadline.maxMarks)}</span>
      </p>
      </div>
    </li>
  )
}

function FacultyDeadlineItem({ deadline }: { deadline: FacultyAssignmentDeadline }) {
  return (
    <li className="flex flex-col gap-2 rounded-control border border-line px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <Badge tone="warning">Teaching deadline</Badge>
          <Badge tone={deadline.status === 'DRAFT' ? 'neutral' : 'success'}>{deadline.status === 'DRAFT' ? 'Draft' : 'Published'}</Badge>
          <span className="text-sm font-semibold break-words text-ink">{deadline.title}</span>
        </p>
        <p className="mt-0.5 flex flex-wrap gap-x-2 text-sm leading-5 text-ink-muted">
          <span>{deadline.courseCode}</span>
          <span>
            Due <time dateTime={deadline.dueAt}>{timeText(deadline.dueAt)}</time>
          </span>
          <span>{marksText(deadline.maxMarks)}</span>
        </p>
      </div>
      <Link to={`/teaching/${deadline.lmsCourseId}/assignments`} className={`${actionLinkClasses} self-start`}>
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
  alsoTeaches: boolean
}

export function CalendarAgenda({ calendar, alsoTeaches }: CalendarAgendaProps) {
  const today = todayIsoDate()
  return (
    <div className="space-y-4">
      {agendaDays(calendar).map((day) => (
        <section
          key={day.date}
          aria-labelledby={`day-${day.date}`}
          className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5"
        >
          <h3 id={`day-${day.date}`} className="flex flex-wrap items-center gap-2 text-lg font-bold text-portal-navy">
            <time dateTime={day.date}>{dayHeading(day.date)}</time>
            {day.date === today && <Badge tone="brand">Today</Badge>}
          </h3>
          <span aria-hidden="true" className="mt-1 block h-0.5 w-12 rounded bg-portal-cta" />
          <ul className="mt-3 space-y-3">
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
