import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getSemesters } from '../api/adminAcademicApi'
import { getCourseOfferings } from '../api/adminEnrollmentApi'
import { addExam, createExamSession, generateSeatPlan, getExamSessions, publishExamSession, removeExam } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import type { ExamSession, ExamSlot } from '../types/erp'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextField } from '../ui'
import { hhmm } from './examFormat'
import { formatDay, refusalOf } from './requestWords'

/** The seats of one hall as a grid: given seats filled, free ones outlined. */
function HallGrid({ exam }: { exam: ExamSlot }) {
  const shown = Math.min(exam.hallSeats, 120)
  return (
    <div>
      <p className="text-xl font-bold text-ink">{`Hall ${exam.hall}`}</p>
      <p className="text-ink-muted">{`Capacity: ${exam.hallSeats} seats · Allocated: ${exam.seatsGiven} seats`}</p>
      <ol aria-label={`Seats of hall ${exam.hall} for ${exam.courseCode}`} className="mt-3 grid grid-cols-6 gap-1.5 sm:grid-cols-10">
        {Array.from({ length: shown }, (_, index) => {
          const given = index < exam.seatsGiven
          return (
            <li key={index} className={`flex h-8 items-center justify-center rounded text-xs font-medium ${given ? 'bg-portal-bar-strong text-portal-navy' : 'border border-portal-cta text-portal-cta'}`}>
              {index + 1}
              <span className="sr-only">{given ? ', allocated' : ', free'}</span>
            </li>
          )
        })}
      </ol>
      <p className="mt-2 text-sm text-ink-muted">{`Unallocated: ${exam.hallSeats - exam.seatsGiven} seats · ${exam.studentsEnrolled} students enrolled`}</p>
    </div>
  )
}

