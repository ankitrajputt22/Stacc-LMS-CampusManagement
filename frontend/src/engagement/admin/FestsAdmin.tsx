import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addFestShift,
  archiveFest,
  createFest,
  decideFestApplication,
  getAllFests,
  getFestApplications,
  getManagedEvents,
  publishFest,
  scheduleFestEvent,
  unscheduleFestEvent,
} from '../../api/engagementApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import type { CampusFest } from '../../types/engagement'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField } from '../../ui'
import { failureText, FEST_STATUS_LABEL, localToIso, VOLUNTEER_LABEL } from '../engagementText'

type Notify = (text: string, error?: boolean) => void

function attempt(action: () => Promise<unknown>, done: string, fallback: string, notify: Notify) {
  void action()
    .then(() => notify(done))
    .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

function Applications({ festId, notify }: { festId: number; notify: Notify }) {
  const applications = useApiQuery(queryKeys.engagement.festApplications(festId), (signal) => getFestApplications(festId, signal))
  if (applications.status !== 'loaded') {
    return <SectionState request={applications} what="volunteer applications" />
  }
  if (applications.data.length === 0) {
    return <p className="px-4 py-3 text-sm text-ink-muted sm:px-5">No volunteer applications yet.</p>
  }
  return (
    <ul className="divide-y divide-line text-sm">
      {applications.data.map((application) => (
        <li key={application.applicationId} className="flex flex-wrap items-center gap-2 px-4 py-3 sm:px-5">
          <span className="font-mono text-ink">{application.loginId}</span>
          <span className="text-ink-muted">{application.roleTitle}</span>
          {application.note && <span className="text-ink-muted">“{application.note}”</span>}
          <Badge tone={application.status === 'ACCEPTED' ? 'success' : 'neutral'}>{VOLUNTEER_LABEL[application.status]}</Badge>
          {application.status === 'APPLIED' && (
            <>
              <Button variant="secondary" onClick={() => attempt(() => decideFestApplication(application.applicationId, true), `${application.loginId} accepted.`, 'Couldn’t accept.', notify)}>
                Accept {application.loginId}
              </Button>
              <Button variant="secondary" onClick={() => attempt(() => decideFestApplication(application.applicationId, false), `${application.loginId} declined.`, 'Couldn’t decline.', notify)}>
                Decline {application.loginId}
              </Button>
            </>
          )}
        </li>
      ))}
    </ul>
  )
}

function FestCard({ fest, notify }: { fest: CampusFest; notify: Notify }) {
  const events = useApiQuery(queryKeys.engagement.managedEvents(), (signal) => getManagedEvents(signal))
  const [eventId, setEventId] = useState('')
  const [shift, setShift] = useState({ roleTitle: '', startsAt: '', endsAt: '', places: '5' })
  const [archiving, setArchiving] = useState(false)
  const open = fest.status !== 'ARCHIVED'
  const scheduled = new Set(fest.schedule.map((item) => item.eventId))
  return (
    <Card
      title={fest.name}
      description={`${fest.startsOn} to ${fest.endsOn}`}
      action={<Badge tone={fest.status === 'PUBLISHED' ? 'success' : 'neutral'}>{FEST_STATUS_LABEL[fest.status]}</Badge>}
      padding="none"
    >
      <div className="flex flex-wrap gap-2 px-4 pb-3 sm:px-5">
        {fest.status === 'DRAFT' && <Button onClick={() => attempt(() => publishFest(fest.festId), 'Fest published.', 'Couldn’t publish.', notify)}>Publish {fest.name}</Button>}
        {open && !archiving && <Button variant="secondary" onClick={() => setArchiving(true)}>Archive {fest.name}</Button>}
      </div>
      {archiving && (
        <div className="px-4 pb-3 sm:px-5">
          <StatusConfirmation
            question={`Archive ${fest.name}?`}
            detail="It is hidden from students and faculty and can no longer be changed. This cannot be undone."
            confirmLabel="Archive"
            busyLabel="Archiving…"
            onCancel={() => setArchiving(false)}
            onConfirm={async () => {
              await archiveFest(fest.festId)
              setArchiving(false)
              notify('Fest archived.')
            }}
            describeRefusal={(_status, message) => message ?? 'Couldn’t archive the fest.'}
          />
        </div>
      )}
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Schedule</h3>
      <ul className="divide-y divide-line text-sm">
        {fest.schedule.map((item) => (
          <li key={item.eventId} className="flex flex-wrap items-center gap-2 px-4 py-2 sm:px-5">
            <span className="font-medium text-ink">{item.title}</span>
            <time className="text-ink-muted" dateTime={item.startsAt}>{formatDateTime(item.startsAt)}</time>
            {open && (
              <Button variant="secondary" onClick={() => attempt(() => unscheduleFestEvent(fest.festId, item.eventId), 'Taken off the schedule.', 'Couldn’t change the schedule.', notify)}>
                Take {item.title} off
              </Button>
            )}
          </li>
        ))}
      </ul>
      {open && (
        <form
          className="flex flex-wrap items-end gap-2 px-4 py-3 sm:px-5"
          onSubmit={(event) => {
            event.preventDefault()
            if (eventId !== '') attempt(() => scheduleFestEvent(fest.festId, Number(eventId)), 'Event added to the schedule.', 'Couldn’t add the event.', notify)
          }}
        >
          <SelectField label={`Event for ${fest.name}`} value={eventId} onChange={(event) => setEventId(event.target.value)}>
            <option value="">Choose…</option>
            {events.status === 'loaded' &&
              events.data.filter((item) => !scheduled.has(item.eventId)).map((item) => <option key={item.eventId} value={item.eventId}>{item.title}</option>)}
          </SelectField>
          <Button type="submit" variant="secondary">Add to schedule</Button>
        </form>
      )}
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Volunteer shifts</h3>
      <ul className="divide-y divide-line text-sm">
        {fest.shifts.map((item) => (
          <li key={item.shiftId} className="flex flex-wrap gap-2 px-4 py-2 sm:px-5">
            <span className="font-medium text-ink">{item.roleTitle}</span>
            <time className="text-ink-muted" dateTime={item.startsAt}>{formatDateTime(item.startsAt)}</time>
            <span className="text-ink-muted">{item.accepted} of {item.places} places taken</span>
          </li>
        ))}
      </ul>
      {open && (
        <form
          className="grid gap-3 px-4 py-3 sm:grid-cols-2 sm:px-5"
          onSubmit={(event) => {
            event.preventDefault()
            const startsAt = localToIso(shift.startsAt)
            const endsAt = localToIso(shift.endsAt)
            if (shift.roleTitle.trim() === '' || startsAt === null || endsAt === null) {
              notify('Enter a role, a start, and an end for the shift.', true)
              return
            }
            attempt(() => addFestShift(fest.festId, { roleTitle: shift.roleTitle.trim(), startsAt, endsAt, places: Number(shift.places) }), 'Shift added.', 'Couldn’t add the shift.', notify)
          }}
        >
          <TextField label="Shift role" value={shift.roleTitle} maxLength={150} onChange={(event) => setShift({ ...shift, roleTitle: event.target.value })} />
          <TextField label="Places" inputMode="numeric" value={shift.places} onChange={(event) => setShift({ ...shift, places: event.target.value.replace(/\D/g, '') })} />
          <TextField label="Shift starts" type="datetime-local" value={shift.startsAt} onChange={(event) => setShift({ ...shift, startsAt: event.target.value })} />
          <TextField label="Shift ends" type="datetime-local" value={shift.endsAt} onChange={(event) => setShift({ ...shift, endsAt: event.target.value })} />
          <div><Button type="submit" variant="secondary">Add shift</Button></div>
        </form>
      )}
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Volunteer applications</h3>
      <Applications festId={fest.festId} notify={notify} />
    </Card>
  )
}

/** Campus Management → Fests: add a fest, build its schedule from campus events, add volunteer shifts, and decide applications. */
export function FestsAdmin() {
  const queryClient = useQueryClient()
  const fests = useApiQuery(queryKeys.engagement.allFests(), (signal) => getAllFests(signal))
  const [form, setForm] = useState({ name: '', description: '', startsOn: '', endsOn: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }
  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <Card title="Add a fest">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            attempt(
              () => createFest({ name: form.name.trim(), description: form.description.trim() || null, startsOn: form.startsOn, endsOn: form.endsOn }),
              'Fest added as a draft.',
              'Couldn’t add the fest.',
              notify,
            )
          }}
        >
          <TextField label="Fest name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          <div />
          <TextField label="First day" type="date" value={form.startsOn} onChange={(event) => setForm({ ...form, startsOn: event.target.value })} />
          <TextField label="Last day" type="date" value={form.endsOn} onChange={(event) => setForm({ ...form, endsOn: event.target.value })} />
          <div className="sm:col-span-2">
            <TextAreaField label="Description (optional)" value={form.description} maxLength={10000} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </div>
          <div><Button type="submit">Add fest</Button></div>
        </form>
      </Card>
      {fests.status !== 'loaded' ? (
        <SectionState request={fests} what="fests" />
      ) : fests.data.length === 0 ? (
        <EmptyState title="No fests yet." description="Add one above." />
      ) : (
        fests.data.map((fest) => <FestCard key={fest.festId} fest={fest} notify={notify} />)
      )}
    </div>
  )
}
