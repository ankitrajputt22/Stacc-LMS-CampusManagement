import { useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { Link } from 'react-router'
import {
  acceptGrievance,
  appealGrievance,
  decideGrievanceAppeal,
  getCommitteeGrievance,
  getMyGrievance,
  resolveGrievance,
  reviewGrievance,
} from '../api/grievanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { GrievanceCase } from '../types/grievances'
import { Alert, Button, TextAreaField, Timeline, type TimelineEntry } from '../ui'
import { GrievanceStatusTag } from './GrievanceStatusTag'

type Notify = (text: string, error?: boolean) => void

const backLinkClasses =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <h2 className="text-lg font-bold text-portal-navy">{title}</h2>
      <span aria-hidden="true" className="mt-1.5 mb-3 block h-0.5 w-14 rounded bg-portal-cta" />
      {children}
    </section>
  )
}

function PlainText({ children }: { children: string }) {
  return <p className="text-[0.95rem] leading-6 break-words whitespace-pre-wrap text-ink">{children}</p>
}

/** A text box and a button that sends it; used for the appeal, the resolution, and the appeal decision. */
function TextAction({ label, button, onSend }: { label: string; button: string; onSend: (text: string) => Promise<unknown> }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault()
        if (text.trim() === '') {
          return
        }
        setBusy(true)
        void onSend(text.trim()).finally(() => setBusy(false))
      }}
    >
      <TextAreaField label={label} rows={4} value={text} maxLength={10_000} onChange={(event) => setText(event.target.value)} />
      <Button type="submit" disabled={busy}>
        {button}
      </Button>
    </form>
  )
}

/** The case's dates in one row: only the moments the case has reached, and the next one that matters. */
function Facts({ found }: { found: GrievanceCase }) {
  const waiting = found.status === 'SUBMITTED' || found.status === 'APPEALED'
  const facts: { label: string; at?: string | null; text?: string; overdue?: boolean }[] = [
    ...(found.complainantLoginId ? [{ label: 'Complainant', text: found.complainantLoginId }] : []),
    { label: 'Category', text: found.categoryName },
    { label: 'Submitted', at: found.submittedAt },
    ...(found.reviewStartedAt ? [{ label: 'Under review', at: found.reviewStartedAt }] : []),
    ...(found.resolvedAt ? [{ label: 'Resolved', at: found.resolvedAt }] : []),
    ...(found.appealedAt ? [{ label: 'Appealed', at: found.appealedAt }] : []),
    ...(found.closedAt ? [{ label: 'Closed', at: found.closedAt }] : []),
    ...(found.status === 'RESOLVED' && found.appealUntil ? [{ label: 'Appeal until', at: found.appealUntil }] : []),
    ...(waiting ? [{ label: 'Answer due', at: found.respondBy, overdue: found.overdue }] : []),
  ]
  return (
    <dl className="mt-4 grid grid-cols-2 gap-3 text-[0.95rem] sm:flex sm:flex-wrap sm:gap-x-0">
      {facts.map((fact) => (
        <div key={fact.label} className="min-w-0 border-line sm:pr-5 sm:not-last:mr-5 sm:not-last:border-r">
          <dt className="text-sm text-ink-muted">{fact.label}</dt>
          <dd className="flex flex-wrap items-center gap-2 break-words text-ink">
            {fact.at ? <time dateTime={fact.at}>{formatDateTime(fact.at)}</time> : fact.text}
            {fact.overdue && <span className="rounded-sm border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-xs font-medium">Overdue</span>}
          </dd>
        </div>
      ))}
    </dl>
  )
}

/** The stored moments of the case, oldest first. Nothing here is an event log; each step is a date on the case. */
function caseTimeline(found: GrievanceCase): TimelineEntry[] {
  const steps: (TimelineEntry | null)[] = [
    { key: 'submitted', title: 'Submitted', at: found.submittedAt, detail: 'Grievance submitted.', tone: 'done' },
    found.reviewStartedAt
      ? { key: 'review', title: 'Under review', at: found.reviewStartedAt, detail: 'The committee took the case under review.', tone: 'done' }
      : null,
    found.resolvedAt ? { key: 'resolved', title: 'Resolved', at: found.resolvedAt, detail: 'The committee issued its resolution.', tone: 'done' } : null,
    found.appealedAt ? { key: 'appealed', title: 'Appealed', at: found.appealedAt, detail: 'The complainant appealed the resolution.', tone: 'done' } : null,
    found.closedAt
      ? {
          key: 'closed',
          title: 'Closed',
          at: found.closedAt,
          detail: found.appealDecision ? 'The committee decided the appeal.' : 'The complainant accepted the resolution.',
          tone: 'done',
        }
      : null,
  ]
  return steps.filter((step): step is TimelineEntry => step !== null)
}

