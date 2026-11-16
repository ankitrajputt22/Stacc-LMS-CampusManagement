import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addClassHour, closeFacility, createFacility, getFacilities, removeClassHour } from '../../api/engagementApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { DayOfWeek, FacilityKind } from '../../types/engagement'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { DAY_LABEL, failureText } from '../engagementText'

const KINDS: FacilityKind[] = ['ROOM', 'CLASSROOM', 'LAB', 'EQUIPMENT', 'HALL', 'COURT', 'GROUND', 'OTHER']
const DAYS = Object.keys(DAY_LABEL) as DayOfWeek[]

/** Campus Management → Facilities: add bookable places with hours and approval, and close them for some days. */
export function FacilitiesAdmin() {
  const queryClient = useQueryClient()
  const facilities = useApiQuery(queryKeys.engagement.facilities(), (signal) => getFacilities(signal))
  const [form, setForm] = useState({ code: '', name: '', kind: 'ROOM' as FacilityKind, location: '', capacity: '', opensAt: '08:00', closesAt: '20:00', needsApproval: false })
  const [closure, setClosure] = useState({ facilityId: '', startDate: '', endDate: '', reason: '' })
  const [slot, setSlot] = useState({ facilityId: '', dayOfWeek: 'MONDAY' as DayOfWeek, startsAt: '09:00', endsAt: '10:00', label: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const done = (text: string, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }
  return (
    <div className="space-y-6">
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <Card title="Add a facility">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            void createFacility({
              code: form.code.trim().toUpperCase(), name: form.name.trim(), kind: form.kind, location: form.location.trim() || null,
              capacity: form.capacity === '' ? null : Number(form.capacity), opensAt: form.opensAt, closesAt: form.closesAt, needsApproval: form.needsApproval,
            })
              .then(() => done('Facility added.'))
              .catch((caught: unknown) => done(failureText(caught, 'Couldn’t add the facility.') ?? '', true))
          }}
        >
          <TextField label="Code" value={form.code} maxLength={50} onChange={(event) => setForm({ ...form, code: event.target.value })} />
          <TextField label="Name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          <SelectField label="Kind" value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value as FacilityKind })}>
            {KINDS.map((kind) => <option key={kind} value={kind}>{kind.charAt(0) + kind.slice(1).toLowerCase()}</option>)}
          </SelectField>
          <TextField label="Location (optional)" value={form.location} maxLength={200} onChange={(event) => setForm({ ...form, location: event.target.value })} />
          <TextField label="Opens" type="time" value={form.opensAt} onChange={(event) => setForm({ ...form, opensAt: event.target.value })} />
          <TextField label="Closes" type="time" value={form.closesAt} onChange={(event) => setForm({ ...form, closesAt: event.target.value })} />
          <TextField label="Capacity (optional)" inputMode="numeric" value={form.capacity} onChange={(event) => setForm({ ...form, capacity: event.target.value.replace(/\D/g, '') })} />
          <label className="inline-flex items-center gap-2 self-end text-sm text-ink">
            <input type="checkbox" className="size-4" checked={form.needsApproval} onChange={(event) => setForm({ ...form, needsApproval: event.target.checked })} />
            Bookings need approval
          </label>
          <div><Button type="submit">Add facility</Button></div>
        </form>
      </Card>
      <Card title="Close a facility">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            void closeFacility(Number(closure.facilityId), { startDate: closure.startDate, endDate: closure.endDate, reason: closure.reason.trim() })
              .then(() => done('Closure added; no bookings are taken on those days.'))
              .catch((caught: unknown) => done(failureText(caught, 'Couldn’t add the closure.') ?? '', true))
          }}
        >
          <SelectField label="Facility" value={closure.facilityId} onChange={(event) => setClosure({ ...closure, facilityId: event.target.value })}>
            <option value="">Choose…</option>
            {facilities.status === 'loaded' && facilities.data.map((facility) => <option key={facility.facilityId} value={facility.facilityId}>{facility.name}</option>)}
          </SelectField>
          <TextField label="Reason" value={closure.reason} maxLength={500} onChange={(event) => setClosure({ ...closure, reason: event.target.value })} />
          <TextField label="From" type="date" value={closure.startDate} onChange={(event) => setClosure({ ...closure, startDate: event.target.value })} />
          <TextField label="To" type="date" value={closure.endDate} onChange={(event) => setClosure({ ...closure, endDate: event.target.value })} />
          <div><Button type="submit" variant="secondary">Add closure</Button></div>
        </form>
      </Card>
      <Card title="Class hours" description="Weekly times a classroom, lab, or piece of equipment is used for classes. New bookings that overlap them are refused.">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            void addClassHour(Number(slot.facilityId), { dayOfWeek: slot.dayOfWeek, startsAt: slot.startsAt, endsAt: slot.endsAt, label: slot.label.trim() })
              .then(() => done('Class hours added.'))
              .catch((caught: unknown) => done(failureText(caught, 'Couldn’t add the class hours.') ?? '', true))
          }}
        >
          <SelectField label="Facility for class hours" value={slot.facilityId} onChange={(event) => setSlot({ ...slot, facilityId: event.target.value })}>
            <option value="">Choose…</option>
            {facilities.status === 'loaded' && facilities.data.map((facility) => <option key={facility.facilityId} value={facility.facilityId}>{facility.name}</option>)}
          </SelectField>
          <SelectField label="Day" value={slot.dayOfWeek} onChange={(event) => setSlot({ ...slot, dayOfWeek: event.target.value as DayOfWeek })}>
            {DAYS.map((day) => <option key={day} value={day}>{DAY_LABEL[day]}</option>)}
          </SelectField>
          <TextField label="Class starts" type="time" value={slot.startsAt} onChange={(event) => setSlot({ ...slot, startsAt: event.target.value })} />
          <TextField label="Class ends" type="time" value={slot.endsAt} onChange={(event) => setSlot({ ...slot, endsAt: event.target.value })} />
          <TextField label="Class" hint="For example CS101 lecture." value={slot.label} maxLength={100} onChange={(event) => setSlot({ ...slot, label: event.target.value })} />
          <div className="self-end"><Button type="submit" variant="secondary">Add class hours</Button></div>
        </form>
      </Card>
      <Card title="Facilities" padding="none">
        {facilities.status !== 'loaded' ? (
          <SectionState request={facilities} what="facilities" />
        ) : facilities.data.length === 0 ? (
          <EmptyState title="No facilities yet." description="Add one above." />
        ) : (
          <ul className="divide-y divide-line text-sm">
            {facilities.data.map((facility) => (
              <li key={facility.facilityId} className="flex flex-wrap items-center gap-2 px-4 py-3 sm:px-5">
                <span className="font-semibold text-ink">{facility.name}</span>
                <span className="font-mono text-xs text-ink-muted">{facility.code}</span>
                <span className="text-ink-muted">{facility.opensAt.slice(0, 5)}–{facility.closesAt.slice(0, 5)}</span>
                {facility.needsApproval && <Badge tone="info">Needs approval</Badge>}
                {facility.closures.length > 0 && <Badge tone="warning">{facility.closures.length} closure(s)</Badge>}
                {facility.classHours.map((hour) => (
                  <span key={hour.classHourId} className="inline-flex items-center gap-1 text-ink-muted">
                    {`${DAY_LABEL[hour.dayOfWeek]} ${hour.startsAt.slice(0, 5)}–${hour.endsAt.slice(0, 5)} ${hour.label}`}
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() =>
                        void removeClassHour(hour.classHourId)
                          .then(() => done('Class hours removed.'))
                          .catch((caught: unknown) => done(failureText(caught, 'Couldn’t remove the class hours.') ?? '', true))
                      }
                    >
                      Remove<span className="sr-only">{` ${hour.label} on ${DAY_LABEL[hour.dayOfWeek]}`}</span>
                    </Button>
                  </span>
                ))}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
