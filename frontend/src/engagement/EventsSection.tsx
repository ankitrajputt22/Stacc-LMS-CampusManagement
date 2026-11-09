import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import {
  cancelEvent,
  createEvent,
  getEventAttendees,
  getEvents,
  getGroups,
  getManagedEvents,
  publishEvent,
  registerForEvent,
  unregisterFromEvent,
} from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { CampusEvent } from '../types/engagement'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField } from '../ui'
import { EVENT_STATUS_LABEL, failureText, localToIso } from './engagementText'

function EventItem({ event, canRegister }: { event: CampusEvent; canRegister: boolean }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const act = (action: () => Promise<unknown>) =>
    void action()
      .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
      .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t save. Try again.')))
  const full = event.capacity !== null && event.registered >= event.capacity
  return (
    <li className="space-y-2 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{event.title}</span>
        {event.status === 'CANCELLED' && <Badge tone="danger">Cancelled</Badge>}
        {event.myRegistration === 'REGISTERED' && <Badge tone="success">You’re going</Badge>}
        {event.myRegistration === 'WAITLISTED' && <Badge tone="info">Waitlist #{event.myWaitlistPosition}</Badge>}
      </div>
      <p className="text-sm text-ink-muted">
        {formatDateTime(event.startsAt)} to {formatDateTime(event.endsAt)}
        {event.venue && ` · ${event.venue}`}
        {event.groupName && ` · ${event.groupName}`}
      </p>
      <p className="text-sm text-ink-muted">
        {event.capacity === null ? `${event.registered} going` : `${event.registered} of ${event.capacity} places taken`}
        {event.waitlisted > 0 && ` · ${event.waitlisted} waiting`}
        {' · '}
        {[event.forStudents && 'students', event.forFaculty && 'faculty'].filter(Boolean).join(' and ')}
        {event.membersOnly && ', members only'}
      </p>
      {event.description && <p className="text-sm break-words whitespace-pre-line text-ink">{event.description}</p>}
      {canRegister && event.status === 'PUBLISHED' && (
        <div className="flex flex-wrap items-center gap-2">
          {event.myRegistration ? (
            <Button size="sm" variant="secondary" onClick={() => act(() => unregisterFromEvent(event.eventId))}>
              {event.myRegistration === 'WAITLISTED' ? 'Leave the waitlist' : 'Give up my place'}
            </Button>
          ) : event.cannotRegisterBecause.length === 0 ? (
            <Button size="sm" onClick={() => act(() => registerForEvent(event.eventId))}>
              {full ? 'Join the waitlist' : 'Register'}
              <span className="sr-only">{` for ${event.title}`}</span>
            </Button>
          ) : (
            <p className="text-sm text-ink-muted">{event.cannotRegisterBecause.join(' ')}</p>
          )}
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  )
}

/** Coming events with registration, the waitlist, and the reasons someone cannot register. */
export function EventsSection({ canRegister, withinDays }: { canRegister: boolean; withinDays?: number }) {
  const events = useApiQuery(queryKeys.engagement.events(), (signal) => getEvents(signal))
  const [search, setSearch] = useState('')
  const [loadedAt] = useState(() => Date.now())
  const term = search.trim().toLowerCase()
  const until = withinDays === undefined ? null : loadedAt + withinDays * 86_400_000
  const shown = events.status === 'loaded'
    ? events.data.filter((event) => (until === null || new Date(event.startsAt).getTime() <= until)
        && (term === '' || `${event.title} ${event.groupName ?? ''} ${event.venue ?? ''}`.toLowerCase().includes(term)))
    : []
  return (
    <Card title={withinDays === undefined ? 'Events' : 'Happening this week'} padding="none">
      {withinDays === undefined && (
        <div className="px-4 pt-3 sm:px-5">
          <TextField label="Search events" value={search} onChange={(event) => setSearch(event.target.value)} />
        </div>
      )}
      {events.status !== 'loaded' ? (
        <SectionState request={events} what="events" />
      ) : shown.length === 0 ? (
        <EmptyState title="No events here." description="Published events appear here." />
      ) : (
        <ul className="divide-y divide-line">
          {shown.map((event) => (
            <EventItem key={event.eventId} event={event} canRegister={canRegister} />
          ))}
        </ul>
      )}
    </Card>
  )
}

function Attendees({ eventId }: { eventId: number }) {
  const attendees = useApiQuery(queryKeys.engagement.attendees(eventId), (signal) => getEventAttendees(eventId, signal))
  if (attendees.status !== 'loaded') {
    return <SectionState request={attendees} what="registrations" />
  }
  return attendees.data.length === 0 ? (
    <p className="text-sm text-ink-muted">Nobody has registered yet.</p>
  ) : (
    <ol className="list-decimal pl-5 text-sm text-ink">
      {attendees.data.map((attendee) => (
        <li key={attendee.loginId}>
          {attendee.loginId}
          {attendee.status === 'WAITLISTED' && <span className="text-ink-muted"> (waitlist)</span>}
        </li>
      ))}
    </ol>
  )
}

/** Events the account organizes: a new draft, Publish (a group event goes to its advisor), Cancel, and who registered. */
export function EventOrganizer({ admin }: { admin: boolean }) {
  const queryClient = useQueryClient()
  const managed = useApiQuery(queryKeys.engagement.managedEvents(), (signal) => getManagedEvents(signal))
  const groups = useApiQuery(queryKeys.engagement.groups(), (signal) => getGroups(signal))
  const myGroups = groups.status === 'loaded' ? groups.data.filter((group) => admin || group.iAmOfficer) : []
  const [form, setForm] = useState({ title: '', description: '', groupId: '', venue: '', startsAt: '', endsAt: '', capacity: '', forStudents: true, forFaculty: false, membersOnly: false })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [cancelling, setCancelling] = useState<CampusEvent | null>(null)
  const [open, setOpen] = useState<number | null>(null)

  if (!admin && groups.status === 'loaded' && myGroups.length === 0) {
    return null
  }

  async function refresh(text: string) {
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const startsAt = localToIso(form.startsAt)
    const endsAt = localToIso(form.endsAt)
    if (!startsAt || !endsAt || form.title.trim() === '' || (!admin && form.groupId === '')) {
      setMessage({ tone: 'error', text: 'Give a title, a start and an end' + (admin ? '.' : ', and choose your group.') })
      return
    }
    try {
      await createEvent({
        title: form.title.trim(), description: form.description.trim() || null, groupId: form.groupId === '' ? null : Number(form.groupId),
        venue: form.venue.trim() || null, startsAt, endsAt, capacity: form.capacity === '' ? null : Number(form.capacity),
        registrationOpensAt: null, registrationClosesAt: null, forStudents: form.forStudents, forFaculty: form.forFaculty, membersOnly: form.membersOnly,
      })
      setForm({ ...form, title: '', description: '', venue: '', startsAt: '', endsAt: '', capacity: '' })
      await refresh('Draft saved. Publish it when it is ready.')
    } catch (caught) {
      const text = failureText(caught, 'Couldn’t save the event.')
      if (text) {
        setMessage({ tone: 'error', text })
      }
    }
  }

  return (
    <Card title="Organize events" description={admin ? 'Every event, and new ones for the college or a group.' : 'Events of the groups you are an officer of.'}>
      <div className="space-y-5">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submit(event)} noValidate>
          <TextField label="Title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
          <SelectField label="Group" value={form.groupId} onChange={(event) => setForm({ ...form, groupId: event.target.value })}>
            <option value="">{admin ? 'College-wide (no group)' : 'Choose a group…'}</option>
            {myGroups.map((group) => (
              <option key={group.groupId} value={group.groupId}>{group.name}</option>
            ))}
          </SelectField>
          <TextField label="Starts" type="datetime-local" value={form.startsAt} onChange={(event) => setForm({ ...form, startsAt: event.target.value })} />
          <TextField label="Ends" type="datetime-local" value={form.endsAt} onChange={(event) => setForm({ ...form, endsAt: event.target.value })} />
          <TextField label="Venue (optional)" value={form.venue} maxLength={200} onChange={(event) => setForm({ ...form, venue: event.target.value })} />
          <TextField label="Places (optional)" inputMode="numeric" value={form.capacity} onChange={(event) => setForm({ ...form, capacity: event.target.value.replace(/\D/g, '') })} />
          <div className="sm:col-span-2">
            <TextAreaField label="Description (optional)" rows={2} value={form.description} maxLength={10_000} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </div>
          <fieldset className="flex flex-wrap gap-4 text-sm text-ink sm:col-span-2">
            <legend className="sr-only">Who may come</legend>
            {(['forStudents', 'forFaculty', 'membersOnly'] as const).map((key) => (
              <label key={key} className="inline-flex items-center gap-2">
                <input type="checkbox" className="size-4" checked={form[key]} onChange={(event) => setForm({ ...form, [key]: event.target.checked })} />
                {key === 'forStudents' ? 'Students' : key === 'forFaculty' ? 'Faculty' : 'Group members only'}
              </label>
            ))}
          </fieldset>
          <div className="sm:col-span-2">
            <Button type="submit">Save draft</Button>
          </div>
        </form>
        {cancelling && (
          <StatusConfirmation
            question={`Cancel “${cancelling.title}”?`}
            detail="It stays listed as cancelled, and registrations are kept. This cannot be undone."
            confirmLabel="Cancel event"
            dismissLabel="Keep event"
            describeRefusal={(status, text) => (text && status !== null && status < 500 ? text : 'Couldn’t cancel the event.')}
            onCancel={() => setCancelling(null)}
            onConfirm={async () => {
              await cancelEvent(cancelling.eventId)
              setCancelling(null)
              await refresh('Event cancelled.')
            }}
          />
        )}
        {managed.status !== 'loaded' ? (
          <SectionState request={managed} what="your events" />
        ) : managed.data.length === 0 ? (
          <EmptyState title="No events yet." description="Save a draft above." />
        ) : (
          <ul className="divide-y divide-line rounded-panel border border-line">
            {managed.data.map((event) => (
              <li key={event.eventId} className="space-y-2 px-4 py-3 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{event.title}</span>
                  <Badge tone={event.status === 'PUBLISHED' ? 'success' : event.status === 'CANCELLED' ? 'danger' : 'info'}>{EVENT_STATUS_LABEL[event.status]}</Badge>
                  <span className="text-ink-muted">{formatDateTime(event.startsAt)}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {event.status === 'DRAFT' && (
                    <Button size="sm" onClick={() => void publishEvent(event.eventId).then(() => refresh(event.groupId && !admin ? 'Sent to the group’s advisor for approval.' : 'Published.')).catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, 'Couldn’t publish.') ?? '' }))}>
                      Publish<span className="sr-only">{` ${event.title}`}</span>
                    </Button>
                  )}
                  {event.status !== 'CANCELLED' && (
                    <Button size="sm" variant="secondary" onClick={() => setCancelling(event)}>
                      Cancel<span className="sr-only">{` ${event.title}`}</span>
                    </Button>
                  )}
                  <Button size="sm" variant="secondary" onClick={() => setOpen(open === event.eventId ? null : event.eventId)}>
                    {open === event.eventId ? 'Hide registrations' : 'Registrations'}
                  </Button>
                </div>
                {open === event.eventId && <Attendees eventId={event.eventId} />}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  )
}
