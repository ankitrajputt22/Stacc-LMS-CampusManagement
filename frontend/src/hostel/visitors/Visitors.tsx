import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import {
  cancelVisitor,
  getHostelOccupancy,
  getMyVisitors,
  getTodaysVisitors,
  getVisitingHours,
  recordVisitorArrival,
  recordVisitorLeaving,
  recordWalkInVisitor,
  registerVisitor,
  setVisitingHours,
} from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import type { HostelVisit } from '../../types/hostel'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { shortTime, visitStatusLabel } from './visitText'

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

function todayText(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function VisitLine({ visit, withResident = false }: { visit: HostelVisit; withResident?: boolean }) {
  return (
    <div className="space-y-0.5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-ink">{`${visit.visitorName} (${visit.relation})`}</span>
        <Badge tone={visit.status === 'ARRIVED' ? 'success' : visit.status === 'EXPECTED' ? 'info' : 'neutral'}>{visitStatusLabel(visit.status)}</Badge>
        {visit.kind === 'WALK_IN' && <Badge tone="neutral">Walk-in</Badge>}
      </div>
      <p className="text-sm text-ink-muted">
        {withResident ? `For ${visit.residentLoginId}, ${visit.hostel} room ${visit.room} · ` : ''}
        {visit.expectedOn}
        {visit.arrivedAt && ` · in ${formatDateTime(visit.arrivedAt)}`}
        {visit.leftAt && ` · out ${formatDateTime(visit.leftAt)}`}
      </p>
    </div>
  )
}

/** A resident's visitors: register one for a day, and cancel while expected. Security records them at the gate. */
export function ResidentVisitors() {
  const queryClient = useQueryClient()
  const visitors = useApiQuery(queryKeys.hostel.myVisitors(), (signal) => getMyVisitors(signal))
  const [name, setName] = useState('')
  const [relation, setRelation] = useState('')
  const [day, setDay] = useState(todayText)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '' || relation.trim() === '') {
      setMessage({ tone: 'error', text: 'Enter the visitor’s name and how they are related to you.' })
      return
    }
    try {
      await registerVisitor(name.trim(), relation.trim(), day)
      setName('')
      setRelation('')
      setMessage({ tone: 'success', text: 'Visitor registered. Security lets them in during visiting hours.' })
      await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.myVisitors() })
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'register the visitor') })
    }
  }

  async function cancel(visit: HostelVisit) {
    try {
      await cancelVisitor(visit.visitId)
      setMessage({ tone: 'success', text: `Visit of ${visit.visitorName} cancelled.` })
      await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.myVisitors() })
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'cancel the visit') })
    }
  }

  return (
    <Card title="Visitors" description="Register visitors ahead. Only their name and relation are kept." padding="none">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      <form className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-4 sm:items-end sm:px-5" onSubmit={(event) => void submit(event)} noValidate>
        <TextField label="Visitor’s name" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
        <TextField label="Relation" hint="For example Mother." value={relation} maxLength={60} onChange={(event) => setRelation(event.target.value)} />
        <TextField label="Visit day" type="date" value={day} onChange={(event) => setDay(event.target.value)} />
        <Button type="submit">Register visitor</Button>
      </form>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {visitors.status !== 'loaded' ? (
        <SectionState request={visitors} what="visitors" />
      ) : visitors.data.length === 0 ? (
        <EmptyState title="No visitors registered." />
      ) : (
        <ul className="divide-y divide-line">
          {visitors.data.map((visit) => (
            <li key={visit.visitId} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
              <VisitLine visit={visit} />
              {visit.status === 'EXPECTED' && (
                <Button size="sm" variant="secondary" onClick={() => void cancel(visit)}>
                  Cancel visit<span className="sr-only">{` of ${visit.visitorName}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The security desk's visitors today: record arrivals (within visiting hours), departures, and walk-ins. */
export function GateVisitors() {
  const queryClient = useQueryClient()
  const visitors = useApiQuery(queryKeys.hostel.todaysVisitors(), (signal) => getTodaysVisitors(signal))
  const [resident, setResident] = useState('')
  const [name, setName] = useState('')
  const [relation, setRelation] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function act(action: () => Promise<unknown>, done: string, what: string) {
    try {
      await action()
      setMessage({ tone: 'success', text: done })
      await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.todaysVisitors() })
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, what) })
    }
  }

  return (
    <Card title="Visitors today" description="Arrivals are taken only within the hostel’s visiting hours." padding="none">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      <form
        className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-4 sm:items-end sm:px-5"
        onSubmit={(event) => {
          event.preventDefault()
          if (resident.trim() === '' || name.trim() === '' || relation.trim() === '') {
            setMessage({ tone: 'error', text: 'Enter the resident’s college ID, the visitor’s name, and the relation.' })
            return
          }
          void act(() => recordWalkInVisitor(resident.trim(), name.trim(), relation.trim()), 'Walk-in visitor recorded.', 'record the visitor').then(() => {
            setName('')
            setRelation('')
          })
        }}
        noValidate
      >
        <TextField label="Resident’s college ID" value={resident} maxLength={100} onChange={(event) => setResident(event.target.value)} />
        <TextField label="Walk-in visitor’s name" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
        <TextField label="Walk-in relation" value={relation} maxLength={60} onChange={(event) => setRelation(event.target.value)} />
        <Button type="submit">Record walk-in</Button>
      </form>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {visitors.status !== 'loaded' ? (
        <SectionState request={visitors} what="visitors" />
      ) : visitors.data.length === 0 ? (
        <EmptyState title="No visitors today." />
      ) : (
        <ul className="divide-y divide-line">
          {visitors.data.map((visit) => (
            <li key={visit.visitId} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
              <VisitLine visit={visit} withResident />
              {visit.status === 'EXPECTED' && (
                <Button size="sm" onClick={() => void act(() => recordVisitorArrival(visit.visitId), `${visit.visitorName} arrived.`, 'record the arrival')}>
                  Arrived<span className="sr-only">{`: ${visit.visitorName}`}</span>
                </Button>
              )}
              {visit.status === 'ARRIVED' && (
                <Button size="sm" variant="secondary" onClick={() => void act(() => recordVisitorLeaving(visit.visitId), `${visit.visitorName} left.`, 'record the departure')}>
                  Left<span className="sr-only">{`: ${visit.visitorName}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function HoursForm({ hostelId }: { hostelId: number }) {
  const queryClient = useQueryClient()
  const hours = useApiQuery(queryKeys.hostel.visitingHours(hostelId), (signal) => getVisitingHours(hostelId, signal))
  const [opens, setOpens] = useState<string | null>(null)
  const [closes, setCloses] = useState<string | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  if (hours.status !== 'loaded') {
    return <SectionState request={hours} what="visiting hours" />
  }
  const opensAt = opens ?? (hours.data ? shortTime(hours.data.opensAt) : '16:00')
  const closesAt = closes ?? (hours.data ? shortTime(hours.data.closesAt) : '19:00')
  return (
    <form
      className="grid gap-3 sm:grid-cols-3 sm:items-end"
      onSubmit={(event) => {
        event.preventDefault()
        void setVisitingHours(hostelId, opensAt, closesAt)
          .then(async () => {
            setMessage({ tone: 'success', text: 'Visiting hours saved.' })
            await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.visitingHours(hostelId) })
          })
          .catch((failure: unknown) => setMessage({ tone: 'error', text: refusal(failure, 'save the visiting hours') }))
      }}
    >
      <TextField label="Visitors from" type="time" value={opensAt} onChange={(event) => setOpens(event.target.value)} />
      <TextField label="Visitors until" type="time" value={closesAt} onChange={(event) => setCloses(event.target.value)} />
      <Button type="submit">Save visiting hours</Button>
      {!hours.data && <p className="text-sm text-ink-muted sm:col-span-3">No visiting hours yet: this hostel takes no visitors.</p>}
      {message && (
        <div className="sm:col-span-3">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
    </form>
  )
}

/** Hostel Management, Visitors: each hostel's daily visiting hours. */
export function VisitingHoursSection() {
  const hostels = useApiQuery(queryKeys.hostel.occupancy('admin'), (signal) => getHostelOccupancy('admin', signal))
  const [hostelId, setHostelId] = useState<number | null>(null)
  if (hostels.status !== 'loaded') {
    return (
      <Card title="Visiting hours">
        <SectionState request={hostels} what="hostels" />
      </Card>
    )
  }
  if (hostels.data.length === 0) {
    return (
      <Card title="Visiting hours">
        <EmptyState title="No hostels yet." />
      </Card>
    )
  }
  const chosen = hostelId ?? hostels.data[0].hostelId
  return (
    <Card title="Visiting hours" description="When each hostel takes visitors every day. Security records arrivals only within them.">
      <div className="space-y-4">
        <SelectField label="Hostel" value={String(chosen)} onChange={(event) => setHostelId(Number(event.target.value))}>
          {hostels.data.map((hostel) => (
            <option key={hostel.hostelId} value={hostel.hostelId}>
              {hostel.name}
            </option>
          ))}
        </SelectField>
        <HoursForm key={chosen} hostelId={chosen} />
      </div>
    </Card>
  )
}
