import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addActionItem, addMeeting, completeActionItem, getMyPairings, getPairing } from '../api/mentoringApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText, localToIso } from '../engagement/engagementText'
import type { ActionOwner, MentoringMeeting, MentoringPairing } from '../types/mentoring'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void

function useRun(notify: Notify) {
  const queryClient = useQueryClient()
  return (action: () => Promise<unknown>, done: string, fallback: string) =>
    void action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.mentoring.all })
        await queryClient.invalidateQueries({ queryKey: queryKeys.me.todo() })
      })
      .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

function Meeting({ meeting, pairing, notify }: { meeting: MentoringMeeting; pairing: MentoringPairing; notify: Notify }) {
  const run = useRun(notify)
  const [item, setItem] = useState({ description: '', owner: 'MENTEE' as ActionOwner, dueAt: '' })
  const other = pairing.iAmMentor ? pairing.menteeLoginId : pairing.mentorLoginId
  return (
    <section aria-label={meeting.title} className="space-y-2 rounded-control border border-line p-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-ink">{meeting.title}</span>
        <time className="text-ink-muted" dateTime={meeting.heldAt}>{formatDateTime(meeting.heldAt)}</time>
      </div>
      {meeting.sharedNotes && <p className="text-sm break-words whitespace-pre-wrap text-ink">{meeting.sharedNotes}</p>}
      {meeting.privateNotes && (
        <div className="rounded-control bg-surface-muted p-2 text-sm">
          <p className="text-xs font-medium text-ink-muted">Private notes (only you see these)</p>
          <p className="break-words whitespace-pre-wrap text-ink">{meeting.privateNotes}</p>
        </div>
      )}
      {meeting.actionItems.length > 0 && (
        <ul className="space-y-1 text-sm">
          {meeting.actionItems.map((action) => {
            const mine = (action.owner === 'MENTOR') === pairing.iAmMentor
            return (
              <li key={action.itemId} className="flex flex-wrap items-center gap-2">
                <span className={action.completedAt ? 'text-ink-muted line-through' : 'text-ink'}>{action.description}</span>
                <span className="text-ink-muted">{mine ? 'You' : other}</span>
                {action.dueAt && <span className="text-ink-muted">by {formatDateTime(action.dueAt)}</span>}
                {action.completedAt ? (
                  <Badge tone="success">Done</Badge>
                ) : (
                  <Button size="sm" variant="secondary" onClick={() => run(() => completeActionItem(action.itemId), 'Marked done.', 'Couldn’t mark it done.')}>
                    Mark done<span className="sr-only">{`: ${action.description}`}</span>
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
      )}
      {pairing.iAmMentor && pairing.active && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (item.description.trim() === '') {
              return
            }
            run(
              () => addActionItem(meeting.meetingId, { description: item.description.trim(), owner: item.owner, dueAt: localToIso(item.dueAt) }),
              'Action item added. It is on its owner’s To-Do list.',
              'Couldn’t add the action item.',
            )
            setItem({ ...item, description: '', dueAt: '' })
          }}
        >
          <TextField label={`Action item for ${meeting.title}`} value={item.description} maxLength={500} onChange={(event) => setItem({ ...item, description: event.target.value })} />
          <SelectField label="Who does it" value={item.owner} onChange={(event) => setItem({ ...item, owner: event.target.value as ActionOwner })}>
            <option value="MENTEE">{other}</option>
            <option value="MENTOR">You</option>
          </SelectField>
          <TextField label="Due (optional)" type="datetime-local" value={item.dueAt} onChange={(event) => setItem({ ...item, dueAt: event.target.value })} />
          <Button type="submit" size="sm">Add action item</Button>
        </form>
      )}
    </section>
  )
}

function Pairing({ pairing, notify }: { pairing: MentoringPairing; notify: Notify }) {
  const detail = useApiQuery(queryKeys.mentoring.pairing(pairing.assignmentId), (signal) => getPairing(pairing.assignmentId, signal))
  const run = useRun(notify)
  const [form, setForm] = useState({ title: '', heldAt: '', sharedNotes: '', privateNotes: '' })
  const heading = pairing.iAmMentor ? `Mentoring ${pairing.menteeLoginId}` : `Your mentor ${pairing.mentorLoginId}`
  return (
    <Card title={heading} action={!pairing.active ? <Badge tone="neutral">Ended</Badge> : undefined}>
      <div className="space-y-3">
        {pairing.iAmMentor && pairing.active && (
          <form
            className="grid gap-3 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              const heldAt = localToIso(form.heldAt)
              if (form.title.trim() === '' || heldAt === null) {
                notify('Enter a title and when the meeting was held.', true)
                return
              }
              run(
                () => addMeeting(pairing.assignmentId, { heldAt, title: form.title.trim(), sharedNotes: form.sharedNotes.trim() || null, privateNotes: form.privateNotes.trim() || null }),
                'Meeting recorded.',
                'Couldn’t record the meeting.',
              )
              setForm({ title: '', heldAt: '', sharedNotes: '', privateNotes: '' })
            }}
          >
            <TextField label="Meeting title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
            <TextField label="Held at" type="datetime-local" value={form.heldAt} onChange={(event) => setForm({ ...form, heldAt: event.target.value })} />
            <TextAreaField label="Shared notes" hint={`${pairing.menteeLoginId} sees these.`} rows={3} value={form.sharedNotes} maxLength={10_000} onChange={(event) => setForm({ ...form, sharedNotes: event.target.value })} />
            <TextAreaField label="Private notes" hint="Only you see these." rows={3} value={form.privateNotes} maxLength={10_000} onChange={(event) => setForm({ ...form, privateNotes: event.target.value })} />
            <div><Button type="submit">Record meeting</Button></div>
          </form>
        )}
        {detail.status !== 'loaded' ? (
          <SectionState request={detail} what="meetings" />
        ) : detail.data.meetings.length === 0 ? (
          <p className="text-sm text-ink-muted">No meetings recorded yet.</p>
        ) : (
          detail.data.meetings.map((meeting) => <Meeting key={meeting.meetingId} meeting={meeting} pairing={pairing} notify={notify} />)
        )}
      </div>
    </Card>
  )
}

/** /mentoring: the account's mentoring pairings, as mentor or as student, with meetings, notes, and action items. */
export function MentoringPage() {
  const pairings = useApiQuery(queryKeys.mentoring.mine(), (signal) => getMyPairings(signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text })
  return (
    <>
      <title>Mentoring · Stacc</title>
      <PageHeader title="Mentoring" description="Meetings with your mentor or mentees, shared notes, and action items. Open items are on your To-Do list." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {pairings.status !== 'loaded' ? (
          <SectionState request={pairings} what="mentoring" />
        ) : pairings.data.length === 0 ? (
          <EmptyState title="No mentoring yet." description="The college pairs mentors with students." />
        ) : (
          pairings.data.map((pairing) => <Pairing key={pairing.assignmentId} pairing={pairing} notify={notify} />)
        )}
      </div>
    </>
  )
}
