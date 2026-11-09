import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { apiBaseUrl } from '../api/apiClient'
import { createCalendarFeed, createPersonalEvent, deletePersonalEvent, getCalendarFeed, revokeCalendarFeed } from '../api/calendarApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { PersonalCalendarEvent } from '../types/calendar'
import { Alert, Button, Card, TextField } from '../ui'
import { dateRangeText } from './calendarDates'

function refusal(status: number | null, apiMessage: string | null, fallback: string): string {
  if (status === 403) {
    return 'You don’t have access to your personal calendar. If your access changed recently, sign out and sign in again.'
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : fallback
}

/** The month's personal entries with Add and a confirmed Remove. Only the owner ever sees them. */
export function PersonalEntries({ events, monthStart }: { events: PersonalCalendarEvent[]; monthStart: string }) {
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<PersonalCalendarEvent | null>(null)
  const [title, setTitle] = useState('')
  const [startDate, setStartDate] = useState(monthStart)
  const [endDate, setEndDate] = useState(monthStart)
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['calendar', 'personal'] })
  }

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (title.trim() === '' || startDate === '' || endDate === '' || endDate < startDate) {
      setError('Enter a title and dates, with the end on or after the start.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      await createPersonalEvent({ title: title.trim(), notes: notes.trim() === '' ? null : notes, startDate, endDate })
      setAdding(false)
      setTitle('')
      setNotes('')
      await refresh()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null, 'Couldn’t add the entry. Try again.'))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card
      title="My entries"
      description="Only you can see these."
      padding="none"
      action={adding ? undefined : <Button size="sm" onClick={() => setAdding(true)}>Add entry</Button>}
    >
      {adding && (
        <form className="space-y-3 border-b border-line px-4 py-4 sm:px-5" onSubmit={(event) => void add(event)} noValidate>
          <TextField label="Title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Start date" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
            <TextField label="End date" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
          </div>
          <TextField label="Notes (optional)" value={notes} maxLength={2000} onChange={(event) => setNotes(event.target.value)} />
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={busy}>
              {busy ? 'Adding…' : 'Add entry'}
            </Button>
            <Button variant="secondary" onClick={() => setAdding(false)} disabled={busy}>
              Cancel
            </Button>
          </div>
        </form>
      )}
      {removing && (
        <div className="border-b border-line px-4 py-4 sm:px-5">
          <StatusConfirmation
            question={`Remove “${removing.title}”?`}
            detail="It is removed from your calendar and your subscription for good."
            confirmLabel="Remove entry"
            busyLabel="Removing…"
            describeRefusal={(status, message) => refusal(status, message, 'Couldn’t remove the entry. Try again.')}
            onCancel={() => setRemoving(null)}
            onConfirm={async () => {
              await deletePersonalEvent(removing.eventId)
              setRemoving(null)
              await refresh()
            }}
          />
        </div>
      )}
      {events.length === 0 ? (
        <EmptyState title="No entries this month." description="Add reminders of your own; they also appear in your subscription." />
      ) : (
        <ul className="divide-y divide-line">
          {events.map((event) => (
            <li key={event.eventId} className="flex flex-wrap items-start justify-between gap-3 px-4 py-3 sm:px-5">
              <div className="min-w-0">
                <p className="font-semibold break-words text-ink">{event.title}</p>
                <p className="text-sm text-ink-muted">{dateRangeText(event.startDate, event.endDate)}</p>
                {event.notes && <p className="mt-1 text-sm break-words whitespace-pre-line text-ink">{event.notes}</p>}
              </div>
              <Button variant="secondary" size="sm" onClick={() => setRemoving(event)}>
                Remove<span className="sr-only">{` ${event.title}`}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The full address of a feed path on the API's own host. */
function feedAddress(feedPath: string): string {
  return new URL(feedPath, new URL(apiBaseUrl, window.location.origin)).toString()
}

/**
 * The private subscription link: make one (shown once, with Copy), replace it, or revoke it, confirmed first. Calendar
 * apps read the link without signing in, so it must be kept private; revoking stops it at once.
 */
export function CalendarSubscription() {
  const queryClient = useQueryClient()
  const status = useApiQuery(queryKeys.calendar.feed(), (signal) => getCalendarFeed(signal))
  const [address, setAddress] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<'replace' | 'revoke' | null>(null)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function make() {
    setError(null)
    const feed = await createCalendarFeed()
    setAddress(feedAddress(feed.feedPath))
    setCopied(false)
    await queryClient.invalidateQueries({ queryKey: queryKeys.calendar.feed() })
  }

  return (
    <Card title="Subscribe in another calendar app" description="A private link that shows your Stacc calendar in apps such as Google Calendar or Outlook.">
      {status.status !== 'loaded' ? (
        <SectionState request={status} what="your subscription" />
      ) : (
        <div className="space-y-3 text-sm">
          {address && (
            <Alert tone="success">
              <p className="font-medium">Your new link. Copy it now: it is not shown again.</p>
              <p className="mt-1 break-all font-mono text-xs">{address}</p>
              <Button
                variant="secondary"
                size="sm"
                className="mt-2"
                onClick={() => void navigator.clipboard?.writeText(address).then(() => setCopied(true), () => setCopied(false))}
              >
                {copied ? 'Copied' : 'Copy link'}
              </Button>
            </Alert>
          )}
          <p className="text-ink">
            {status.data.active && status.data.createdAt
              ? `A link is active since ${formatDateTime(status.data.createdAt)}. Anyone with it can read your calendar.`
              : 'No link is active.'}
          </p>
          {error && <Alert tone="error">{error}</Alert>}
          {confirming ? (
            <StatusConfirmation
              question={confirming === 'revoke' ? 'Revoke your subscription link?' : 'Make a new link?'}
              detail={
                confirming === 'revoke'
                  ? 'Calendar apps using it stop getting your calendar at once.'
                  : 'The current link stops working at once; calendar apps need the new one.'
              }
              confirmLabel={confirming === 'revoke' ? 'Revoke link' : 'Make new link'}
              describeRefusal={(code, message) => refusal(code, message, 'Couldn’t change the link. Try again.')}
              onCancel={() => setConfirming(null)}
              onConfirm={async () => {
                if (confirming === 'revoke') {
                  await revokeCalendarFeed()
                  setAddress(null)
                  await queryClient.invalidateQueries({ queryKey: queryKeys.calendar.feed() })
                } else {
                  await make()
                }
                setConfirming(null)
              }}
            />
          ) : (
            <div className="flex flex-wrap gap-2">
              {status.data.active ? (
                <>
                  <Button variant="secondary" size="sm" onClick={() => setConfirming('replace')}>
                    Make a new link
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => setConfirming('revoke')}>
                    Revoke link
                  </Button>
                </>
              ) : (
                <Button
                  size="sm"
                  onClick={() =>
                    void make().catch((failure: unknown) => {
                      const code = getHttpStatus(failure)
                      if (code !== 401) {
                        setError(refusal(code, getApiError(failure)?.message ?? null, 'Couldn’t make the link. Try again.'))
                      }
                    })
                  }
                >
                  Make a subscription link
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
