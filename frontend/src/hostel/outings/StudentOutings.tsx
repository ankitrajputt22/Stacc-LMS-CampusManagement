import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { cancelOuting, extendOuting, getMyOutings, openOutingPass, reportRoomProblem, requestOuting } from '../../api/outingsApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import type { HostelOuting, OutingKind, OutingPass } from '../../types/outings'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField } from '../../ui'
import { KIND_LABEL, localToIso, outingStatusLabel } from './outingText'
import { PassQr } from './PassQr'

function failure(error: unknown, fallback: string): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  const message = getApiError(error)?.message
  return message && status !== null && status < 500 ? message : fallback
}

function RequestForm({ onDone }: { onDone: (message: string) => Promise<void> }) {
  const [kind, setKind] = useState<OutingKind>('GATE_PASS')
  const [from, setFrom] = useState('')
  const [until, setUntil] = useState('')
  const [reason, setReason] = useState('')
  const [destination, setDestination] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const leaveFrom = localToIso(from)
    const leaveUntil = localToIso(until)
    if (!leaveFrom || !leaveUntil || leaveUntil <= leaveFrom || reason.trim() === '') {
      setError('Choose when you leave and return (return after leaving), and give a reason.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await requestOuting({ kind, leaveFrom, leaveUntil, reason: reason.trim(), destination: destination.trim() || null })
      setReason('')
      setDestination('')
      await onDone('Your request was sent to your warden.')
    } catch (caught) {
      setError(failure(caught, 'Couldn’t send the request. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => void submit(event)} noValidate>
      <SelectField label="Kind" value={kind} onChange={(event) => setKind(event.target.value as OutingKind)}>
        <option value="GATE_PASS">Gate pass (up to 24 hours)</option>
        <option value="LEAVE">Leave (up to 60 days)</option>
      </SelectField>
      <TextField label="Destination (optional)" value={destination} maxLength={200} onChange={(event) => setDestination(event.target.value)} />
      <TextField label="Leaving" type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />
      <TextField label="Returning" type="datetime-local" value={until} onChange={(event) => setUntil(event.target.value)} />
      <div className="sm:col-span-2">
        <TextAreaField label="Reason" rows={2} value={reason} maxLength={1000} onChange={(event) => setReason(event.target.value)} />
      </div>
      {error && (
        <div className="sm:col-span-2">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Sending…' : 'Send request'}
        </Button>
      </div>
    </form>
  )
}

function OutingRow({ outing, onChanged }: { outing: HostelOuting; onChanged: (message: string) => Promise<void> }) {
  const [pass, setPass] = useState<OutingPass | null>(null)
  const [extending, setExtending] = useState(false)
  const [until, setUntil] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function act(action: () => Promise<unknown>, message: string, fallback: string) {
    setError(null)
    try {
      await action()
      await onChanged(message)
    } catch (caught) {
      setError(failure(caught, fallback))
    }
  }

  return (
    <li className="space-y-2 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{KIND_LABEL[outing.kind]}</span>
        <Badge tone={outing.approvalStatus === 'APPROVED' ? 'success' : outing.approvalStatus === 'PENDING' ? 'info' : 'neutral'}>
          {outingStatusLabel(outing.approvalStatus)}
        </Badge>
        {outing.outside && <Badge tone="warning">Outside now</Badge>}
        {outing.overdue && <Badge tone="danger">Overdue</Badge>}
        {outing.returnedAt && <Badge tone="neutral">Returned</Badge>}
      </div>
      <p className="text-sm text-ink-muted">
        {formatDateTime(outing.leaveFrom)} to {formatDateTime(outing.leaveUntil)}
        {outing.leaveUntil !== outing.originalUntil && ' (extended)'}
        {outing.destination && ` · ${outing.destination}`}
      </p>
      <p className="text-sm break-words whitespace-pre-line text-ink">{outing.reason}</p>
      {outing.extensions.map((extension) => (
        <p key={extension.extensionId} className="text-xs text-ink-muted">
          Extension to {formatDateTime(extension.requestedUntil)}: {outingStatusLabel(extension.approvalStatus)}
        </p>
      ))}
      <div className="flex flex-wrap gap-2">
        {outing.canShowPass && (
          <Button size="sm" onClick={() => void openOutingPass(outing.outingId).then(setPass).catch((caught: unknown) => setError(failure(caught, 'Couldn’t open the pass.')))}>
            {pass ? 'New code' : 'Show pass'}
          </Button>
        )}
        {outing.canExtend && !extending && (
          <Button size="sm" variant="secondary" onClick={() => setExtending(true)}>
            Ask for more time
          </Button>
        )}
        {outing.canCancel && (
          <Button size="sm" variant="secondary" onClick={() => void act(() => cancelOuting(outing.outingId), 'Request cancelled.', 'Couldn’t cancel.')}>
            Cancel request
          </Button>
        )}
      </div>
      {pass && <PassQr pass={pass} />}
      {extending && (
        <form
          className="grid gap-2 sm:grid-cols-[1fr_2fr_auto] sm:items-end"
          onSubmit={(event) => {
            event.preventDefault()
            const iso = localToIso(until)
            if (!iso || reason.trim() === '') {
              setError('Choose the new return and give a reason.')
              return
            }
            void act(() => extendOuting(outing.outingId, iso, reason.trim()), 'Extension sent to your warden.', 'Couldn’t send the extension.').then(() =>
              setExtending(false),
            )
          }}
        >
          <TextField label="New return" type="datetime-local" value={until} onChange={(event) => setUntil(event.target.value)} />
          <TextField label="Reason" value={reason} maxLength={1000} onChange={(event) => setReason(event.target.value)} />
          <Button type="submit" size="sm">
            Send
          </Button>
        </form>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  )
}

function RoomProblemForm() {
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState('')
  const [created, setCreated] = useState<{ id: number; number: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (subject.trim() === '' || description.trim() === '') {
          setError('Describe the problem with a subject.')
          return
        }
        setError(null)
        void reportRoomProblem(subject.trim(), description.trim())
          .then((ticket) => {
            setCreated({ id: ticket.ticketId, number: ticket.ticketNumber })
            setSubject('')
            setDescription('')
          })
          .catch((caught: unknown) => setError(failure(caught, 'Couldn’t report the problem.')))
      }}
    >
      <TextField label="Subject" value={subject} maxLength={200} onChange={(event) => setSubject(event.target.value)} />
      <TextAreaField label="What is wrong?" rows={3} value={description} maxLength={9000} onChange={(event) => setDescription(event.target.value)} />
      {created && (
        <Alert tone="success">
          Reported as <Link to={`/support/tickets/${created.id}`} className="font-medium underline">{created.number}</Link>. Your room was attached.
        </Alert>
      )}
      {error && <Alert tone="error">{error}</Alert>}
      <Button type="submit" variant="secondary">
        Report problem
      </Button>
    </form>
  )
}

/** A resident's gate passes and leave, with their passes as QR codes, and room problems sent to the helpdesk. */
export function StudentOutings() {
  const queryClient = useQueryClient()
  const outings = useApiQuery(queryKeys.hostel.outings(), (signal) => getMyOutings(signal))
  const [notice, setNotice] = useState<string | null>(null)

  async function changed(message: string) {
    setNotice(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.outings() })
  }

  return (
    <div className="space-y-6">
      {notice && <Alert tone="success">{notice}</Alert>}
      <Card title="Gate passes and leave" description="Your hostel warden approves each request. Show the pass at the gate.">
        <RequestForm onDone={changed} />
      </Card>
      <Card title="Your requests" padding="none">
        {outings.status !== 'loaded' ? (
          <SectionState request={outings} what="your requests" />
        ) : outings.data.length === 0 ? (
          <EmptyState title="No requests yet." description="Ask for a gate pass or leave above." />
        ) : (
          <ul className="divide-y divide-line">
            {outings.data.map((outing) => (
              <OutingRow key={outing.outingId} outing={outing} onChanged={changed} />
            ))}
          </ul>
        )}
      </Card>
      <Card title="Room problem" description="Sent to hostel maintenance through Support, with your room attached.">
        <RoomProblemForm />
      </Card>
    </div>
  )
}
