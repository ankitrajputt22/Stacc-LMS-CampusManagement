import { useQueryClient } from '@tanstack/react-query'
import { useId, useRef, useState } from 'react'
import { getAcademicSessions, getSemesters } from '../api/adminAcademicApi'
import {
  cancelCalendarEvent,
  createCalendarEvent,
  getCalendarEvents,
  publishCalendarEvent,
  updateCalendarEvent,
} from '../api/calendarApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { dateRangeText } from '../calendar/calendarDates'
import {
  CALENDAR_DENIED,
  categoryLabel,
  EVENT_STATUSES,
  eventStatusLabel,
  eventStatusTone,
  scopeText,
  SIGN_IN_AGAIN,
} from '../calendar/calendarText'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { usePendingFocus } from '../components/usePendingFocus'
import { formatDateTime } from '../courses/dates'
import type { AdminCalendarEvent, CalendarEventStatus } from '../types/calendar'
import { CalendarEventForm, type CalendarEventValues } from './CalendarEventForm'
import { Badge, Button, Card, SelectField, TextField } from '../ui'
import { semesterLabel } from './enrollmentText'

type Open = { type: 'create' } | { type: 'edit'; eventId: number } | null
type Confirming = { type: 'publish' | 'cancel'; eventId: number } | null
type Filters = { session: string; semester: string; status: string; from: string; to: string }

const NO_FILTERS: Filters = { session: '', semester: '', status: '', from: '', to: '' }
const EMPTY_EVENT: CalendarEventValues = {
  academicSessionId: '',
  semesterId: '',
  title: '',
  category: '',
  startDate: '',
  endDate: '',
  description: '',
}

function valuesOf(event: AdminCalendarEvent): CalendarEventValues {
  return {
    academicSessionId: String(event.academicSessionId),
    semesterId: event.semesterId === null ? '' : String(event.semesterId),
    title: event.title,
    category: event.category,
    startDate: event.startDate,
    endDate: event.endDate,
    description: event.description ?? '',
  }
}

function refusalText(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? 'Couldn’t save. Try again.'
    case 403:
      return `${CALENDAR_DENIED} If your access changed recently, sign out and sign in again.`
    default:
      return 'Couldn’t save. Check your connection and try again.'
  }
}

/** When the event was published or cancelled, or that a draft is seen only here. */
function lifecycleText(event: AdminCalendarEvent): string {
  if (event.status === 'DRAFT') {
    return 'Draft: not shown to students or faculty yet.'
  }
  const published = event.publishedAt ? `Published ${formatDateTime(event.publishedAt)}` : 'Never published'
  if (event.status === 'PUBLISHED') {
    return `${published}.`
  }
  return event.publishedAt
    ? `${published}. Cancelled ${formatDateTime(event.cancelledAt ?? '')}: still shown, marked as cancelled.`
    : `Cancelled ${formatDateTime(event.cancelledAt ?? '')} before it was published: never shown.`
}

/**
 * Official academic calendar events: listed with compact filters, added as drafts, edited, published, and cancelled.
 * Nothing is deleted or restored. Assignment deadlines are not listed or edited here: they belong to LMS assignments
 * and only appear on the shared calendar. The backend checks the role and the permissions on every request; a 403
 * never signs the user out.
 */
