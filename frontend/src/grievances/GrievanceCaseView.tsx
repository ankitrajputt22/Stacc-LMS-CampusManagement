import { useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
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
import { Button, TextAreaField } from '../ui'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium text-ink-muted">{label}</dt>
      <dd className="text-sm break-words whitespace-pre-wrap text-ink">{children}</dd>
    </div>
  )
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
      <TextAreaField label={label} rows={3} value={text} maxLength={10_000} onChange={(event) => setText(event.target.value)} />
      <Button type="submit" size="sm" disabled={busy}>{button}</Button>
    </form>
  )
}

function Details({ found }: { found: GrievanceCase }) {
  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      {found.complainantLoginId && <Field label="Complainant">{found.complainantLoginId}</Field>}
      <Field label="Submitted"><time dateTime={found.submittedAt}>{formatDateTime(found.submittedAt)}</time></Field>
      <div className="sm:col-span-2"><Field label="Details">{found.details}</Field></div>
      {found.resolution && <div className="sm:col-span-2"><Field label="Committee’s resolution">{found.resolution}</Field></div>}
      {found.appealReason && <div className="sm:col-span-2"><Field label="Appeal">{found.appealReason}</Field></div>}
      {found.appealDecision && <div className="sm:col-span-2"><Field label="Appeal decision">{found.appealDecision}</Field></div>}
    </dl>
  )
}

/** One of the signed-in account's own grievances, with Accept and Appeal while it is resolved. */
export function MyGrievance({ caseId, notify }: { caseId: number; notify: (text: string, error?: boolean) => void }) {
  const queryClient = useQueryClient()
  const found = useApiQuery(queryKeys.grievances.myCase(caseId), (signal) => getMyGrievance(caseId, signal))
  const act = (action: () => Promise<unknown>, done: string) =>
    action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
      })
      .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save. Try again.') ?? '', true))
  if (found.status !== 'loaded') {
    return <SectionState request={found} what="the grievance" />
  }
  const data = found.data
  return (
    <div className="space-y-3">
      <Details found={data} />
      {data.status === 'RESOLVED' && (
        <div className="space-y-3 rounded-control border border-line p-3">
          <p className="text-sm text-ink-muted">
            Accept the resolution to close the case, or appeal it once{data.appealUntil ? ` until ${formatDateTime(data.appealUntil)}` : ''}.
          </p>
          <Button size="sm" variant="secondary" onClick={() => void act(() => acceptGrievance(caseId), 'Resolution accepted. The case is closed.')}>
            Accept resolution
          </Button>
          <TextAction label="Reason for the appeal" button="Appeal" onSend={(text) => act(() => appealGrievance(caseId, text), 'Appeal sent to the committee.')} />
        </div>
      )}
    </div>
  )
}

/** A case of the account's committees, with the next step the committee may take. */
export function CommitteeGrievance({ caseId, notify }: { caseId: number; notify: (text: string, error?: boolean) => void }) {
  const queryClient = useQueryClient()
  const found = useApiQuery(queryKeys.grievances.committeeCase(caseId), (signal) => getCommitteeGrievance(caseId, signal))
  const act = (action: () => Promise<unknown>, done: string) =>
    action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
      })
      .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save. Try again.') ?? '', true))
  if (found.status !== 'loaded') {
    return <SectionState request={found} what="the grievance" />
  }
  const data = found.data
  return (
    <div className="space-y-3">
      <Details found={data} />
      {data.status === 'SUBMITTED' && (
        <Button size="sm" variant="secondary" onClick={() => void act(() => reviewGrievance(caseId), 'Taken under review.')}>
          Take under review
        </Button>
      )}
      {(data.status === 'SUBMITTED' || data.status === 'UNDER_REVIEW') && (
        <TextAction label="Resolution" button="Resolve" onSend={(text) => act(() => resolveGrievance(caseId, text), 'Resolved. The complainant can accept or appeal.')} />
      )}
      {data.status === 'APPEALED' && (
        <TextAction label="Appeal decision" button="Decide appeal" onSend={(text) => act(() => decideGrievanceAppeal(caseId, text), 'Appeal decided. The case is closed.')} />
      )}
    </div>
  )
}
