import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { applyForScholarship, askForRefund, getMyFees } from '../api/feesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import { PortalEmblem } from '../login/PortalEmblem'
import type { AdjustmentKind, FeeLedger, FeePayment } from '../types/fees'
import { Alert, Badge, Button, Card, LinkTabs, PageHeader, TextAreaField, TextField } from '../ui'
import { HEAD_WORDS, MODE_WORDS, rupees } from './money'
import { formatDay, refusalOf } from './requestWords'

export type FeesTab = 'overview' | 'structure' | 'payments' | 'scholarships' | 'refunds'

const STATUS = { SUBMITTED: ['Pending', 'warning'], APPROVED: ['Approved', 'success'], REJECTED: ['Rejected', 'danger'] } as const

function Summary({ icon, label, amount, strong }: { icon: NavIconName; label: string; amount: number; strong?: boolean }) {
  return (
    <div className="flex items-center gap-4 rounded-panel border border-line bg-surface px-5 py-5">
      <NavIcon name={icon} className={`size-9 ${strong ? 'text-portal-cta' : 'text-ink'}`} />
      <div>
        <p className="text-lg text-ink">{label}</p>
        <p className={`text-3xl font-bold ${strong ? 'text-portal-cta' : 'text-ink'}`}>{rupees(amount)}</p>
      </div>
    </div>
  )
}

