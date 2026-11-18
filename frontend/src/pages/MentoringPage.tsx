import { useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { Link, useParams } from 'react-router'
import { addActionItem, addMeeting, completeActionItem, getMyPairings, getPairing } from '../api/mentoringApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { primaryLinkClasses } from '../components/actionStyles'
import { NavIcon } from '../components/navigation/navIcons'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { formatMomentDay as formatDate } from '../erp/requestWords'
import { SectionState } from '../courses/SectionState'
import { failureText, localToIso } from '../engagement/engagementText'
import type { ActionOwner, MentoringActionItem, MentoringMeeting, MentoringPairing } from '../types/mentoring'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField, Timeline } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Notify = (text: string, error?: boolean) => void

const backLinkClasses =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

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

function otherOf(pairing: MentoringPairing): string {
  return pairing.iAmMentor ? pairing.menteeLoginId : pairing.mentorLoginId
}

function headingOf(pairing: MentoringPairing): string {
  return pairing.iAmMentor ? `Mentoring ${pairing.menteeLoginId}` : `Your mentor ${pairing.mentorLoginId}`
}

function isMine(item: MentoringActionItem, pairing: MentoringPairing): boolean {
  return (item.owner === 'MENTOR') === pairing.iAmMentor
}

function SubHeading({ children }: { children: string }) {
  return (
    <>
      <h3 className="text-base font-bold text-portal-navy">{children}</h3>
      <span aria-hidden="true" className="mt-1 mb-2 block h-0.5 w-12 rounded bg-portal-cta" />
    </>
  )
}

function ActionItems({ meeting, pairing, notify }: { meeting: MentoringMeeting; pairing: MentoringPairing; notify: Notify }) {
  const run = useRun(notify)
  if (meeting.actionItems.length === 0) {
    return <p className="text-sm text-ink-muted">No action items for this meeting.</p>
  }
  return (
    <ul className="divide-y divide-line rounded-control border border-line text-sm">
      {meeting.actionItems.map((action) => (
        <li key={action.itemId} className="grid gap-1 px-3 py-2 sm:grid-cols-[minmax(0,1.6fr)_7rem_minmax(0,1fr)_5rem_auto] sm:items-center sm:gap-3">
          <span className={`break-words ${action.completedAt ? 'text-ink-muted' : 'text-ink'}`}>{action.description}</span>
          <span className="text-ink-muted">
            <span className="sr-only">Who does it: </span>
            {isMine(action, pairing) ? 'You' : otherOf(pairing)}
          </span>
          <span className="text-ink-muted">
            {action.completedAt ? (
              <>
                Completed <time dateTime={action.completedAt}>{formatDateTime(action.completedAt)}</time>
              </>
            ) : action.dueAt ? (
              <>
                Due <time dateTime={action.dueAt}>{formatDateTime(action.dueAt)}</time>
              </>
            ) : (
              'No due date'
            )}
          </span>
          <span>{action.completedAt ? <Badge tone="success">Done</Badge> : <span className="font-semibold text-ink">Open</span>}</span>
          <span>
            {!action.completedAt && (
              <Button size="sm" variant="secondary" onClick={() => run(() => completeActionItem(action.itemId), 'Marked done.', 'Couldn’t mark it done.')}>
                Mark done<span className="sr-only">{` ${action.description}`}</span>
              </Button>
            )}
          </span>
        </li>
      ))}
    </ul>
  )
}

function Meeting({ meeting, pairing, notify }: { meeting: MentoringMeeting; pairing: MentoringPairing; notify: Notify }) {
  const run = useRun(notify)
  const [item, setItem] = useState({ description: '', owner: 'MENTEE' as ActionOwner, dueAt: '' })
  return (
    <section aria-label={meeting.title} className="space-y-4 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <div>
        <h2 className="text-xl font-bold break-words text-portal-navy">{meeting.title}</h2>
        <p className="text-[0.95rem] text-ink">
          Held at: <time dateTime={meeting.heldAt}>{formatDateTime(meeting.heldAt)}</time>
        </p>
      </div>
      {meeting.sharedNotes && (
        <div className="rounded-control border border-line px-3 py-2.5">
          <p className="text-sm font-semibold text-ink">Shared notes</p>
          <p className="mt-0.5 text-[0.95rem] break-words whitespace-pre-wrap text-ink">{meeting.sharedNotes}</p>
        </div>
      )}
      {meeting.privateNotes && (
        <div className="rounded-control bg-surface-muted px-3 py-2.5">
          <p className="text-sm font-semibold text-ink">Private notes (only you see these)</p>
          <p className="mt-0.5 text-[0.95rem] break-words whitespace-pre-wrap text-ink">{meeting.privateNotes}</p>
        </div>
      )}
      <div>
        <SubHeading>Action items</SubHeading>
        <ActionItems meeting={meeting} pairing={pairing} notify={notify} />
      </div>
      {pairing.iAmMentor && pairing.active && (
        <form
          className="space-y-2"
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
          <SubHeading>Add action item</SubHeading>
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_minmax(0,1fr)] sm:items-end">
            <TextField label={`Action item for ${meeting.title}`} value={item.description} maxLength={500} onChange={(event) => setItem({ ...item, description: event.target.value })} />
            <SelectField label="Who does it" value={item.owner} onChange={(event) => setItem({ ...item, owner: event.target.value as ActionOwner })}>
              <option value="MENTEE">{pairing.menteeLoginId}</option>
              <option value="MENTOR">You</option>
            </SelectField>
            <TextField label="Due (optional)" type="datetime-local" value={item.dueAt} onChange={(event) => setItem({ ...item, dueAt: event.target.value })} />
          </div>
          <Button type="submit">Add action item</Button>
        </form>
      )}
    </section>
  )
}