export function CalendarSection() {
  const id = useId()
  const queryClient = useQueryClient()
  const sectionRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(sectionRef)
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const [open, setOpen] = useState<Open>(null)
  const [confirming, setConfirming] = useState<Confirming>(null)
  const [announcement, setAnnouncement] = useState('')
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const sessionList = sessions.status === 'loaded' ? sessions.data : null
  const semesterList = semesters.status === 'loaded' ? semesters.data : null
  const rangeInvalid = filters.from !== '' && filters.to !== '' && filters.to < filters.from

  const query = {
    academicSessionId: filters.session === '' ? undefined : Number(filters.session),
    semesterId: filters.semester === '' ? undefined : Number(filters.semester),
    status: filters.status === '' ? undefined : (filters.status as CalendarEventStatus),
    from: filters.from === '' ? undefined : filters.from,
    to: filters.to === '' ? undefined : filters.to,
  }
  const request = useApiQuery(rangeInvalid ? null : queryKeys.admin.calendarEvents({ ...filters }), (signal) =>
    getCalendarEvents(query, signal),
  )
  const rows = request.status === 'loaded' ? request.data : null
  const filtered = Object.values(filters).some((value) => value !== '')
  const blocked =
    sessions.status === 'loading' || semesters.status === 'loading'
      ? 'Loading academic sessions and semesters…'
      : sessionList === null || semesterList === null
        ? 'Academic sessions and semesters couldn’t be loaded, so events can’t be added now.'
        : sessionList.length === 0
          ? 'Add an academic session first.'
          : null

  function filter(change: Partial<Filters>) {
    setFilters((current) => ({ ...current, ...change }))
  }

  function finish(message: string, focusKey: string) {
    setAnnouncement(message)
    setOpen(null)
    setConfirming(null)
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.academic })
    focusLater(focusKey)
  }

  const addButton = (focusKey: string) => (
    <Button
      data-focus-key={focusKey}
      aria-disabled={blocked ? true : undefined}
      aria-describedby={blocked ? `${id}-blocked` : undefined}
      onClick={() => {
        if (!blocked) {
          setAnnouncement('')
          setConfirming(null)
          setOpen({ type: 'create' })
        }
      }}
    >
      Add calendar event
    </Button>
  )

  const filterBar = (
    <div className="grid gap-4 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5 lg:grid-cols-3">
      <SelectField
        label="Academic session"
        value={filters.session}
        onChange={(event) => filter({ session: event.target.value, semester: '' })}
      >
        <option value="">All sessions</option>
        {(sessionList ?? []).map((session) => (
          <option key={session.id} value={String(session.id)}>
            {session.code}
          </option>
        ))}
      </SelectField>
      <SelectField label="Semester" value={filters.semester} onChange={(event) => filter({ semester: event.target.value })}>
        <option value="">All semesters</option>
        {(semesterList ?? [])
          .filter((semester) => filters.session === '' || String(semester.academicSessionId) === filters.session)
          .map((semester) => (
            <option key={semester.id} value={String(semester.id)}>
              {semesterLabel(semester)}
            </option>
          ))}
      </SelectField>
      <SelectField label="Status" value={filters.status} onChange={(event) => filter({ status: event.target.value })}>
        <option value="">All statuses</option>
        {EVENT_STATUSES.map((status) => (
          <option key={status} value={status}>
            {eventStatusLabel(status)}
          </option>
        ))}
      </SelectField>
      <TextField label="From" type="date" value={filters.from} onChange={(event) => filter({ from: event.target.value })} />
      <TextField
        label="To"
        type="date"
        value={filters.to}
        error={rangeInvalid ? 'The to date must not be before the from date.' : null}
        onChange={(event) => filter({ to: event.target.value })}
      />
      {filtered && (
        <div className="flex items-end">
          <Button variant="ghost" onClick={() => setFilters(NO_FILTERS)}>
            Clear filters
          </Button>
        </div>
      )}
    </div>
  )

  // Plain render helpers, not components: the form inside a row must keep its state when the section re-renders.
  function actionsFor(event: AdminCalendarEvent) {
    if (event.status === 'CANCELLED') {
      return <p className="mt-2 text-sm text-ink-muted">Cancelled events cannot be changed.</p>
    }
    const name = <span className="sr-only"> {event.title}</span>
    return (
      <div className="mt-2 flex flex-wrap gap-2">
        <Button
          variant="secondary"
          size="sm"
          data-focus-key={`edit-${event.eventId}`}
          onClick={() => {
            setAnnouncement('')
            setConfirming(null)
            setOpen({ type: 'edit', eventId: event.eventId })
          }}
        >
          Edit{name}
        </Button>
        {event.status === 'DRAFT' && (
          <Button
            variant="secondary"
            size="sm"
            data-focus-key={`publish-${event.eventId}`}
            onClick={() => {
              setOpen(null)
              setConfirming({ type: 'publish', eventId: event.eventId })
            }}
          >
            Publish{name}
          </Button>
        )}
        <Button
          variant="secondary"
          size="sm"
          data-focus-key={`cancel-${event.eventId}`}
          onClick={() => {
            setOpen(null)
            setConfirming({ type: 'cancel', eventId: event.eventId })
          }}
        >
          Cancel event{name}
        </Button>
      </div>
    )
  }

  function rowFor(event: AdminCalendarEvent) {
    const editing = open?.type === 'edit' && open.eventId === event.eventId
    const confirm = confirming?.eventId === event.eventId ? confirming.type : null
    return (
      <li key={event.eventId} className="px-4 py-3 sm:px-5">
        <div className="flex flex-wrap items-center gap-2">
          <h3
            tabIndex={-1}
            data-focus-key={`event-${event.eventId}`}
            className={`text-sm font-semibold break-words focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary ${
              event.status === 'CANCELLED' ? 'text-ink-muted' : 'text-ink'
            }`}
          >
            {event.title}
          </h3>
          <Badge tone={eventStatusTone(event.status)}>
            <span className="sr-only">Status: </span>
            {eventStatusLabel(event.status)}
          </Badge>
          <Badge tone="info">
            <span className="sr-only">Category: </span>
            {categoryLabel(event.category)}
          </Badge>
        </div>
        <p className="mt-0.5 text-sm leading-5 break-words text-ink-muted">
          {dateRangeText(event.startDate, event.endDate)}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {scopeText(event)}
        </p>
        {event.description && (
          <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{event.description}</p>
        )}
        <p className="mt-1 text-sm text-ink-muted">{lifecycleText(event)}</p>

        {editing && sessionList !== null && semesterList !== null ? (
          <div className="mt-3">
            <CalendarEventForm
              heading={`Edit ${event.title}`}
              initial={valuesOf(event)}
              sessions={sessionList}
              semesters={semesterList}
              scopeLocked={event.status !== 'DRAFT'}
              submitLabel={event.status === 'DRAFT' ? 'Save draft' : 'Save changes'}
              onSubmit={async (body) => {
                await updateCalendarEvent(event.eventId, body)
                finish(`${body.title} saved.`, `event-${event.eventId}`)
              }}
              onCancel={() => {
                setOpen(null)
                focusLater(`edit-${event.eventId}`)
              }}
            />
          </div>
        ) : (
          actionsFor(event)
        )}

        {confirm === 'publish' && (
          <StatusConfirmation
            question="Publish academic event?"
            detail="It will become visible to relevant students and faculty."
            confirmLabel="Publish"
            busyLabel="Publishing…"
            onConfirm={async () => {
              await publishCalendarEvent(event.eventId)
              finish(`${event.title} published.`, `event-${event.eventId}`)
            }}
            onCancel={() => {
              setConfirming(null)
              focusLater(`publish-${event.eventId}`)
            }}
            describeRefusal={refusalText}
          />
        )}
        {confirm === 'cancel' && (
          <StatusConfirmation
            question="Cancel academic event?"
            detail="If this event was already published, users will continue to see it marked as Cancelled. A cancelled event cannot be changed or published again."
            confirmLabel="Cancel event"
            busyLabel="Cancelling…"
            dismissLabel="Keep event"
            onConfirm={async () => {
              await cancelCalendarEvent(event.eventId)
              finish(`${event.title} cancelled.`, `event-${event.eventId}`)
            }}
            onCancel={() => {
              setConfirming(null)
              focusLater(`cancel-${event.eventId}`)
            }}
            describeRefusal={refusalText}
          />
        )}
      </li>
    )
  }

  return (
    <div ref={sectionRef} className="space-y-4">
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {open?.type === 'create' && sessionList !== null && semesterList !== null && (
        <CalendarEventForm
          heading="New academic calendar event"
          initial={EMPTY_EVENT}
          sessions={sessionList}
          semesters={semesterList}
          scopeLocked={false}
          submitLabel="Save as draft"
          onSubmit={async (body) => {
            await createCalendarEvent(body)
            finish(`${body.title} saved as a draft.`, 'add')
          }}
          onCancel={() => {
            setOpen(null)
            focusLater(rows !== null && rows.length === 0 ? 'add-empty' : 'add')
          }}
        />
      )}

      <Card
        title="Academic calendar events"
        description={
          <>
            Official dates of an academic session, or of one semester in it. Drafts are seen only here; published events
            appear on the calendars of the students and faculty they apply to. Events are cancelled, never deleted.
            Assignment deadlines come from LMS assignments and are not managed here.
            {blocked && (
              <span id={`${id}-blocked`} className="mt-1 block">
                {blocked}
              </span>
            )}
          </>
        }
        action={rows !== null && rows.length > 0 && open?.type !== 'create' ? addButton('add') : undefined}
        padding="none"
      >
        {filterBar}
        {rangeInvalid ? null : request.status === 'loading' ? (
          <LoadingState label="Loading academic calendar events…" />
        ) : request.status === 'denied' ? (
          <AccessDeniedState title={CALENDAR_DENIED} message={SIGN_IN_AGAIN} />
        ) : rows === null ? (
          <ErrorState
            title="Couldn’t load academic calendar events."
            message="Check your connection and try again."
            onRetry={request.retry}
          />
        ) : rows.length === 0 ? (
          open?.type !== 'create' && (
            <EmptyState
              title={filtered ? 'No academic calendar events match these filters.' : 'No academic calendar events yet.'}
              action={filtered ? undefined : addButton('add-empty')}
            />
          )
        ) : (
          <ul aria-label="Academic calendar events" className="divide-y divide-line">
            {rows.map((event) => rowFor(event))}
          </ul>
        )}
      </Card>
    </div>
  )
}