function Overview({ ledger }: { ledger: FeeLedger }) {
  const scholarships = ledger.adjustments.filter((entry) => entry.kind === 'SCHOLARSHIP' && entry.status === 'APPROVED')
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Summary icon="academics" label="Total fees" amount={ledger.total} />
        <Summary icon="assignments" label="Paid" amount={ledger.paid} />
        <Summary icon="career" label="Scholarship" amount={ledger.scholarships} />
        <Summary icon="fees" label={ledger.due >= 0 ? 'Due' : 'Credit'} amount={Math.abs(ledger.due)} strong={ledger.due > 0} />
      </div>
      {ledger.dueDate && ledger.due > 0 && (
        <div role="status" className={`flex items-center gap-4 rounded-panel border px-5 py-3.5 ${ledger.overdue ? 'border-red-300 bg-red-50' : 'border-amber-300 bg-amber-50'}`}>
          <NavIcon name="calendar" className="size-7 text-portal-cta" />
          <span aria-hidden="true" className="h-8 w-px bg-portal-cta/40" />
          <p className="text-lg font-semibold text-ink">{`${ledger.overdue ? 'Overdue since' : 'Next due:'} ${formatDay(ledger.dueDate)}`}</p>
        </div>
      )}
      <Card title="Instalment details">
        <div className="overflow-x-auto rounded-panel border border-line">
          <table className="w-full min-w-[32rem] text-left text-[0.95rem]">
            <caption className="sr-only">How the fees are being paid</caption>
            <thead className="bg-surface-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-semibold">Instalment</th>
                <th scope="col" className="px-4 py-3 font-semibold">Amount</th>
                <th scope="col" className="px-4 py-3 font-semibold">Status</th>
              </tr>
            </thead>
            <tbody>
              {ledger.payments.map((payment) => (
                <tr key={payment.paymentId} className="border-t border-line">
                  <th scope="row" className="px-4 py-2.5 font-normal">{`Payment of ${formatDay(payment.paidOn)}`}</th>
                  <td className="px-4 py-2.5">{rupees(payment.amount)}</td>
                  <td className="px-4 py-2.5"><Badge tone="success">Paid</Badge></td>
                </tr>
              ))}
              {scholarships.map((entry) => (
                <tr key={entry.adjustmentId} className="border-t border-line">
                  <th scope="row" className="px-4 py-2.5 font-normal">{entry.title}</th>
                  <td className="px-4 py-2.5">{rupees(entry.amount)}</td>
                  <td className="px-4 py-2.5"><Badge tone="info">Applied</Badge></td>
                </tr>
              ))}
              {ledger.due > 0 && (
                <tr className="border-t border-line">
                  <th scope="row" className="px-4 py-2.5 font-normal">Remaining fee</th>
                  <td className="px-4 py-2.5">{rupees(ledger.due)}</td>
                  <td className="px-4 py-2.5">
                    <Badge tone={ledger.overdue ? 'danger' : 'warning'}>
                      {ledger.dueDate ? `${ledger.overdue ? 'Overdue' : 'Due'} – ${formatDay(ledger.dueDate)}` : 'Due'}
                    </Badge>
                  </td>
                </tr>
              )}
            </tbody>
            <tfoot>
              <tr className="border-t border-line bg-surface-muted text-lg font-bold">
                <th scope="row" className="px-4 py-3">Total</th>
                <td className="px-4 py-3" colSpan={2}>{rupees(ledger.total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </Card>
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="flex items-center gap-3 text-ink-muted"><NavIcon name="info" className="size-6" />Payments are recorded by the accounts office.</p>
          <Link to="/erp/fees/payments" className="flex items-center gap-2 font-medium text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
            <NavIcon name="documents" />View payment history
          </Link>
        </div>
      </Card>
    </div>
  )
}

function Structure({ ledger }: { ledger: FeeLedger }) {
  return (
    <Card title={`Fee structure ${ledger.academicSessionCode}`} description={`${ledger.programName} (${ledger.programCode})`}>
      {ledger.items.length === 0 ? (
        <p className="text-[0.95rem] text-ink-muted">The fee structure for this session has not been published yet.</p>
      ) : (
        <table className="w-full max-w-xl text-left text-[0.95rem]">
          <caption className="sr-only">Fee heads and amounts</caption>
          <thead className="bg-surface-muted"><tr><th scope="col" className="px-4 py-2.5">Fee head</th><th scope="col" className="px-4 py-2.5 text-right">Amount</th></tr></thead>
          <tbody>
            {ledger.items.map((item) => (
              <tr key={item.head} className="border-t border-line"><th scope="row" className="px-4 py-2.5 font-normal">{HEAD_WORDS[item.head]}</th><td className="px-4 py-2.5 text-right">{rupees(item.amount)}</td></tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-line bg-orange-50 text-lg font-bold"><th scope="row" className="px-4 py-3">Total</th><td className="px-4 py-3 text-right">{rupees(ledger.total)}</td></tr>
          </tfoot>
        </table>
      )}
      {ledger.dueDate && <p className="mt-4 text-ink-muted">{`Due by ${formatDay(ledger.dueDate)}.`}</p>}
    </Card>
  )
}

function Receipt({ ledger, payment }: { ledger: FeeLedger; payment: FeePayment }) {
  return (
    <article aria-label={`Receipt ${payment.receiptNumber}`} className="print-area rounded-panel border border-line bg-surface p-6">
      <header className="flex items-center gap-3 border-b-2 border-portal-navy pb-3">
        <PortalEmblem className="size-12" />
        <div><p className="text-xl font-extrabold text-portal-navy">STACC</p><p className="text-sm font-semibold">Fee receipt</p></div>
      </header>
      <dl className="mt-4 grid grid-cols-[9rem_1fr] gap-y-1 text-sm">
        <dt className="text-ink-muted">Receipt number</dt><dd className="font-semibold">{payment.receiptNumber}</dd>
        <dt className="text-ink-muted">College ID</dt><dd>{ledger.studentLoginId}</dd>
        <dt className="text-ink-muted">Programme</dt><dd>{ledger.programCode}</dd>
        <dt className="text-ink-muted">Session</dt><dd>{ledger.academicSessionCode}</dd>
        <dt className="text-ink-muted">Paid on</dt><dd>{formatDay(payment.paidOn)}</dd>
        <dt className="text-ink-muted">Mode</dt><dd>{MODE_WORDS[payment.mode]}</dd>
        {payment.reference && <><dt className="text-ink-muted">Reference</dt><dd>{payment.reference}</dd></>}
        <dt className="text-ink-muted">Amount</dt><dd className="text-lg font-bold">{rupees(payment.amount)}</dd>
      </dl>
      <p className="mt-6 text-right text-sm">Accounts Office</p>
    </article>
  )
}

function Payments({ ledger }: { ledger: FeeLedger }) {
  const [shown, setShown] = useState<number | null>(ledger.payments.at(-1)?.paymentId ?? null)
  const chosen = ledger.payments.find((payment) => payment.paymentId === shown)
  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <Card title="Payment history" icon="fees">
        {ledger.payments.length === 0 ? (
          <p className="text-[0.95rem] text-ink-muted">No payments have been recorded yet.</p>
        ) : (
          <div className="overflow-x-auto rounded-panel border border-line">
            <table className="w-full min-w-[34rem] text-left text-[0.95rem]">
              <caption className="sr-only">Payments</caption>
              <thead className="bg-surface-muted">
                <tr>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Date</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Receipt</th>
                  <th scope="col" className="px-3 py-2.5 font-semibold">Mode</th>
                  <th scope="col" className="px-3 py-2.5 text-right font-semibold">Amount</th>
                  <th scope="col" className="px-3 py-2.5"><span className="sr-only">Receipt</span></th>
                </tr>
              </thead>
              <tbody>
                {ledger.payments.map((payment) => (
                  <tr key={payment.paymentId} className="border-t border-line">
                    <td className="px-3 py-2.5 whitespace-nowrap">{formatDay(payment.paidOn)}</td>
                    <th scope="row" className="px-3 py-2.5 font-normal">{payment.receiptNumber}</th>
                    <td className="px-3 py-2.5">{MODE_WORDS[payment.mode]}</td>
                    <td className="px-3 py-2.5 text-right">{rupees(payment.amount)}</td>
                    <td className="px-3 py-2.5 text-right">
                      <Button variant="ghost" size="sm" onClick={() => setShown(payment.paymentId)}>View receipt<span className="sr-only"> {payment.receiptNumber}</span></Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {chosen && (
        <div>
          <div className="mb-3 flex justify-end"><Button variant="secondary" onClick={() => window.print()}>Print receipt</Button></div>
          <Receipt ledger={ledger} payment={chosen} />
        </div>
      )}
    </div>
  )
}

function Requests({ ledger, kind }: { ledger: FeeLedger; kind: AdjustmentKind }) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const mine = ledger.adjustments.filter((entry) => entry.kind === kind)
  const scholarship = kind === 'SCHOLARSHIP'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await (scholarship
        ? applyForScholarship({ title: title.trim(), amount: Number(amount), reason: reason.trim() })
        : askForRefund({ amount: Number(amount), reason: reason.trim() }))
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.myFees() })
      setTitle('')
      setAmount('')
      setReason('')
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card title={scholarship ? 'Apply for a scholarship' : 'Ask for a refund'} icon={scholarship ? 'career' : 'fees'}>
        {!scholarship && ledger.credit <= 0 ? (
          <p className="text-[0.95rem] text-ink-muted">A refund is possible only when you have paid more than your fees. You have no credit now.</p>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            {scholarship && <TextField label="Scholarship or waiver" required maxLength={150} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="For example: Merit Scholarship" />}
            <TextField label="Amount (₹)" type="number" min={1} step="0.01" required value={amount} onChange={(event) => setAmount(event.target.value)}
              hint={scholarship ? undefined : `At most ${rupees(ledger.credit)}, what you paid beyond your fees.`} />
            <TextAreaField label="Reason" required maxLength={1000} rows={3} value={reason} onChange={(event) => setReason(event.target.value)} />
            <Button type="submit" disabled={busy}>{busy ? 'Sending…' : scholarship ? 'Apply' : 'Ask for refund'}</Button>
            {error && <Alert tone="error">{error}</Alert>}
          </form>
        )}
      </Card>
      <Card title={scholarship ? 'Your scholarships' : 'Your refund requests'} icon="documents">
        {mine.length === 0 ? (
          <p className="text-[0.95rem] text-ink-muted">Nothing yet.</p>
        ) : (
          <ul className="space-y-3">
            {mine.map((entry) => (
              <li key={entry.adjustmentId} className="rounded-panel border border-line px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <p><span className="font-semibold">{entry.title}</span> · {rupees(entry.amount)}</p>
                  <Badge tone={STATUS[entry.status][1]}>{STATUS[entry.status][0]}</Badge>
                </div>
                {entry.decisionComment && <p className="mt-1 text-sm text-ink-muted">{entry.decisionComment}</p>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

export function ErpFeesPage({ tab = 'overview' }: { tab?: FeesTab }) {
  const fees = useApiQuery(queryKeys.erp.myFees(), getMyFees)
  return (
    <>
      <title>Fees · Stacc</title>
      <PageHeader title="Fees" description="Fee details, payments, scholarships and refunds." />
      <LinkTabs
        label="Fees sections"
        tabs={[
          { to: '/erp/fees', label: 'Overview', end: true },
          { to: '/erp/fees/structure', label: 'Fee structure' },
          { to: '/erp/fees/payments', label: 'Payments' },
          { to: '/erp/fees/scholarships', label: 'Scholarships' },
          { to: '/erp/fees/refunds', label: 'Refunds' },
        ]}
      />
      {fees.status !== 'loaded' ? (
        <SectionState request={fees} what="your fees" />
      ) : tab === 'overview' ? (
        <Overview ledger={fees.data} />
      ) : tab === 'structure' ? (
        <Structure ledger={fees.data} />
      ) : tab === 'payments' ? (
        <Payments ledger={fees.data} />
      ) : (
        <Requests key={tab} ledger={fees.data} kind={tab === 'scholarships' ? 'SCHOLARSHIP' : 'REFUND'} />
      )}
    </>
  )
}
