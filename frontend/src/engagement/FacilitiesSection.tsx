import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { bookFacility, cancelBooking, getFacilities, getFacilityBookings, getMyBookings } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { CampusFacility } from '../types/engagement'
import { Alert, Button, Card, DataTable, TextField } from '../ui'
import { BOOKING_LABEL, DAY_LABEL, failureText, localToIso } from './engagementText'

function FacilityRow({ facility, canBook }: { facility: CampusFacility; canBook: boolean }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const bookings = useApiQuery(open ? queryKeys.engagement.facilityBookings(facility.facilityId) : null, (signal) =>
    getFacilityBookings(facility.facilityId, signal),
  )
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [purpose, setPurpose] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function book() {
    const start = localToIso(startsAt)
    const end = localToIso(endsAt)
    if (!start || !end || purpose.trim() === '') {
      setMessage({ tone: 'error', text: 'Choose a start, an end, and say what it is for.' })
      return
    }
    try {
      const booking = await bookFacility(facility.facilityId, { startsAt: start, endsAt: end, purpose: purpose.trim() })
      setMessage({ tone: 'success', text: booking.status === 'PENDING' ? 'Booked, waiting for approval.' : 'Booked.' })
      setPurpose('')
      await queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
    } catch (caught) {
      const text = failureText(caught, 'Couldn’t book.')
      if (text) {
        setMessage({ tone: 'error', text })
      }
    }
  }

  return (
    <li className="space-y-4 px-4 py-5 sm:px-5">
      <div className="grid gap-3 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.3fr)_auto] lg:items-start lg:gap-0 lg:[&>*:not(:first-child):not(:last-child)]:border-l lg:[&>*:not(:first-child):not(:last-child)]:border-line lg:[&>*:not(:first-child):not(:last-child)]:px-5">
        <div className="min-w-0">
          <h3 className="text-xl font-bold break-words text-portal-navy">{facility.name}</h3>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            <span className="rounded-sm bg-sky-50 px-2 py-0.5 text-sm text-ink">{facility.kind.charAt(0) + facility.kind.slice(1).toLowerCase()}</span>
            {facility.needsApproval && <span className="rounded-sm bg-orange-50 px-2 py-0.5 text-sm text-ink">Needs approval</span>}
            {!facility.active && <span className="rounded-sm bg-surface-muted px-2 py-0.5 text-sm text-ink-muted">Not taking bookings</span>}
          </div>
        </div>
        <p className="text-[0.95rem] text-ink">
          Open {facility.opensAt.slice(0, 5)} to {facility.closesAt.slice(0, 5)} <span className="text-ink-muted">(college time)</span>
        </p>
        <p className="text-[0.95rem] break-words text-ink">{facility.location ?? '—'}</p>
        <div className="space-y-1 text-[0.95rem] text-ink">
          {facility.capacity && <p>Up to {facility.capacity} people</p>}
          {facility.classHours.length > 0 && (
            <p className="text-sm">
              Class hours (no bookings):{' '}
              {facility.classHours.map((slot) => `${DAY_LABEL[slot.dayOfWeek]} ${slot.startsAt.slice(0, 5)}–${slot.endsAt.slice(0, 5)} ${slot.label}`).join('; ')}
            </p>
          )}
          {facility.closures.map((closure) => (
            <p key={closure.closureId} className="text-sm text-warning">
              Closed {closure.startDate}
              {closure.endDate !== closure.startDate && ` to ${closure.endDate}`}: {closure.reason}
            </p>
          ))}
        </div>
        <div className="lg:pl-5">
          <Button variant={open ? 'secondary' : 'primary'} onClick={() => setOpen(!open)}>
            {open ? 'Hide' : canBook ? 'See times and book' : 'See bookings'}
            <span className="sr-only">{` ${facility.name}`}</span>
          </Button>
        </div>
      </div>
      {open && (
        <div className="space-y-4 rounded-panel border border-line px-4 py-4 sm:px-5">
          <div>
            <h4 className="text-lg font-bold text-portal-navy">Taken</h4>
            <span aria-hidden="true" className="mt-1 mb-3 block h-0.5 w-12 rounded bg-portal-cta" />
            {bookings.status !== 'loaded' ? (
              <SectionState request={bookings} what="bookings" />
            ) : bookings.data.length === 0 ? (
              <p className="text-sm text-ink-muted">No coming bookings: every opening hour is free.</p>
            ) : (
              <ul className="space-y-2 text-[0.95rem] text-ink">
                {bookings.data.map((booking) => (
                  <li key={booking.bookingId} className="rounded-control border border-line bg-surface-muted px-3 py-2.5">
                    <span className="sr-only">Taken </span>
                    {formatDateTime(booking.startsAt)} to {formatDateTime(booking.endsAt)}
                    {booking.status === 'PENDING' && ' (waiting for approval)'}
                  </li>
                ))}
              </ul>
            )}
          </div>
          {facility.closures.map((closure) => (
            <Alert key={closure.closureId} tone="warning">
              Closed {closure.startDate}
              {closure.endDate !== closure.startDate && ` to ${closure.endDate}`}: {closure.reason}
            </Alert>
          ))}
          {canBook && (
            <h4 className="border-t border-line pt-4 text-lg font-bold text-portal-navy">Book facility</h4>
          )}
          {canBook && (
            <div className="grid max-w-3xl gap-3 sm:grid-cols-2">
              <TextField label="From" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
              <TextField label="To" type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} />
              <div className="sm:col-span-2">
                <TextField label="Purpose" value={purpose} maxLength={500} onChange={(event) => setPurpose(event.target.value)} />
              </div>
              {message && (
                <div className="sm:col-span-2">
                  <Alert tone={message.tone}>{message.text}</Alert>
                </div>
              )}
              <div>
                <Button onClick={() => void book()}>Book</Button>
              </div>
            </div>
          )}
          {!canBook && message && <Alert tone={message.tone}>{message.text}</Alert>}
        </div>
      )}
    </li>
  )
}

