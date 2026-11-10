import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import {
  answerRoomSwap,
  cancelRoomChange,
  completeRoomChange,
  getAvailableBeds,
  getMyRoomChanges,
  getOpenRoomChanges,
  proposeRoomSwap,
  requestRoomMove,
} from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import type { RoomChange } from '../../types/hostel'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField } from '../../ui'
import { placeText, roomChangeState } from './roomChangeText'

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  if (status === 403) {
    return 'You don’t have access to do this. If your access changed recently, sign out and sign in again.'
  }
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

function ChangeSummary({ change }: { change: RoomChange }) {
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{change.kind === 'MOVE' ? 'Room move' : 'Bed swap'}</span>
        <Badge tone={change.status === 'COMPLETED' ? 'success' : change.status === 'OPEN' ? 'info' : 'neutral'}>{roomChangeState(change)}</Badge>
      </div>
      <p className="text-sm text-ink">
        {change.requester.loginId} · {placeText(change.requester)}
        {change.partner && ` ⇄ ${change.partner.loginId} · ${placeText(change.partner)}`}
      </p>
      <p className="text-sm text-ink-muted">Reason: {change.reason}</p>
      {change.preference && <p className="text-sm text-ink-muted">Wish: {change.preference}</p>}
      <p className="text-xs text-ink-muted">
        Asked <time dateTime={change.createdAt}>{formatDateTime(change.createdAt)}</time>
      </p>
    </div>
  )
}

/** Ask to move, or propose a swap: a reason, and for a swap the other resident's college ID. */
function NewChangeForm({ kind, onDone }: { kind: 'MOVE' | 'SWAP'; onDone: (message: string) => void }) {
  const [reason, setReason] = useState('')
  const [extra, setExtra] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (reason.trim() === '' || (kind === 'SWAP' && extra.trim() === '')) {
      setError(kind === 'SWAP' ? 'Enter the other student’s college ID and your reason.' : 'Enter your reason.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (kind === 'MOVE') {
        await requestRoomMove(reason.trim(), extra.trim() || null)
        onDone('Your request went to the warden.')
      } else {
        await proposeRoomSwap(extra.trim(), reason.trim())
        onDone('The swap was proposed. The other student answers first, then the warden decides.')
      }
    } catch (failure) {
      setError(refusal(failure, 'send the request'))
      setBusy(false)
    }
  }

  return (
    <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate>
      {kind === 'SWAP' && (
        <TextField label="Other student’s college ID" value={extra} maxLength={100} onChange={(event) => setExtra(event.target.value)} />
      )}
      <TextAreaField label="Reason" rows={3} maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} />
      {kind === 'MOVE' && (
        <TextField
          label="Wish (optional)"
          hint="For example a floor or a quiet room. Only a wish: the hostel office chooses the bed."
          value={extra}
          maxLength={300}
          onChange={(event) => setExtra(event.target.value)}
        />
      )}
      {error && <Alert tone="error">{error}</Alert>}
      <Button type="submit" disabled={busy}>
        {busy ? 'Sending…' : kind === 'MOVE' ? 'Ask to move' : 'Propose swap'}
      </Button>
    </form>
  )
}