function RecordMeeting({ pairing, notify }: { pairing: MentoringPairing; notify: Notify }) {
  const run = useRun(notify)
  const [form, setForm] = useState({ title: '', heldAt: '', sharedNotes: '', privateNotes: '' })
  return (
    <form
      aria-label="Record a meeting"
      className="space-y-3 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5"
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
      <h2 className="text-lg font-bold text-portal-navy">Record a meeting</h2>
      <span aria-hidden="true" className="block h-0.5 w-14 rounded bg-portal-cta" />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Meeting title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
        <TextField label="Held at" type="datetime-local" value={form.heldAt} onChange={(event) => setForm({ ...form, heldAt: event.target.value })} />
        <TextAreaField label="Shared notes" hint={`${pairing.menteeLoginId} sees these.`} rows={3} value={form.sharedNotes} maxLength={10_000} onChange={(event) => setForm({ ...form, sharedNotes: event.target.value })} />
        <TextAreaField label="Private notes" hint="Only you see these." rows={3} value={form.privateNotes} maxLength={10_000} onChange={(event) => setForm({ ...form, privateNotes: event.target.value })} />
      </div>
      <Button type="submit">Record meeting</Button>
    </form>
  )
}

function PairingDetails({ pairing }: { pairing: MentoringPairing }) {
  const rows: [string, ReactNode][] = [
    ['Student', pairing.iAmMentor ? pairing.menteeLoginId : `${pairing.menteeLoginId} (You)`],
    ['Mentor', pairing.iAmMentor ? `${pairing.mentorLoginId} (You)` : pairing.mentorLoginId],
    ['Status', pairing.active ? 'Active' : 'Ended'],
    ['Active from', <time key="from" dateTime={pairing.startedAt}>{formatDate(pairing.startedAt)}</time>],
    ...(pairing.endedAt ? [['Ended', <time key="to" dateTime={pairing.endedAt}>{formatDate(pairing.endedAt)}</time>] as [string, ReactNode]] : []),
  ]
  return (
    <Card title="Pairing details">
      <dl className="divide-y divide-line text-[0.95rem]">
        {rows.map(([term, value]) => (
          <div key={term} className="grid grid-cols-[7rem_minmax(0,1fr)] gap-3 py-1.5">
            <dt className="font-semibold text-ink">{term}</dt>
            <dd className="break-words text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  )
}

function PairingPage({ assignmentId, notify }: { assignmentId: number; notify: Notify }) {
  const detail = useApiQuery(queryKeys.mentoring.pairing(assignmentId), (signal) => getPairing(assignmentId, signal))
  if (detail.status !== 'loaded') {
    return (
      <Card>
        <SectionState request={detail} what="this pairing" />
      </Card>
    )
  }
  const { pairing, meetings } = detail.data
  const newestFirst = [...meetings].sort((a, b) => b.heldAt.localeCompare(a.heldAt))
  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
      <section aria-label={headingOf(pairing)} className="min-w-0 space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/mentoring" className={backLinkClasses}>
            <span aria-hidden="true">←</span> Mentoring
          </Link>
          <h2 className="text-xl font-bold text-ink">{headingOf(pairing)}</h2>
          {!pairing.active && <Badge tone="neutral">Ended</Badge>}
        </div>
        {!pairing.active && <Alert tone="info">This pairing has ended. It stays readable; no new meetings or action items can be added.</Alert>}
        {pairing.iAmMentor && pairing.active && <RecordMeeting pairing={pairing} notify={notify} />}
        {newestFirst.length === 0 ? (
          <Card>
            <EmptyState title="No meetings recorded yet." description={pairing.iAmMentor ? 'Record the first meeting above.' : 'Your mentor records each meeting here.'} />
          </Card>
        ) : (
          newestFirst.map((meeting) => <Meeting key={meeting.meetingId} meeting={meeting} pairing={pairing} notify={notify} />)
        )}
      </section>
      <div className="min-w-0 space-y-4">
        <PairingDetails pairing={pairing} />
        <Card title="Meetings">
          <Timeline
            label="Meetings"
            empty="No meetings yet."
            entries={meetings.map((meeting) => ({ key: meeting.meetingId, title: meeting.title, at: meeting.heldAt, tone: 'done' as const }))}
          />
        </Card>
      </div>
    </div>
  )
}

function OpenItemsOf({ pairing, notify }: { pairing: MentoringPairing; notify: Notify }) {
  const detail = useApiQuery(queryKeys.mentoring.pairing(pairing.assignmentId), (signal) => getPairing(pairing.assignmentId, signal))
  const run = useRun(notify)
  if (detail.status !== 'loaded') {
    return null
  }
  const open = detail.data.meetings.flatMap((meeting) => meeting.actionItems).filter((item) => !item.completedAt && isMine(item, pairing))
  return (
    <>
      {open.map((item) => (
        <li key={item.itemId} className="grid gap-1 px-3 py-2.5 sm:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-3">
          <span className="break-words text-ink">{item.description}</span>
          <span className="text-ink-muted">With {otherOf(pairing)}</span>
          <span className="text-ink-muted">{item.dueAt ? <>Due <time dateTime={item.dueAt}>{formatDateTime(item.dueAt)}</time></> : 'No due date'}</span>
          <Button size="sm" onClick={() => run(() => completeActionItem(item.itemId), 'Marked done.', 'Couldn’t mark it done.')}>
            Mark done<span className="sr-only">{` ${item.description}`}</span>
          </Button>
        </li>
      ))}
    </>
  )
}

function MentoringHome({ notify }: { notify: Notify }) {
  const pairings = useApiQuery(queryKeys.mentoring.mine(), (signal) => getMyPairings(signal))
  if (pairings.status !== 'loaded') {
    return (
      <Card>
        <SectionState request={pairings} what="mentoring" />
      </Card>
    )
  }
  if (pairings.data.length === 0) {
    return (
      <Card>
        <EmptyState title="No mentoring yet." description="The college pairs mentors with students." />
      </Card>
    )
  }
  return (
    <div className="space-y-6">
      <Card title="Your pairings">
        <ul className="space-y-3">
          {pairings.data.map((pairing) => (
            <li
              key={pairing.assignmentId}
              className="grid gap-3 rounded-panel border border-line px-4 py-3 sm:grid-cols-[auto_minmax(0,1fr)_minmax(0,1.2fr)_auto] sm:items-center sm:gap-5"
            >
              <NavIcon name="mentoring" className="hidden size-6 text-ink-muted sm:block" />
              <div className="min-w-0">
                <p className="text-sm text-ink-muted">{pairing.iAmMentor ? 'Your mentee' : 'Your mentor'}</p>
                <p className="text-lg font-bold break-words text-ink">{otherOf(pairing)}</p>
              </div>
              <p className="text-[0.95rem] text-ink">
                {pairing.active ? (
                  <>
                    <span className="font-semibold">Active</span> since <time dateTime={pairing.startedAt}>{formatDate(pairing.startedAt)}</time>
                  </>
                ) : (
                  <>
                    <span className="font-semibold">Ended</span> <time dateTime={pairing.endedAt ?? pairing.startedAt}>{formatDate(pairing.endedAt ?? pairing.startedAt)}</time>
                  </>
                )}
              </p>
              <Link to={`/mentoring/${pairing.assignmentId}`} className={pairing.active ? primaryLinkClasses : `font-medium ${textLinkClasses}`}>
                {pairing.active ? 'View meetings' : 'View history'} <span className="sr-only">{`with ${otherOf(pairing)}`}</span>
              </Link>
            </li>
          ))}
        </ul>
      </Card>
      <Card title="Your open action items" description="Items assigned to you that are still open. They are also on your To-Do list.">
        <ul aria-label="Your open action items" className="divide-y divide-line rounded-control border border-line text-sm empty:hidden">
          {pairings.data.map((pairing) => (
            <OpenItemsOf key={pairing.assignmentId} pairing={pairing} notify={notify} />
          ))}
        </ul>
        <Link to="/todo" className={`mt-3 inline-block font-medium ${textLinkClasses}`}>
          View To-Do
        </Link>
      </Card>
    </div>
  )
}

export function MentoringPage() {
  const { assignmentId } = useParams()
  const id = Number(assignmentId)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string; at: string } | null>(null)
  const place = assignmentId ?? 'home'
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text, at: place })
  const shown = message?.at === place ? message : null
  return (
    <>
      <title>Mentoring · Stacc</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Mentoring</h1>
      <p className="mt-2 mb-5 text-[0.95rem] leading-6 text-ink-muted sm:text-base">
        Meetings with your mentor or mentees, shared notes, and action items. Open items are on your To-Do list.
      </p>
      <div className="space-y-4">
        <p role="status" className="sr-only">{shown?.tone === 'success' ? shown.text : ''}</p>
        {shown && <Alert tone={shown.tone}>{shown.text}</Alert>}
        {assignmentId === undefined ? (
          <MentoringHome notify={notify} />
        ) : Number.isInteger(id) && id > 0 ? (
          <PairingPage assignmentId={id} notify={notify} />
        ) : (
          <Card>
            <EmptyState title="Pairing unavailable" description="This pairing could not be found or is not yours." />
          </Card>
        )}
      </div>
    </>
  )
}