function SessionPanel({ session, semesterId }: { session: ExamSession; semesterId: number }) {
  const queryClient = useQueryClient()
  const offerings = useApiQuery(queryKeys.admin.offerings({ semesterId }), (signal) => getCourseOfferings({ semesterId }, signal))
  const [form, setForm] = useState({ courseOfferingId: '', examDate: '', startsAt: '10:00', endsAt: '13:00', hall: 'B-12', hallSeats: '60' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number | null>(session.exams[0]?.slotId ?? null)
  const chosen = session.exams.find((exam) => exam.slotId === selected) ?? null

  async function act(send: () => Promise<unknown>) {
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await send()
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.examSessions(semesterId) })
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void act(() => addExam(session.examSessionId, {
      courseOfferingId: Number(form.courseOfferingId), examDate: form.examDate, startsAt: form.startsAt, endsAt: form.endsAt,
      hall: form.hall, hallSeats: Number(form.hallSeats),
    }))
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
      <Card
        title={session.name}
        icon="calendar"
        action={
          <span className="flex items-center gap-3">
            <Badge tone={session.status === 'PUBLISHED' ? 'success' : 'warning'}>{session.status === 'PUBLISHED' ? 'Published' : 'Draft'}</Badge>
            {session.status === 'DRAFT' && (
              <Button size="sm" disabled={busy || session.exams.length === 0}
                onClick={() => window.confirm('Publish this exam session? Students then see their exams and seats.') && void act(() => publishExamSession(session.examSessionId))}>
                Publish
              </Button>
            )}
          </span>
        }
      >
        {session.exams.length === 0 ? (
          <p className="text-[0.95rem] text-ink-muted">No exams yet. Add the first one below.</p>
        ) : (
          <div className="overflow-x-auto rounded-panel border border-line">
            <table className="w-full min-w-[40rem] text-left text-sm">
              <caption className="sr-only">{`Exams of ${session.name}`}</caption>
              <thead className="bg-surface-muted">
                <tr>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Date</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Course</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Time</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Hall</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Seats</th>
                  <th scope="col" className="px-3 py-2.5"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {session.exams.map((exam) => (
                  <tr key={exam.slotId} className={`border-t border-line ${exam.slotId === selected ? 'bg-portal-bar-strong/30' : ''}`}>
                    <td className="px-3 py-2.5 whitespace-nowrap">{formatDay(exam.examDate)}</td>
                    <th scope="row" className="px-3 py-2.5 font-normal">{`${exam.courseCode} · ${exam.courseName}${exam.sectionCode ? ` (${exam.sectionCode})` : ''}`}</th>
                    <td className="px-3 py-2.5 whitespace-nowrap">{`${hhmm(exam.startsAt)} – ${hhmm(exam.endsAt)}`}</td>
                    <td className="px-3 py-2.5">{exam.hall}</td>
                    <td className="px-3 py-2.5">{`${exam.seatsGiven} / ${exam.studentsEnrolled}`}</td>
                    <td className="px-3 py-2.5 whitespace-nowrap">
                      <Button variant="ghost" size="sm" onClick={() => setSelected(exam.slotId)}>Seats<span className="sr-only"> of {exam.courseCode}</span></Button>
                      <Button variant="ghost" size="sm" disabled={busy}
                        onClick={() => window.confirm(`Remove the ${exam.courseCode} exam? It is kept as history.`) && void act(() => removeExam(exam.slotId))}>
                        Remove<span className="sr-only"> {exam.courseCode}</span>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <form onSubmit={add} className="mt-5 grid gap-3 border-t border-line pt-5 sm:grid-cols-3">
          <SelectField label="Course offering" required value={form.courseOfferingId} onChange={(event) => setForm({ ...form, courseOfferingId: event.target.value })} className="sm:col-span-3">
            <option value="">Choose an offering</option>
            {offerings.status === 'loaded' && offerings.data.map((offering) => (
              <option key={offering.courseOfferingId} value={offering.courseOfferingId}>{`${offering.courseCode} · ${offering.courseName}`}</option>
            ))}
          </SelectField>
          <TextField label="Date" type="date" required value={form.examDate} onChange={(event) => setForm({ ...form, examDate: event.target.value })} />
          <TextField label="Starts" type="time" required value={form.startsAt} onChange={(event) => setForm({ ...form, startsAt: event.target.value })} />
          <TextField label="Ends" type="time" required value={form.endsAt} onChange={(event) => setForm({ ...form, endsAt: event.target.value })} />
          <TextField label="Hall" required maxLength={40} value={form.hall} onChange={(event) => setForm({ ...form, hall: event.target.value })} />
          <TextField label="Seats in the hall" type="number" min={1} max={1000} required value={form.hallSeats} onChange={(event) => setForm({ ...form, hallSeats: event.target.value })} />
          <div className="flex items-end"><Button type="submit" disabled={busy}>Add exam</Button></div>
        </form>
        {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      </Card>
      <Card title="Hall allocation" icon="clubs">
        {chosen ? (
          <>
            <HallGrid exam={chosen} />
            <Button fullWidth className="mt-4" disabled={busy} onClick={() => void act(() => generateSeatPlan(chosen.slotId))}>Generate seat plan</Button>
          </>
        ) : (
          <p className="text-[0.95rem] text-ink-muted">Choose Seats on an exam to see its hall.</p>
        )}
      </Card>
    </div>
  )
}

/** Exam management: a semester's exam sessions, their exams and halls, seat plans, and publishing. */
export function ErpExamAdminPage() {
  const queryClient = useQueryClient()
  const semesters = useApiQuery(queryKeys.admin.semesters(), getSemesters)
  const [semesterId, setSemesterId] = useState<number | null>(null)
  const [name, setName] = useState('End Semester Examinations')
  const [error, setError] = useState<string | null>(null)
  const sessions = useApiQuery(semesterId ? queryKeys.erp.examSessions(semesterId) : null, (signal) => getExamSessions(semesterId ?? 0, signal))
  const open = semesters.status === 'loaded' ? semesters.data.filter((semester) => semester.status !== 'CLOSED') : []

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!semesterId) {
      return
    }
    setError(null)
    try {
      await createExamSession(semesterId, name.trim())
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.examSessions(semesterId) })
    } catch (failure) {
      setError(refusalOf(failure))
    }
  }

  return (
    <>
      <title>Exam management · Stacc</title>
      <PageHeader title="Exam management" description="Create and manage examination sessions, schedules and hall allocation." />
      <Card title="Exam session" icon="academics">
        {semesters.status !== 'loaded' ? (
          <SectionState request={semesters} what="semesters" />
        ) : (
          <form onSubmit={create} className="grid gap-4 sm:grid-cols-[1.2fr_1.2fr_auto] sm:items-end">
            <SelectField label="Semester" value={semesterId ?? ''} onChange={(event) => setSemesterId(event.target.value ? Number(event.target.value) : null)}>
              <option value="">Choose a semester</option>
              {open.map((semester) => (
                <option key={semester.id} value={semester.id}>{`${semester.programCode} · Semester ${semester.semesterNumber} · ${semester.academicSessionCode}`}</option>
              ))}
            </SelectField>
            <TextField label="New session name" maxLength={150} value={name} onChange={(event) => setName(event.target.value)} />
            <Button type="submit" disabled={!semesterId || name.trim() === ''}>Create session</Button>
          </form>
        )}
        {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      </Card>
      {semesterId && (
        <div className="mt-6 space-y-6">
          {sessions.status !== 'loaded' ? (
            <SectionState request={sessions} what="exam sessions" />
          ) : sessions.data.length === 0 ? (
            <p className="text-[0.95rem] text-ink-muted">This semester has no exam session yet.</p>
          ) : (
            sessions.data.map((session) => <SessionPanel key={session.examSessionId} session={session} semesterId={semesterId} />)
          )}
        </div>
      )}
    </>
  )
}
