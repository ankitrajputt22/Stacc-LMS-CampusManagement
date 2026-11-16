import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { bookFacility, cancelBooking, getFacilities, getFacilityBookings, getMyBookings } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { CampusFacility } from '../types/engagement'
import { Alert, Badge, Button, Card, TextField } from '../ui'
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
    <li className="space-y-2 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{facility.name}</span>
        <Badge tone="neutral">{facility.kind.toLowerCase()}</Badge>
        {facility.needsApproval && <Badge tone="info">Needs approval</Badge>}
      </div>
      <p className="text-sm text-ink-muted">
        Open {facility.opensAt.slice(0, 5)} to {facility.closesAt.slice(0, 5)} (college time)
        {facility.location && ` · ${facility.location}`}
        {facility.capacity && ` · up to ${facility.capacity} people`}
      </p>
      {facility.classHours.length > 0 && (
        <p className="text-sm text-ink-muted">
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
      <Button size="sm" variant="secondary" onClick={() => setOpen(!open)}>
        {open ? 'Hide' : canBook ? 'See times and book' : 'See bookings'}
        <span className="sr-only">{` ${facility.name}`}</span>
      </Button>
      {open && (
        <div className="space-y-3">
          {bookings.status !== 'loaded' ? (
            <SectionState request={bookings} what="bookings" />
          ) : bookings.data.length === 0 ? (
            <p className="text-sm text-ink-muted">No coming bookings: every opening hour is free.</p>
          ) : (
            <ul className="text-sm text-ink">
              {bookings.data.map((booking) => (
                <li key={booking.bookingId}>
                  Taken {formatDateTime(booking.startsAt)} to {formatDateTime(booking.endsAt)}
                  {booking.status === 'PENDING' && ' (waiting for approval)'}
                </li>
              ))}
            </ul>
          )}
          {canBook && (
            <div className="grid gap-2 sm:grid-cols-[1fr_1fr_2fr_auto] sm:items-end">
              <TextField label="From" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
              <TextField label="To" type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} />
              <TextField label="Purpose" value={purpose} maxLength={500} onChange={(event) => setPurpose(event.target.value)} />
              <Button onClick={() => void book()}>Book</Button>
            </div>
          )}
          {message && <Alert tone={message.tone}>{message.text}</Alert>}
        </div>
      )}
    </li>
  )
}

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
        <ul className="divide-y divide-line text-sm">
          {bookings.data.map((booking) => (
            <li key={booking.bookingId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 sm:px-5">
              <span>
                <span className="font-medium text-ink">{booking.facilityName}</span>
                <span className="text-ink-muted"> · {formatDateTime(booking.startsAt)} to {formatDateTime(booking.endsAt)} · </span>
                <Badge tone={booking.status === 'CONFIRMED' ? 'success' : booking.status === 'PENDING' ? 'info' : 'neutral'}>{BOOKING_LABEL[booking.status]}</Badge>
              </span>
              {(booking.status === 'CONFIRMED' || booking.status === 'PENDING') && new Date(booking.startsAt).getTime() > loadedAt && (
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    void cancelBooking(booking.bookingId)
                      .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
                      .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t cancel.')))
                  }
                >
                  Cancel booking<span className="sr-only">{` ${booking.facilityName}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
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
