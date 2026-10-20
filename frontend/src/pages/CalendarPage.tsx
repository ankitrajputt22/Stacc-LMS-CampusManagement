import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getMyCalendar } from '../api/calendarApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { CalendarAgenda } from '../calendar/CalendarAgenda'
import {
  addMonths,
  browserTimeZone,
  type CalendarMonth,
  currentMonth,
  monthKey,
  monthLabel,
  monthRange,
  parseMonth,
  sameMonth,
} from '../calendar/calendarDates'
import { CALENDAR_DENIED, SIGN_IN_AGAIN } from '../calendar/calendarText'
import { ActionButton } from '../components/ActionButton'
import { actionLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'

function MonthControls({ month, onChange }: { month: CalendarMonth; onChange: (month: CalendarMonth | null) => void }) {
  const previous = addMonths(month, -1)
  const next = addMonths(month, 1)
  return (
    <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-3">
      <h2 id="calendar-month" className="mr-auto text-lg font-semibold text-ink">
        {monthLabel(month)}
      </h2>
      <div role="group" aria-label="Change month" className="flex flex-wrap gap-2">
        <ActionButton
          onClick={() => onChange(previous)}
          aria-label={`Previous month, ${monthLabel(previous)}`}
          className="gap-1.5"
        >
          <span aria-hidden="true">‹</span>Previous month
        </ActionButton>
        <ActionButton onClick={() => onChange(null)} aria-label={`Today, ${monthLabel(currentMonth())}`}>
          Today
        </ActionButton>
        <ActionButton onClick={() => onChange(next)} aria-label={`Next month, ${monthLabel(next)}`} className="gap-1.5">
          Next month<span aria-hidden="true">›</span>
        </ActionButton>
      </div>
    </div>
  )
}

function MonthAgenda({ month, alsoTeaches, adminOnly }: { month: CalendarMonth; alsoTeaches: boolean; adminOnly: boolean }) {
  const key = monthKey(month)
  // One month at a time, in the browser's own time zone: the zone only decides which deadlines fall on these days.
  const load = useMemo(() => {
    const { from, to } = monthRange(parseMonth(key) ?? currentMonth())
    const timeZone = browserTimeZone()
    return (signal: AbortSignal) => getMyCalendar({ from, to, timeZone }, signal)
  }, [key])
  const request = useApiRequest(load)

  if (request.status === 'loading') {
    return (
      <StatePanel>
        <LoadingState label="Loading calendar…" />
      </StatePanel>
    )
  }
  if (request.status === 'denied') {
    return (
      <StatePanel>
        <AccessDeniedState title={CALENDAR_DENIED} message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  }
  if (request.status !== 'loaded') {
    return (
      <StatePanel>
        <ErrorState
          title="Couldn’t load the calendar."
          message="Check your connection and try again."
          onRetry={request.retry}
        />
      </StatePanel>
    )
  }

  const calendar = request.data
  const count =
    calendar.academicEvents.length +
    calendar.studentAssignmentDeadlines.length +
    calendar.facultyAssignmentDeadlines.length
  return (
    <>
      <p role="status" className="sr-only">
        {`${monthLabel(month)}: ${count} ${count === 1 ? 'item' : 'items'}.`}
      </p>
      {count === 0 ? (
        <StatePanel>
          <EmptyState
            title={adminOnly ? 'No academic calendar events for this month.' : 'No calendar events or deadlines for this month.'}
          />
        </StatePanel>
      ) : (
        <CalendarAgenda calendar={calendar} alsoTeaches={alsoTeaches} />
      )}
    </>
  )
}

/**
 * The shared calendar at /calendar, one month at a time: the official academic events that apply to the account and,
 * for each role it holds, its assignment deadlines, all from GET /api/calendar/me. Nothing is filtered or added here;
 * the backend decides what is shown. The month is kept in the address (?month=2026-10) so a refresh stays on it. The
 * role checks only choose wording and links; they are not security.
 */
export function CalendarPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const isStudent = hasAnyRole(roles, ['STUDENT'])
  const isFaculty = hasAnyRole(roles, ['FACULTY'])
  const isAdmin = hasAnyRole(roles, ['ADMIN'])
  const [searchParams, setSearchParams] = useSearchParams()
  const month = parseMonth(searchParams.get('month')) ?? currentMonth()

  function changeMonth(target: CalendarMonth | null) {
    // Today, or the current month, needs no parameter.
    setSearchParams(target === null || sameMonth(target, currentMonth()) ? {} : { month: monthKey(target) }, {
      replace: true,
    })
  }

  return (
    <>
      <title>Calendar · Stacc</title>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Calendar</h1>
          <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
            {isStudent || isFaculty
              ? 'Official academic dates and your assignment deadlines, in your time zone.'
              : 'Official academic dates published for the college.'}
          </p>
        </div>
        {isAdmin && (
          <Link to="/admin/academic/calendar" className={`${actionLinkClasses} self-start`}>
            Manage academic calendar
          </Link>
        )}
      </div>

      {isStudent || isFaculty || isAdmin ? (
        <>
          <MonthControls month={month} onChange={changeMonth} />
          <div className="mt-4">
            <MonthAgenda month={month} alsoTeaches={isStudent && isFaculty} adminOnly={isAdmin && !isStudent && !isFaculty} />
          </div>
        </>
      ) : (
        <div className="mt-6">
          <StatePanel>
            <AccessDeniedState
              title="The calendar is for student, faculty, and admin accounts."
              message="If you think you should see it, contact the college administration."
            />
          </StatePanel>
        </div>
      )}
    </>
  )
}
