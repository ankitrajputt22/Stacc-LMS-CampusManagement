import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { approveRequest, getRequestQueue, rejectRequest } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import type { RequestKind, RequestStatus, StudentRequest } from '../types/erp'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'
import { formatDay, formatMomentDay, KIND_WORDS, refusalOf, STATUS_TONES, STATUS_WORDS } from './requestWords'

function Detail({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[8rem_1fr] gap-3 py-1.5">
      <dt className="font-semibold text-ink">{term}</dt>
      <dd className="break-words text-ink">{children}</dd>
    </div>
  )
}

/** One request's details with Approve (and new marks for a revaluation) and Reject with a required comment. */
function Review({ request, onClose }: { request: StudentRequest; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [newMarks, setNewMarks] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const open = request.status === 'SUBMITTED'

  async function decide(send: () => Promise<unknown>) {
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await send()
      await queryClient.invalidateQueries({ queryKey: queryKeys.erp.all })
      onClose()
    } catch (failure) {
      setError(refusalOf(failure))
    } finally {
      setBusy(false)
    }
  }

  function approve() {
    const marks = newMarks.trim() === '' ? null : Number(newMarks)
    void decide(() => approveRequest(request.requestId, { comment: null, newMarks: marks }))
  }

  function reject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void decide(() => rejectRequest(request.requestId, comment.trim()))
  }

  return (
    <Card>
      <div className="flex items-start justify-between gap-3 border-b border-line pb-3">
        <div>
          <h2 className="text-2xl font-bold text-ink">{KIND_WORDS[request.kind]}</h2>
          <p className="text-ink-muted">{request.reference}</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close review" className="flex size-10 items-center justify-center rounded-lg hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-stacc-primary">
          <NavIcon name="close" />
        </button>
      </div>
      <dl className="mt-3 text-[0.95rem]">
        <Detail term="College ID">{request.requesterLoginId}</Detail>
        <Detail term="Submitted">{formatMomentDay(request.createdAt)}</Detail>
        {request.fromDate && request.toDate && <Detail term="Leave dates">{`${formatDay(request.fromDate)} – ${formatDay(request.toDate)}`}</Detail>}
        {request.courseCode && <Detail term="Course">{`${request.courseCode} · ${request.courseName}`}</Detail>}
        {request.classHeldAt && <Detail term="Class">{`${formatMomentDay(request.classHeldAt)}, marked absent`}</Detail>}
        {request.marksObtained !== null && <Detail term="Marks">{`${Number(request.marksObtained)} / ${Number(request.maxMarks)}`}</Detail>}
        <Detail term="Status"><Badge tone={STATUS_TONES[request.status]}>{STATUS_WORDS[request.status]}</Badge></Detail>
      </dl>
      <h3 className="mt-4 font-semibold text-ink">Reason</h3>
      <p className="mt-1 whitespace-pre-line text-ink-muted">{request.reason}</p>
      {(request.outcome || request.decisionComment) && (
        <p className="mt-3 rounded-md bg-surface-muted px-3 py-2 text-sm">{[request.outcome, request.decisionComment].filter(Boolean).join(' — ')}</p>
      )}
      {open && (
        <div className="mt-5 space-y-4 border-t border-line pt-5">
          {request.kind === 'REVALUATION' && (
            <TextField label="New marks" type="number" min={0} step="0.01" max={request.maxMarks ?? undefined} value={newMarks}
              onChange={(event) => setNewMarks(event.target.value)} hint="Leave empty when the marks do not change." />
          )}
          {request.kind === 'ATTENDANCE_CORRECTION' && <p className="text-sm text-ink-muted">Approving marks this class present.</p>}
          <Button fullWidth disabled={busy} onClick={approve}>Approve</Button>
          <form onSubmit={reject} className="space-y-3">
            <TextAreaField label="Reject comment" required maxLength={1000} rows={3} value={comment} onChange={(event) => setComment(event.target.value)} placeholder="Enter reason for rejection" />
            <Button type="submit" variant="secondary" fullWidth disabled={busy || comment.trim() === ''}>Reject</Button>
          </form>
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      )}
    </Card>
  )
}