/** A resident's room changes: ask to move, propose a swap, answer swaps proposed to them, and cancel their own. */
export function StudentRoomChanges() {
  const queryClient = useQueryClient()
  const changes = useApiQuery(queryKeys.hostel.myRoomChanges(), (signal) => getMyRoomChanges(signal))
  const [form, setForm] = useState<'MOVE' | 'SWAP' | null>(null)
  const [confirming, setConfirming] = useState<number | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function changed(text: string) {
    setForm(null)
    setConfirming(null)
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: ['hostel'] })
  }

  async function answer(change: RoomChange, accept: boolean) {
    try {
      await answerRoomSwap(change.changeId, accept)
      await changed(accept ? 'You accepted the swap. It went to the warden.' : 'You declined the swap.')
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'answer the swap') })
    }
  }

  return (
    <Card
      title="Room change"
      description="Ask to move to another bed, or swap beds with another resident. The warden decides; the hostel office chooses the new bed."
      padding="none"
      action={
        form === null ? (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" onClick={() => setForm('MOVE')}>
              Ask to move
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setForm('SWAP')}>
              Propose a swap
            </Button>
          </div>
        ) : undefined
      }
    >
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {form && (
        <div className="border-b border-line px-4 py-4 sm:px-5">
          <NewChangeForm kind={form} onDone={(text) => void changed(text)} />
          <Button className="mt-2" variant="secondary" size="sm" onClick={() => setForm(null)}>
            Cancel
          </Button>
        </div>
      )}
      {changes.status !== 'loaded' ? (
        <SectionState request={changes} what="room changes" />
      ) : changes.data.length === 0 ? (
        <EmptyState title="No room changes." />
      ) : (
        <ul className="divide-y divide-line">
          {changes.data.map((change) => (
            <li key={change.changeId} className="space-y-3 px-4 py-4 sm:px-5">
              <ChangeSummary change={change} />
              {change.canAnswer && (
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" onClick={() => void answer(change, true)}>
                    Accept swap
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => void answer(change, false)}>
                    Decline swap
                  </Button>
                </div>
              )}
              {change.canCancel &&
                (confirming === change.changeId ? (
                  <StatusConfirmation
                    question="Cancel this room change?"
                    detail="It stops here and is kept as history."
                    confirmLabel="Cancel room change"
                    busyLabel="Cancelling…"
                    dismissLabel="Keep it"
                    describeRefusal={(status, apiMessage) =>
                      apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t cancel. Try again.'
                    }
                    onCancel={() => setConfirming(null)}
                    onConfirm={async () => {
                      await cancelRoomChange(change.changeId)
                      await changed('Room change cancelled.')
                    }}
                  />
                ) : (
                  <Button size="sm" variant="secondary" onClick={() => setConfirming(change.changeId)}>
                    Cancel request
                  </Button>
                ))}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The hostel office's open room changes: an approved move needs a bed; an approved swap is carried out as it is. */
export function RoomChangesSection() {
  const queryClient = useQueryClient()
  const changes = useApiQuery(queryKeys.hostel.openRoomChanges(), (signal) => getOpenRoomChanges(signal))
  const beds = useApiQuery(queryKeys.hostel.availableBeds(), (signal) => getAvailableBeds(signal))
  const [chosen, setChosen] = useState<Record<number, string>>({})
  const [confirming, setConfirming] = useState<number | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  return (
    <Card title="Room changes" description="Moves and swaps waiting for the warden or for you. Only an approved change can be carried out." padding="none">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {changes.status !== 'loaded' ? (
        <SectionState request={changes} what="room changes" />
      ) : changes.data.length === 0 ? (
        <EmptyState title="No open room changes." />
      ) : (
        <ul className="divide-y divide-line">
          {changes.data.map((change) => (
            <li key={change.changeId} className="space-y-3 px-4 py-4 sm:px-5">
              <ChangeSummary change={change} />
              {change.canComplete && change.kind === 'MOVE' && (
                <SelectField
                  label={`New bed for ${change.requester.loginId}`}
                  value={chosen[change.changeId] ?? ''}
                  onChange={(event) => setChosen((current) => ({ ...current, [change.changeId]: event.target.value }))}
                >
                  <option value="">Choose a free bed</option>
                  {(beds.status === 'loaded' ? beds.data : []).map((bed) => (
                    <option key={bed.bedId} value={bed.bedId}>
                      {`${bed.hostelName}, room ${bed.roomNumber}, bed ${bed.bedNumber}`}
                    </option>
                  ))}
                </SelectField>
              )}
              {change.canComplete &&
                (confirming === change.changeId ? (
                  <StatusConfirmation
                    question={change.kind === 'MOVE' ? `Move ${change.requester.loginId}?` : `Swap the beds of ${change.requester.loginId} and ${change.partner?.loginId}?`}
                    detail="The old allocation is kept as history and the student is told in their Inbox. This cannot be undone."
                    confirmLabel={change.kind === 'MOVE' ? 'Move student' : 'Swap beds'}
                    busyLabel="Saving…"
                    describeRefusal={(status, apiMessage) =>
                      apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t carry out the change. Try again.'
                    }
                    onCancel={() => setConfirming(null)}
                    onConfirm={async () => {
                      const bed = chosen[change.changeId]
                      await completeRoomChange(change.changeId, bed ? Number(bed) : null)
                      setConfirming(null)
                      setMessage({ tone: 'success', text: change.kind === 'MOVE' ? 'Student moved.' : 'Beds swapped.' })
                      await queryClient.invalidateQueries({ queryKey: ['hostel'] })
                    }}
                  />
                ) : (
                  <Button
                    size="sm"
                    disabled={change.kind === 'MOVE' && !chosen[change.changeId]}
                    onClick={() => setConfirming(change.changeId)}
                  >
                    {change.kind === 'MOVE' ? 'Move student' : 'Swap beds'}
                  </Button>
                ))}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
