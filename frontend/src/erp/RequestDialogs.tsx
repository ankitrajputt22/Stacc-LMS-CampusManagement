import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { requestCorrection, requestMedicalLeave } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import type { StudentCourseAttendance } from '../types/attendance'
import { Alert, Button, Dialog, SelectField, TextAreaField, TextField } from '../ui'
import { formatMomentDay, refusalOf } from './requestWords'

function useSubmit(onDone: () => void) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function run(send: () => Promise<unknown>) {
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await send()
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.myRequests() })
      onDone()
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }
  return { busy, error, run }
}

function Actions({ busy, onCancel }: { busy: boolean; onCancel: () => void }) {
  return (
    <div className="mt-6 flex justify-end gap-3">
      <Button variant="secondary" onClick={onCancel}>Cancel</Button>
      <Button type="submit" disabled={busy}>{busy ? 'Submitting…' : 'Submit request'}</Button>
    </div>
  )
}

export function MedicalLeaveDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [reason, setReason] = useState('')
  const { busy, error, run } = useSubmit(onClose)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void run(() => requestMedicalLeave({ fromDate: from, toDate: to, reason: reason.trim() }))
  }

  return (
    <Dialog title="Request medical leave" open={open} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <TextField label="From date" type="date" required value={from} onChange={(event) => setFrom(event.target.value)} />
        <TextField label="To date" type="date" required min={from || undefined} value={to} onChange={(event) => setTo(event.target.value)} />
        <TextAreaField label="Reason" required maxLength={1000} rows={3} value={reason} onChange={(event) => setReason(event.target.value)}
          hint="Keep your medical certificate; the office may ask to see it." />
        {error && <Alert tone="error">{error}</Alert>}
        <Actions busy={busy} onCancel={onClose} />
      </form>
    </Dialog>
  )
}

export function CorrectionDialog({ open, onClose, courses, recordId }: {
  open: boolean
  onClose: () => void
  courses: readonly StudentCourseAttendance[]
  recordId?: number
}) {
  const absences = courses.flatMap((course) =>
    course.sessions.filter((session) => session.status === 'ABSENT').map((session) => ({ course, session })),
  )
  const [chosen, setChosen] = useState(recordId ? String(recordId) : '')
  const [reason, setReason] = useState('')
  const { busy, error, run } = useSubmit(onClose)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void run(() => requestCorrection({ attendanceRecordId: Number(chosen), reason: reason.trim() }))
  }

  return (
    <Dialog title="Request a correction" open={open} onClose={onClose}>
      {absences.length === 0 ? (
        <p className="text-[0.95rem] text-ink-muted">You have no classes marked absent, so there is nothing to correct.</p>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <SelectField label="Class marked absent" required value={chosen} onChange={(event) => setChosen(event.target.value)}>
            <option value="">Choose a class</option>
            {absences.map(({ course, session }) => (
              <option key={session.attendanceRecordId} value={session.attendanceRecordId}>
                {`${course.courseCode} · ${formatMomentDay(session.heldAt)}${session.topic ? ` · ${session.topic}` : ''}`}
              </option>
            ))}
          </SelectField>
          <TextAreaField label="Reason" required maxLength={1000} rows={3} value={reason} onChange={(event) => setReason(event.target.value)}
            hint="For example: I was in class but was marked absent." />
          {error && <Alert tone="error">{error}</Alert>}
          <Actions busy={busy} onCancel={onClose} />
        </form>
      )}
    </Dialog>
  )
}
