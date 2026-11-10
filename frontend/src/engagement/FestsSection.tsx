import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { applyForFestShift, getFests, withdrawFestApplication } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { CampusFest, FestShift } from '../types/engagement'
import { Alert, Badge, Button, Card, TextField } from '../ui'
import { failureText, VOLUNTEER_LABEL } from './engagementText'

type Notify = (text: string, error?: boolean) => void

function Shift({ shift, canVolunteer, notify }: { shift: FestShift; canVolunteer: boolean; notify: Notify }) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const live = shift.myApplication === 'APPLIED' || shift.myApplication === 'ACCEPTED'
  const full = shift.accepted >= shift.places
  const run = (action: () => Promise<unknown>, done: string, fallback: string) => {
    setBusy(true)
    void action()
      .then(() => notify(done))
      .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
      .finally(() => setBusy(false))
  }
  return (
    <li className="space-y-2 px-4 py-3 sm:px-5">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-ink">{shift.roleTitle}</span>
        <span className="text-ink-muted">
          <time dateTime={shift.startsAt}>{formatDateTime(shift.startsAt)}</time> to <time dateTime={shift.endsAt}>{formatDateTime(shift.endsAt)}</time>
        </span>
        <span className="text-ink-muted">{shift.accepted} of {shift.places} places taken</span>
        {shift.myApplication && <Badge tone={shift.myApplication === 'ACCEPTED' ? 'success' : 'neutral'}>{VOLUNTEER_LABEL[shift.myApplication]}</Badge>}
      </div>
      {canVolunteer && live && shift.myApplicationId !== null && (
        <Button variant="secondary" disabled={busy} onClick={() => run(() => withdrawFestApplication(shift.myApplicationId as number), 'Application withdrawn.', 'Couldn’t withdraw.')}>
          Withdraw
        </Button>
      )}
      {canVolunteer && !live && shift.myApplication === null && !full && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            run(() => applyForFestShift(shift.shiftId, note.trim() || null), 'Applied. The campus office will accept or decline.', 'Couldn’t apply.')
          }}
        >
          <TextField label={`Note for ${shift.roleTitle} (optional)`} value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />
          <Button type="submit" disabled={busy}>Volunteer</Button>
        </form>
      )}
    </li>
  )
}

function Fest({ fest, canVolunteer, notify }: { fest: CampusFest; canVolunteer: boolean; notify: Notify }) {
  return (
    <Card title={fest.name} description={`${fest.startsOn} to ${fest.endsOn}`} padding="none">
      {fest.description && <p className="px-4 pb-3 text-sm whitespace-pre-wrap text-ink sm:px-5">{fest.description}</p>}
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Schedule</h3>
      {fest.schedule.length === 0 ? (
        <p className="px-4 py-3 text-sm text-ink-muted sm:px-5">No events on the schedule yet.</p>
      ) : (
        <ul className="divide-y divide-line text-sm">
          {fest.schedule.map((item) => (
            <li key={item.eventId} className="flex flex-wrap gap-2 px-4 py-2 sm:px-5">
              <span className="font-medium text-ink">{item.title}</span>
              <time className="text-ink-muted" dateTime={item.startsAt}>{formatDateTime(item.startsAt)}</time>
              {item.venue && <span className="text-ink-muted">{item.venue}</span>}
              {item.status === 'CANCELLED' && <Badge tone="warning">Cancelled</Badge>}
            </li>
          ))}
        </ul>
      )}
      <h3 className="border-t border-line px-4 pt-3 text-sm font-semibold text-ink sm:px-5">Volunteer shifts</h3>
      {fest.shifts.length === 0 ? (
        <p className="px-4 py-3 text-sm text-ink-muted sm:px-5">No volunteer shifts yet.</p>
      ) : (
        <ul className="divide-y divide-line">
          {fest.shifts.map((shift) => <Shift key={shift.shiftId} shift={shift} canVolunteer={canVolunteer} notify={notify} />)}
        </ul>
      )}
    </Card>
  )
}

/** The campus Fests tab: published fests with their schedules, and volunteering for their shifts. */
export function FestsSection({ canVolunteer }: { canVolunteer: boolean }) {
  const queryClient = useQueryClient()
  const fests = useApiQuery(canVolunteer ? queryKeys.engagement.fests() : null, (signal) => getFests(signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.fests() })
  }
  if (!canVolunteer) {
    return <EmptyState title="Fests are for student and faculty accounts." />
  }
  return (
    <section aria-label="Fests" className="space-y-4">
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      {fests.status !== 'loaded' ? (
        <SectionState request={fests} what="fests" />
      ) : fests.data.length === 0 ? (
        <EmptyState title="No fests published yet." />
      ) : (
        fests.data.map((fest) => <Fest key={fest.festId} fest={fest} canVolunteer={canVolunteer} notify={notify} />)
      )}
    </section>
  )
}
