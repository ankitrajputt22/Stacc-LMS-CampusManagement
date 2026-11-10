import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { finalizeRollCall, getRollCall, getRollCalls, markRollCall, openRollCall } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { HostelOccupancy, RollCallDetail, RollCallMark } from '../../types/hostel'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { MARKS, markLabel } from './rollCallText'

/** Today in the browser's own time zone, as yyyy-mm-dd for a date field. */
function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

/** One sheet: a Present, Absent, or Out choice per resident while open; Save marks and Finalize (confirmed). */
function Sheet({ detail, onChanged }: { detail: RollCallDetail; onChanged: (message: string) => Promise<void> }) {
  const [marks, setMarks] = useState<Record<number, RollCallMark>>(() =>
    Object.fromEntries(detail.entries.map((entry) => [entry.entryId, entry.status])),
  )
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const open = detail.summary.status === 'OPEN'
  const changed = detail.entries.filter((entry) => marks[entry.entryId] !== entry.status && marks[entry.entryId] !== 'UNMARKED')

  async function save() {
    setError(null)
    try {
      await markRollCall(detail.summary.rollCallId, changed.map((entry) => ({ entryId: entry.entryId, status: marks[entry.entryId] })))
      await onChanged('Marks saved.')
    } catch (failure) {
      setError(refusal(failure, 'save the marks'))
    }
  }

  return (
    <div className="space-y-3">
      {detail.entries.length === 0 ? (
        <EmptyState title="No residents on this sheet." description="Nobody was allocated here when it was opened." />
      ) : (
        <ul className="divide-y divide-line rounded-control border border-line">
          {detail.entries.map((entry) => (
            <li key={entry.entryId} className="flex flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between">
              <span className="text-sm text-ink">
                <span className="font-medium">{entry.studentLoginId}</span> · room {entry.room}, bed {entry.bed}
                {entry.prefilledOut && <span className="text-ink-muted"> · went out at the gate</span>}
              </span>
              {open ? (
                <fieldset className="flex flex-wrap gap-3 text-sm">
                  <legend className="sr-only">{`Mark ${entry.studentLoginId}`}</legend>
                  {MARKS.map((choice) => (
                    <label key={choice.value} className="flex items-center gap-1.5">
                      <input
                        type="radio"
                        name={`mark-${entry.entryId}`}
                        checked={marks[entry.entryId] === choice.value}
                        onChange={() => setMarks((current) => ({ ...current, [entry.entryId]: choice.value }))}
                      />
                      {choice.label}
                    </label>
                  ))}
                </fieldset>
              ) : (
                <Badge tone={entry.status === 'ABSENT' ? 'danger' : 'neutral'}>{markLabel(entry.status)}</Badge>
              )}
            </li>
          ))}
        </ul>
      )}
      {error && <Alert tone="error">{error}</Alert>}
      {open &&
        (confirming ? (
          <StatusConfirmation
            question="Finalize this roll call?"
            detail="Every resident must be marked. A finalized roll call no longer changes."
            confirmLabel="Finalize"
            busyLabel="Finalizing…"
            describeRefusal={(status, message) => (message && status !== null && status < 500 ? message : 'Couldn’t finalize. Try again.')}
            onCancel={() => setConfirming(false)}
            onConfirm={async () => {
              await finalizeRollCall(detail.summary.rollCallId)
              setConfirming(false)
              await onChanged('Roll call finalized.')
            }}
          />
        ) : (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" disabled={changed.length === 0} onClick={() => void save()}>
              Save marks
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setConfirming(true)}>
              Finalize
            </Button>
          </div>
        ))}
    </div>
  )
}

/**
 * Night roll call for a warden's hostels: open tonight's sheet for a hostel or block (pre-filled from the gate log,
 * nobody starts as absent), mark residents, and finalize. Recent sheets are listed with their counts.
 */
export function WardenRollCall({ hostels }: { hostels: HostelOccupancy[] }) {
  const queryClient = useQueryClient()
  const [hostelId, setHostelId] = useState<number>(hostels[0].hostelId)
  const [block, setBlock] = useState('')
  const [night, setNight] = useState(today)
  const [openId, setOpenId] = useState<number | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const recent = useApiQuery(queryKeys.hostel.rollCalls(hostelId), (signal) => getRollCalls(hostelId, signal))
  const sheet = useApiQuery(openId === null ? null : queryKeys.hostel.rollCall(openId), (signal) => getRollCall(openId ?? 0, signal))

  async function refreshed(text: string) {
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: ['hostel'] })
  }

  async function start(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      const opened = await openRollCall(hostelId, block.trim() || null, night || null)
      setOpenId(opened.summary.rollCallId)
      await refreshed(`Roll call opened with ${opened.entries.length} residents.`)
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'open the roll call') })
    }
  }

  return (
    <Card title="Night roll call" description="One sheet per hostel or block and night. Residents who went out at the gate start as out; nobody starts as absent." padding="none">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      <form className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-4 sm:items-end sm:px-5" onSubmit={(event) => void start(event)}>
        <SelectField label="Hostel" value={String(hostelId)} onChange={(event) => setHostelId(Number(event.target.value))}>
          {hostels.map((hostel) => (
            <option key={hostel.hostelId} value={hostel.hostelId}>
              {hostel.name}
            </option>
          ))}
        </SelectField>
        <TextField label="Block (optional)" value={block} maxLength={50} onChange={(event) => setBlock(event.target.value)} />
        <TextField label="Night" type="date" value={night} onChange={(event) => setNight(event.target.value)} />
        <Button type="submit">Open roll call</Button>
      </form>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {recent.status !== 'loaded' ? (
        <SectionState request={recent} what="roll calls" />
      ) : recent.data.length === 0 ? (
        <EmptyState title="No roll calls yet." />
      ) : (
        <ul className="divide-y divide-line">
          {recent.data.map((summary) => (
            <li key={summary.rollCallId} className="space-y-3 px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-ink">{`${summary.rollDate}${summary.block ? ` · block ${summary.block}` : ''}`}</span>
                <Badge tone={summary.status === 'FINALIZED' ? 'success' : 'warning'}>{summary.status === 'FINALIZED' ? 'Finalized' : 'Open'}</Badge>
                <span className="text-sm text-ink-muted">
                  {`${summary.present} present · ${summary.absent} absent · ${summary.out} out · ${summary.unmarked} not marked`}
                </span>
                {openId !== summary.rollCallId && (
                  <Button size="sm" variant="secondary" onClick={() => setOpenId(summary.rollCallId)}>
                    {summary.status === 'OPEN' ? 'Mark' : 'View'}
                    <span className="sr-only">{` the roll call of ${summary.rollDate}`}</span>
                  </Button>
                )}
              </div>
              {openId === summary.rollCallId &&
                (sheet.status !== 'loaded' ? (
                  <SectionState request={sheet} what="the roll call" />
                ) : (
                  <Sheet key={`${sheet.data.summary.rollCallId}-${sheet.data.summary.status}-${sheet.data.summary.unmarked}`} detail={sheet.data} onChanged={refreshed} />
                ))}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