/** The whole case: its title and dates, the plain-text parts, and beside them the next step and the timeline. */
function CaseLayout({ found, back, nextStep }: { found: GrievanceCase; back: { to: string; label: string }; nextStep: ReactNode }) {
  return (
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
      <div className="min-w-0 space-y-4">
        <div className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
          <Link to={back.to} className={backLinkClasses}>
            <span aria-hidden="true">←</span> {back.label}
          </Link>
          <h2 className="mt-2 text-2xl font-bold break-words text-portal-navy">
            <span className="whitespace-nowrap">{found.caseNumber}:</span> {found.subject}
          </h2>
          <div className="mt-2">
            <GrievanceStatusTag status={found.status} />
          </div>
          <Facts found={found} />
        </div>
        <Panel title="Original details">
          <PlainText>{found.details}</PlainText>
        </Panel>
        {found.resolution && (
          <Panel title="Committee’s resolution">
            <PlainText>{found.resolution}</PlainText>
          </Panel>
        )}
        {found.appealReason && (
          <Panel title="Appeal">
            <PlainText>{found.appealReason}</PlainText>
          </Panel>
        )}
        {found.appealDecision && (
          <Panel title="Appeal decision">
            <PlainText>{found.appealDecision}</PlainText>
          </Panel>
        )}
      </div>
      <div className="min-w-0 space-y-4">
        {nextStep}
        <Panel title="Case timeline">
          <Timeline label="Case timeline" entries={caseTimeline(found)} />
        </Panel>
      </div>
    </div>
  )
}

function useAct(notify: Notify) {
  const queryClient = useQueryClient()
  return (action: () => Promise<unknown>, done: string) =>
    action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
      })
      .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save. Try again.') ?? '', true))
}

/** One of the signed-in account's own grievances, with Accept and Appeal while it is resolved. */
export function MyGrievance({ caseId, notify }: { caseId: number; notify: Notify }) {
  const found = useApiQuery(queryKeys.grievances.myCase(caseId), (signal) => getMyGrievance(caseId, signal))
  const act = useAct(notify)
  if (found.status !== 'loaded') {
    return <SectionState request={found} what="the grievance" />
  }
  const data = found.data
  const nextStep = (
    <Panel title="Your next step">
      {data.status === 'RESOLVED' ? (
        <div className="space-y-4">
          <p className="text-[0.95rem] text-ink">The grievance is resolved.</p>
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="secondary" onClick={() => void act(() => acceptGrievance(caseId), 'Resolution accepted. The case is closed.')}>
              Accept resolution
            </Button>
            <span className="text-sm text-ink-muted">Accepting closes the case.</span>
          </div>
          <p className="text-[0.95rem] text-ink">
            Or appeal once
            {data.appealUntil && (
              <>
                {' '}
                until <time dateTime={data.appealUntil} className="font-semibold">{formatDateTime(data.appealUntil)}</time>
              </>
            )}
            .
          </p>
          <TextAction label="Reason for the appeal" button="Appeal" onSend={(text) => act(() => appealGrievance(caseId, text), 'Appeal sent to the committee.')} />
        </div>
      ) : (
        <p className="text-[0.95rem] text-ink">
          {data.status === 'SUBMITTED' && 'The committee has your grievance and will answer by the due date.'}
          {data.status === 'UNDER_REVIEW' && 'The committee is reviewing your grievance.'}
          {data.status === 'APPEALED' && 'The committee is deciding your appeal.'}
          {data.status === 'CLOSED' && 'This case is closed. Nothing more can change.'}
        </p>
      )}
    </Panel>
  )
  return <CaseLayout found={data} back={{ to: '/grievances', label: 'My grievances' }} nextStep={nextStep} />
}

/** A case of the account's committees, with the next step the committee may take. */
export function CommitteeGrievance({ caseId, notify }: { caseId: number; notify: Notify }) {
  const found = useApiQuery(queryKeys.grievances.committeeCase(caseId), (signal) => getCommitteeGrievance(caseId, signal))
  const act = useAct(notify)
  if (found.status !== 'loaded') {
    return <SectionState request={found} what="the grievance" />
  }
  const data = found.data
  const nextStep = (
    <Panel title="Committee’s next step">
      <div className="space-y-4">
        <Alert tone="info">Keep this case confidential. The complainant can accept or appeal after resolution.</Alert>
        {data.status === 'SUBMITTED' && (
          <Button variant="secondary" onClick={() => void act(() => reviewGrievance(caseId), 'Taken under review.')}>
            Take under review
          </Button>
        )}
        {(data.status === 'SUBMITTED' || data.status === 'UNDER_REVIEW') && (
          <TextAction label="Resolution" button="Resolve" onSend={(text) => act(() => resolveGrievance(caseId, text), 'Resolved. The complainant can accept or appeal.')} />
        )}
        {data.status === 'APPEALED' && (
          <TextAction label="Appeal decision" button="Decide appeal" onSend={(text) => act(() => decideGrievanceAppeal(caseId, text), 'Appeal decided. The case is closed.')} />
        )}
        {data.status === 'RESOLVED' && <p className="text-[0.95rem] text-ink">Waiting for the complainant to accept or appeal.</p>}
        {data.status === 'CLOSED' && <p className="text-[0.95rem] text-ink">This case is closed. Nothing more can change.</p>}
      </div>
    </Panel>
  )
  return <CaseLayout found={data} back={{ to: '/grievances/committee', label: 'Committee cases' }} nextStep={nextStep} />
}