const BOOKING_TONE = {
  CONFIRMED: 'border-success-line bg-success-soft text-ink',
  PENDING: 'border-amber-300 bg-amber-50 text-ink',
  REJECTED: 'border-danger/40 bg-danger/5 text-danger',
  CANCELLED: 'border-line-strong bg-surface-muted text-ink-muted',
} as const

function MyBookings() {
  const queryClient = useQueryClient()
  const bookings = useApiQuery(queryKeys.engagement.myBookings(), (signal) => getMyBookings(signal))
  const [error, setError] = useState<string | null>(null)
  const [loadedAt] = useState(() => Date.now())
  return (
    <Card title="My bookings" padding="none">
      {bookings.status !== 'loaded' ? (
        <SectionState request={bookings} what="your bookings" />
      ) : bookings.data.length === 0 ? (
        <EmptyState title="No bookings yet." description="Book a facility above." />
      ) : (
        <DataTable<(typeof bookings.data)[number]>
          caption="My bookings"
          rows={bookings.data}
          rowKey={(booking) => booking.bookingId}
          columns={[
            { key: 'facility', header: 'Facility', primary: true, cell: (booking) => <span className="font-medium">{booking.facilityName}</span> },
            { key: 'from', header: 'From', cell: (booking) => <time dateTime={booking.startsAt}>{formatDateTime(booking.startsAt)}</time> },
            { key: 'to', header: 'To', cell: (booking) => <time dateTime={booking.endsAt}>{formatDateTime(booking.endsAt)}</time> },
            {
              key: 'status',
              header: 'Status',
              cell: (booking) => (
                <span className={`inline-block rounded-sm border px-2 py-0.5 text-sm ${BOOKING_TONE[booking.status]}`}>{BOOKING_LABEL[booking.status]}</span>
              ),
            },
            {
              key: 'action',
              header: 'Action',
              cell: (booking) =>
                (booking.status === 'CONFIRMED' || booking.status === 'PENDING') && new Date(booking.startsAt).getTime() > loadedAt ? (
                  <Button
                    size="sm"
                    onClick={() =>
                      void cancelBooking(booking.bookingId)
                        .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
                        .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t cancel.')))
                    }
                  >
                    Cancel booking<span className="sr-only">{` ${booking.facilityName}`}</span>
                  </Button>
                ) : (
                  <span aria-label="None">–</span>
                ),
            },
          ]}
        />
      )}
      {error && <div className="px-4 pb-3"><Alert tone="error">{error}</Alert></div>}
    </Card>
  )
}

/** Facilities with hours and closures, their taken times, booking, and the account's own bookings. */
export function FacilitiesSection({ canBook }: { canBook: boolean }) {
  const facilities = useApiQuery(queryKeys.engagement.facilities(), (signal) => getFacilities(signal))
  return (
    <div className="space-y-6">
      <Card title="Facilities" padding="none">
        {facilities.status !== 'loaded' ? (
          <SectionState request={facilities} what="facilities" />
        ) : facilities.data.length === 0 ? (
          <EmptyState title="No facilities yet." description="The college adds rooms, labs, halls, and courts." />
        ) : (
          <ul className="divide-y divide-line">
            {facilities.data.map((facility) => (
              <FacilityRow key={facility.facilityId} facility={facility} canBook={canBook && facility.active} />
            ))}
          </ul>
        )}
      </Card>
      {canBook && <MyBookings />}
    </div>
  )
}