/** The ERP office's queue of student requests: filter, review, and decide each once. */
export function ErpRequestsPage() {
  const [kind, setKind] = useState<RequestKind | ''>('')
  const [status, setStatus] = useState<RequestStatus | ''>('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const queue = useApiQuery(queryKeys.erp.queue(kind, status, q), (signal) => getRequestQueue({ kind, status, q }, signal))
  const rows = queue.status === 'loaded' ? queue.data : []
  const chosen = rows.find((request) => request.requestId === selected) ?? null

  return (
    <>
      <title>Requests · Stacc</title>
      <PageHeader title="Requests" description="Review and process student requests." />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <Card>
          <form
            onSubmit={(event) => { event.preventDefault(); setQ(search.trim()) }}
            className="grid gap-4 sm:grid-cols-[1fr_1fr_1.3fr_auto] sm:items-end"
          >
            <SelectField label="Type" value={kind} onChange={(event) => setKind(event.target.value as RequestKind | '')}>
              <option value="">All</option>
              {(Object.keys(KIND_WORDS) as RequestKind[]).map((value) => <option key={value} value={value}>{KIND_WORDS[value]}</option>)}
            </SelectField>
            <SelectField label="Status" value={status} onChange={(event) => setStatus(event.target.value as RequestStatus | '')}>
              <option value="">All statuses</option>
              {(Object.keys(STATUS_WORDS) as RequestStatus[]).map((value) => <option key={value} value={value}>{STATUS_WORDS[value]}</option>)}
            </SelectField>
            <TextField label="Search" type="search" placeholder="Search by college ID" value={search} onChange={(event) => setSearch(event.target.value)} />
            <Button type="submit" variant="secondary">Search</Button>
          </form>
          <div className="mt-5">
            {queue.status !== 'loaded' ? (
              <SectionState request={queue} what="requests" />
            ) : rows.length === 0 ? (
              <p className="text-[0.95rem] text-ink-muted">No requests match.</p>
            ) : (
              <div className="overflow-x-auto rounded-panel border border-line">
                <table className="w-full min-w-[38rem] text-left text-[0.95rem]">
                  <caption className="sr-only">Student requests</caption>
                  <thead className="bg-surface-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-semibold">Request Type</th>
                      <th scope="col" className="px-4 py-3 font-semibold">College ID</th>
                      <th scope="col" className="px-4 py-3 font-semibold">Submitted</th>
                      <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                      <th scope="col" className="px-4 py-3 font-semibold">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((request) => (
                      <tr key={request.requestId} className={`border-t border-line ${request.requestId === selected ? 'bg-portal-bar-strong/40' : ''}`}>
                        <th scope="row" className="px-4 py-3 font-normal">
                          <span className="block text-lg font-semibold text-ink">{request.reference}</span>
                          <span className="text-ink-muted">{KIND_WORDS[request.kind]}</span>
                        </th>
                        <td className="px-4 py-3">{request.requesterLoginId}</td>
                        <td className="px-4 py-3 whitespace-nowrap">{formatMomentDay(request.createdAt)}</td>
                        <td className="px-4 py-3"><Badge tone={STATUS_TONES[request.status]}>{STATUS_WORDS[request.status]}</Badge></td>
                        <td className="px-4 py-3">
                          <Button variant="ghost" size="sm" onClick={() => setSelected(request.requestId)}>
                            {request.status === 'SUBMITTED' ? 'Review' : 'View'}<span className="sr-only"> {request.reference}</span>
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </Card>
        {chosen ? (
          <Review key={chosen.requestId} request={chosen} onClose={() => setSelected(null)} />
        ) : (
          <Card>
            <p className="py-6 text-center text-ink-muted">Choose Review on a request to see its details and decide it.</p>
          </Card>
        )}
      </div>
    </>
  )
}
