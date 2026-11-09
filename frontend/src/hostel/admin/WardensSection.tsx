import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { assignHostelWarden, endHostelWarden, getHostels, getHostelWardens } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'

function failure(error: unknown): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  const message = getApiError(error)?.message
  return message && status !== null && status < 500 ? message : 'Couldn’t save. Try again.'
}

function HostelWardens({ hostelId }: { hostelId: number }) {
  const queryClient = useQueryClient()
  const wardens = useApiQuery(queryKeys.hostel.wardens(hostelId), (signal) => getHostelWardens(hostelId, signal))
  const [loginId, setLoginId] = useState('')
  const [block, setBlock] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function run(action: () => Promise<unknown>, done: string) {
    try {
      await action()
      setMessage({ tone: 'success', text: done })
      await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.wardens(hostelId) })
    } catch (caught) {
      const text = failure(caught)
      if (text) {
        setMessage({ tone: 'error', text })
      }
    }
  }

  return (
    <div className="space-y-4">
      <form
        className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-end"
        onSubmit={(event) => {
          event.preventDefault()
          void run(() => assignHostelWarden(hostelId, loginId.trim(), block.trim() || null), `Assigned ${loginId.trim()}.`).then(() => {
            setLoginId('')
            setBlock('')
          })
        }}
      >
        <TextField label="Warden login ID" value={loginId} maxLength={100} hint="An active account holding the warden role." onChange={(event) => setLoginId(event.target.value)} />
        <TextField label="Block (optional)" value={block} maxLength={50} onChange={(event) => setBlock(event.target.value)} />
        <Button type="submit">Assign warden</Button>
      </form>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      {wardens.status !== 'loaded' ? (
        <SectionState request={wardens} what="wardens" />
      ) : wardens.data.length === 0 ? (
        <EmptyState title="No wardens yet." description="Assign a warden to this hostel." />
      ) : (
        <ul className="divide-y divide-line rounded-panel border border-line">
          {wardens.data.map((warden) => (
            <li key={warden.wardenAssignmentId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
              <span>
                <span className="font-medium text-ink">{warden.loginId}</span>
                <span className="text-ink-muted">
                  {warden.block ? ` · Block ${warden.block}` : ' · Whole hostel'} · since {formatDateTime(warden.assignedAt)}
                </span>
                {warden.endedAt && <span className="ml-2"><Badge tone="neutral">Ended</Badge></span>}
              </span>
              {!warden.endedAt && (
                <Button size="sm" variant="secondary" onClick={() => void run(() => endHostelWarden(warden.wardenAssignmentId), `Ended ${warden.loginId}'s assignment.`)}>
                  End assignment<span className="sr-only">{` of ${warden.loginId}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Hostel Management → Wardens: who looks after each hostel, or one block of it. Assignments end; nothing is deleted. */
export function WardensSection() {
  const hostels = useApiQuery(['hostel', 'list'], (signal) => getHostels(signal))
  const [hostelId, setHostelId] = useState<number | null>(null)
  return (
    <Card title="Wardens">
      {hostels.status !== 'loaded' ? (
        <SectionState request={hostels} what="hostels" />
      ) : (
        <div className="space-y-4">
          <div className="max-w-sm">
            <SelectField label="Hostel" value={hostelId ?? ''} onChange={(event) => setHostelId(event.target.value === '' ? null : Number(event.target.value))}>
              <option value="">Choose a hostel…</option>
              {hostels.data.map((hostel) => (
                <option key={hostel.hostelId} value={hostel.hostelId}>
                  {hostel.name}
                </option>
              ))}
            </SelectField>
          </div>
          {hostelId !== null && <HostelWardens key={hostelId} hostelId={hostelId} />}
        </div>
      )}
    </Card>
  )
}
