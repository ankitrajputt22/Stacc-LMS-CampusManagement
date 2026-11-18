import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getAcademicSessions, getPrograms } from '../api/adminAcademicApi'
import { decideFeeRequest, getFeeRequests, getFeeStructure, getStudentLedger, recordPayment, setFeeStructure } from '../api/feesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import type { AdjustmentStatus, FeeHead, FeeLedger, FeeStructure, PaymentMode } from '../types/fees'
import { Alert, Badge, Button, Card, Dialog, PageHeader, SelectField, TextField } from '../ui'
import { HEAD_WORDS, MODE_WORDS, rupees } from './money'
import { formatDay, refusalOf } from './requestWords'

const HEADS = Object.keys(HEAD_WORDS) as FeeHead[]

function StructureEditor({ structure, onSaved }: { structure: FeeStructure; onSaved: () => void }) {
  const [dueDate, setDueDate] = useState(structure.dueDate ?? '')
  const [amounts, setAmounts] = useState<Record<FeeHead, string>>(() =>
    Object.fromEntries(HEADS.map((head) => [head, String(structure.items.find((item) => item.head === head)?.amount ?? '')])) as Record<FeeHead, string>,
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const total = HEADS.reduce((sum, head) => sum + (Number(amounts[head]) || 0), 0)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await setFeeStructure(structure.programId, structure.academicSessionId, {
        dueDate,
        items: HEADS.filter((head) => amounts[head].trim() !== '').map((head) => ({ head, amount: Number(amounts[head]) })),
      })
      onSaved()
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={save}>
      <table className="w-full text-left text-[0.95rem]">
        <caption className="sr-only">Fee heads</caption>
        <thead className="bg-surface-muted"><tr><th scope="col" className="px-3 py-2">Fee head</th><th scope="col" className="px-3 py-2 text-right">Amount (₹)</th></tr></thead>
        <tbody>
          {HEADS.map((head) => (
            <tr key={head} className="border-t border-line">
              <th scope="row" className="px-3 py-1.5 font-normal">{HEAD_WORDS[head]}</th>
              <td className="px-3 py-1.5">
                <TextField label={`${HEAD_WORDS[head]} amount`} className="ml-auto w-36 [&>label]:sr-only" type="number" min={0} step="0.01"
                  value={amounts[head]} onChange={(event) => setAmounts({ ...amounts, [head]: event.target.value })} />
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot><tr className="border-t border-line bg-orange-50 text-lg font-bold"><th scope="row" className="px-3 py-2.5">Total</th><td className="px-3 py-2.5 text-right">{rupees(total)}</td></tr></tfoot>
      </table>
      <div className="mt-4 flex flex-wrap items-end gap-3">
        <TextField label="Due date" type="date" required value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
        <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save fee structure'}</Button>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
    </form>
  )
}

function Structures() {
  const queryClient = useQueryClient()
  const programs = useApiQuery(queryKeys.admin.programs(), getPrograms)
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const [programId, setProgramId] = useState<number | null>(null)
  const [sessionId, setSessionId] = useState<number | null>(null)
  const structure = useApiQuery(
    programId && sessionId ? queryKeys.erp.feeStructure(programId, sessionId) : null,
    (signal) => getFeeStructure(programId ?? 0, sessionId ?? 0, signal),
  )
  return (
    <Card title="Fee structure" icon="fees">
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <SelectField label="Program" value={programId ?? ''} onChange={(event) => setProgramId(event.target.value ? Number(event.target.value) : null)}>
          <option value="">Choose a program</option>
          {programs.status === 'loaded' && programs.data.map((program) => <option key={program.id} value={program.id}>{`${program.code} · ${program.name}`}</option>)}
        </SelectField>
        <SelectField label="Academic session" value={sessionId ?? ''} onChange={(event) => setSessionId(event.target.value ? Number(event.target.value) : null)}>
          <option value="">Choose a session</option>
          {sessions.status === 'loaded' && sessions.data.map((session) => <option key={session.id} value={session.id}>{session.code}</option>)}
        </SelectField>
      </div>
      {programId && sessionId && (structure.status !== 'loaded' ? (
        <SectionState request={structure} what="the fee structure" />
      ) : (
        <StructureEditor key={`${programId}-${sessionId}-${structure.data.total}`} structure={structure.data}
          onSaved={() => void queryClient.invalidateQueries({ queryKey: queryKeys.erp.feeStructure(programId, sessionId) })} />
      ))}
    </Card>
  )
}

function RecordPaymentDialog({ loginId, open, onClose }: { loginId: string; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState<PaymentMode>('BANK_TRANSFER')
  const [reference, setReference] = useState('')
  const [paidOn, setPaidOn] = useState(() => new Date().toISOString().slice(0, 10))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await recordPayment(loginId, { amount: Number(amount), mode, reference: reference.trim() || null, paidOn })
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.ledger(loginId) })
      onClose()
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog title="Record payment" open={open} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Alert tone="warning">This payment has not been recorded yet.</Alert>
        <TextField label="Amount (₹)" type="number" min={1} step="0.01" required value={amount} onChange={(event) => setAmount(event.target.value)} />
        <SelectField label="Mode" value={mode} onChange={(event) => setMode(event.target.value as PaymentMode)}>
          {(Object.keys(MODE_WORDS) as PaymentMode[]).map((value) => <option key={value} value={value}>{MODE_WORDS[value]}</option>)}
        </SelectField>
        <TextField label="Reference" maxLength={60} value={reference} onChange={(event) => setReference(event.target.value)} />
        <TextField label="Payment date" type="date" required value={paidOn} onChange={(event) => setPaidOn(event.target.value)} />
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Recording…' : 'Record payment'}</Button>
        </div>
      </form>
    </Dialog>
  )
}

function LedgerView({ ledger }: { ledger: FeeLedger }) {
  const [recording, setRecording] = useState(false)
  return (
    <>
      <div className="rounded-panel border border-line border-l-4 border-l-teal-700 px-4 py-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xl font-bold text-ink">{ledger.studentLoginId}</p>
            <p className="text-ink-muted">{`${ledger.programCode} · ${ledger.academicSessionCode}${ledger.rollNumber ? ` · Roll No: ${ledger.rollNumber}` : ''}`}</p>
          </div>
          <Badge tone={ledger.due > 0 ? (ledger.overdue ? 'danger' : 'warning') : 'success'}>
            {ledger.due > 0 ? `Due: ${rupees(ledger.due)}` : ledger.due < 0 ? `Credit: ${rupees(-ledger.due)}` : 'Paid in full'}
          </Badge>
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div><dt className="text-sm text-ink-muted">Total fees</dt><dd className="font-semibold">{rupees(ledger.total)}</dd></div>
          <div><dt className="text-sm text-ink-muted">Paid</dt><dd className="font-semibold">{rupees(ledger.paid)}</dd></div>
          <div><dt className="text-sm text-ink-muted">Scholarship</dt><dd className="font-semibold">{rupees(ledger.scholarships)}</dd></div>
          <div><dt className="text-sm text-ink-muted">Due</dt><dd className="font-semibold text-portal-cta">{rupees(Math.max(0, ledger.due))}</dd></div>
        </dl>
      </div>
      <div className="mt-4 flex items-center justify-between gap-3">
        <h3 className="text-lg font-semibold">Payment records</h3>
        <Button size="sm" onClick={() => setRecording(true)}>Record payment</Button>
      </div>
      {ledger.payments.length === 0 ? (
        <p className="mt-2 text-[0.95rem] text-ink-muted">No payments recorded yet.</p>
      ) : (
        <div className="mt-2 overflow-x-auto rounded-panel border border-line">
          <table className="w-full min-w-[34rem] text-left text-sm">
            <caption className="sr-only">Payments of this student</caption>
            <thead className="bg-surface-muted"><tr><th scope="col" className="px-3 py-2">Date</th><th scope="col" className="px-3 py-2">Amount</th><th scope="col" className="px-3 py-2">Mode</th><th scope="col" className="px-3 py-2">Receipt</th><th scope="col" className="px-3 py-2">Reference</th></tr></thead>
            <tbody>
              {ledger.payments.map((payment) => (
                <tr key={payment.paymentId} className="border-t border-line">
                  <td className="px-3 py-2 whitespace-nowrap">{formatDay(payment.paidOn)}</td>
                  <td className="px-3 py-2">{rupees(payment.amount)}</td>
                  <td className="px-3 py-2">{MODE_WORDS[payment.mode]}</td>
                  <td className="px-3 py-2">{payment.receiptNumber}</td>
                  <td className="px-3 py-2">{payment.reference ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {recording && <RecordPaymentDialog loginId={ledger.studentLoginId} open onClose={() => setRecording(false)} />}
    </>
  )
}

function StudentLedger() {
  const [search, setSearch] = useState('')
  const [loginId, setLoginId] = useState('')
  const ledger = useApiQuery(loginId ? queryKeys.erp.ledger(loginId) : null, (signal) => getStudentLedger(loginId, signal))
  return (
    <Card title="Student ledger" icon="profile">
      <form onSubmit={(event) => { event.preventDefault(); setLoginId(search.trim()) }} className="mb-4 flex items-end gap-3">
        <TextField label="College ID" className="flex-1" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="For example: cse24001" />
        <Button type="submit" variant="secondary">Find</Button>
      </form>
      {loginId && (ledger.status === 'loaded' ? <LedgerView ledger={ledger.data} />
        : ledger.status === 'notFound' ? <p className="text-[0.95rem] text-ink-muted">No student with that college ID.</p>
        : <SectionState request={ledger} what="the ledger" />)}
    </Card>
  )
}

function FeeRequests() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AdjustmentStatus | ''>('SUBMITTED')
  const requests = useApiQuery(queryKeys.erp.feeRequests(status), (signal) => getFeeRequests(status, signal))
  const [comments, setComments] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)

  async function decide(adjustmentId: number, approve: boolean) {
    setError(null)
    try {
      await decideFeeRequest(adjustmentId, approve, comments[adjustmentId]?.trim() || null)
      await queryClient.invalidateQueries({ queryKey: ['erp', 'fees'] })
    } catch (failure) {
      setError(refusalOf(failure))
    }
  }

  return (
    <Card title="Scholarships and refunds" icon="career"
      action={
        <SelectField label="Status" className="w-44" value={status} onChange={(event) => setStatus(event.target.value as AdjustmentStatus | '')}>
          <option value="SUBMITTED">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option><option value="">All</option>
        </SelectField>
      }>
      {error && <Alert tone="error" className="mb-3">{error}</Alert>}
      {requests.status !== 'loaded' ? <SectionState request={requests} what="requests" />
        : requests.data.length === 0 ? <p className="text-[0.95rem] text-ink-muted">No requests.</p> : (
          <ul className="space-y-3">
            {requests.data.map((entry) => (
              <li key={entry.adjustmentId} className="rounded-panel border border-line px-4 py-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{`${entry.kind === 'SCHOLARSHIP' ? 'Scholarship' : 'Refund'}: ${entry.title} · ${rupees(entry.amount)}`}</p>
                    <p className="text-sm text-ink-muted">{`${entry.studentLoginId} · ${entry.academicSessionCode}`}</p>
                    <p className="mt-1 text-sm">{entry.reason}</p>
                  </div>
                  <Badge tone={entry.status === 'APPROVED' ? 'success' : entry.status === 'REJECTED' ? 'danger' : 'warning'}>
                    {entry.status === 'SUBMITTED' ? 'Pending' : entry.status === 'APPROVED' ? 'Approved' : 'Rejected'}
                  </Badge>
                </div>
                {entry.status === 'SUBMITTED' && (
                  <div className="mt-3 flex flex-wrap items-end gap-3">
                    <TextField label="Comment" className="min-w-56 flex-1" maxLength={1000} value={comments[entry.adjustmentId] ?? ''}
                      onChange={(event) => setComments({ ...comments, [entry.adjustmentId]: event.target.value })} hint="Required to reject." />
                    <Button size="sm" onClick={() => void decide(entry.adjustmentId, true)}>Approve</Button>
                    <Button size="sm" variant="secondary" disabled={!(comments[entry.adjustmentId] ?? '').trim()} onClick={() => void decide(entry.adjustmentId, false)}>Reject</Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
    </Card>
  )
}

export function ErpFeesAdminPage() {
  return (
    <>
      <title>Fees management · Stacc</title>
      <PageHeader title="Fees management" description="Manage fee structure, student dues and payment records." />
      <div className="grid gap-6 xl:grid-cols-2">
        <Structures />
        <StudentLedger />
      </div>
      <div className="mt-6"><FeeRequests /></div>
    </>
  )
}
