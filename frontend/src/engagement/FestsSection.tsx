import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { applyForFestShift, getFests, withdrawFestApplication } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { CampusFest, FestShift } from '../types/engagement'
import { Alert, Badge, Button, Card, DataTable, TextField } from '../ui'
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
    <li className="grid gap-2 px-4 py-3 sm:px-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_9rem_minmax(0,2fr)] lg:items-center lg:gap-4">
      <span className="flex flex-wrap items-center gap-2 font-semibold text-ink">
        {shift.roleTitle}
        {shift.myApplication && <Badge tone={shift.myApplication === 'ACCEPTED' ? 'success' : 'neutral'}>{VOLUNTEER_LABEL[shift.myApplication]}</Badge>}
      </span>
      <span className="text-sm text-ink">
        <time dateTime={shift.startsAt}>{formatDateTime(shift.startsAt)}</time> to <time dateTime={shift.endsAt}>{formatDateTime(shift.endsAt)}</time>
      </span>
      <span className="text-sm text-ink">{shift.accepted} of {shift.places} places taken</span>
      <div className="min-w-0">
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
          <div className="min-w-48 flex-1">
            <TextField label={`Note for ${shift.roleTitle} (optional)`} value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />
          </div>
          <Button type="submit" disabled={busy}>Volunteer</Button>
        </form>
      )}
      </div>
    </li>
  )
}

function SubHeading({ children }: { children: string }) {
  return (
    <div className="border-t border-line px-4 pt-4 pb-2 sm:px-5">
      <h3 className="text-lg font-bold text-portal-navy">{children}</h3>
      <span aria-hidden="true" className="mt-1 block h-0.5 w-12 rounded bg-portal-cta" />
    </div>
  )
}

type FestScheduleItem = CampusFest['schedule'][number]

function Fest({ fest, canVolunteer, notify }: { fest: CampusFest; canVolunteer: boolean; notify: Notify }) {
  return (
    <Card title={fest.name} description={`${fest.startsOn} to ${fest.endsOn}`} padding="none">
      {fest.description && <p className="px-4 pb-3 text-[0.95rem] whitespace-pre-wrap text-ink sm:px-5">{fest.description}</p>}
      <SubHeading>Schedule</SubHeading>
      {fest.schedule.length === 0 ? (
        <p className="px-4 py-3 text-sm text-ink-muted sm:px-5">No events on the schedule yet.</p>
      ) : (
        <DataTable<FestScheduleItem>
          caption={`Schedule of ${fest.name}`}
          rows={fest.schedule}
          rowKey={(item) => item.eventId}
          columns={[
            { key: 'title', header: 'Activity', primary: true, cell: (item) => item.title },
            { key: 'when', header: 'When', cell: (item) => <time dateTime={item.startsAt}>{formatDateTime(item.startsAt)}</time> },
            { key: 'venue', header: 'Venue', cell: (item) => item.venue ?? '—' },
            { key: 'status', header: 'Status', cell: (item) => (item.status === 'CANCELLED' ? <Badge tone="warning">Cancelled</Badge> : 'On') },
          ]}
        />
      )}
      <SubHeading>Volunteer shifts</SubHeading>
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
