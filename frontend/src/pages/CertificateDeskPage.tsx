import { useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { getCertificateQueue, issueCertificate, refuseCertificate } from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { CertificateRequest, CertificateType } from '../types/career'
import { Alert, Button, Card, DataTable, PageHeader, TextAreaField, TextField } from '../ui'

const TYPES: Record<CertificateType, string> = {
  BONAFIDE: 'Bonafide certificate',
  TRANSCRIPT: 'Transcript',
  CHARACTER: 'Character certificate',
  COURSE_COMPLETION: 'Course completion',
  OTHER: 'Other',
}

type Notify = (text: string, error?: boolean) => void

const backLinkClasses =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'
const openLinkClasses =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-portal-cta px-4 text-sm font-medium text-portal-cta hover:bg-orange-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-9'

function RequestedTag() {
  return <span className="inline-block rounded-sm border border-orange-200 bg-orange-50 px-1.5 py-0.5 text-xs font-medium text-warning">Requested</span>
}

function Queue({ queue }: { queue: CertificateRequest[] }) {
  return (
    <Card title="Waiting" padding="none">
      {queue.length === 0 ? (
        <EmptyState title="Nothing waiting." description="New certificate requests appear here." />
      ) : (
        <DataTable<CertificateRequest>
          caption="Waiting certificate requests"
          rows={queue}
          rowKey={(request) => request.requestId}
          columns={[
            { key: 'number', header: 'Request #', primary: true, cell: (request) => <span className="font-semibold">#{request.requestId}</span> },
            { key: 'student', header: 'Student', cell: (request) => request.requesterLoginId },
            { key: 'type', header: 'Certificate type', cell: (request) => TYPES[request.type] },
            { key: 'purpose', header: 'Purpose', cell: (request) => <span className="break-words">{request.purpose}</span> },
            { key: 'requested', header: 'Requested on', cell: (request) => <time dateTime={request.createdAt}>{formatDateTime(request.createdAt)}</time> },
            { key: 'status', header: 'Status', cell: () => <RequestedTag /> },
            {
              key: 'open',
              header: 'Action',
              cell: (request) => (
                <Link to={`/admin/certificates/${request.requestId}`} className={openLinkClasses}>
                  Open<span className="sr-only">{` request ${request.requestId} from ${request.requesterLoginId}`}</span>
                </Link>
              ),
            },
          ]}
        />
      )}
    </Card>
  )
}

function SubHeading({ children }: { children: string }) {
  return (
    <>
      <h2 className="text-lg font-bold text-portal-navy">{children}</h2>
      <span aria-hidden="true" className="mt-1.5 mb-3 block h-0.5 w-14 rounded bg-portal-cta" />
    </>
  )
}

/** One waiting request: its details beside the two decisions, issuing a file into the vault or refusing with a reason. */
function Decide({ request, notify }: { request: CertificateRequest; notify: Notify }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [title, setTitle] = useState(TYPES[request.type])
  const [file, setFile] = useState<File | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const finish = async (action: () => Promise<unknown>, done: string, fallback: string) => {
    setBusy(true)
    try {
      await action()
      await queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
      void navigate('/admin/certificates', { state: { done } })
    } catch (caught) {
      notify(failureText(caught, fallback) ?? '', true)
    } finally {
      setBusy(false)
    }
  }
  const rows: [string, ReactNode][] = [
    ['Request #', String(request.requestId)],
    ['From', request.requesterLoginId],
    ['Certificate type', TYPES[request.type]],
    ['Status', <RequestedTag key="status" />],
    ['Requested on', <time key="on" dateTime={request.createdAt}>{formatDateTime(request.createdAt)}</time>],
  ]
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[22rem_minmax(0,1fr)]">
      <section aria-label="Request details" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
        <Link to="/admin/certificates" className={backLinkClasses}>
          <span aria-hidden="true">←</span> Back to Waiting
        </Link>
        <div className="mt-4">
          <SubHeading>Request details</SubHeading>
        </div>
        <dl className="divide-y divide-line text-[0.95rem]">
          {rows.map(([term, value]) => (
            <div key={term} className="grid grid-cols-[9rem_minmax(0,1fr)] gap-3 py-2">
              <dt className="font-semibold text-ink">{term}</dt>
              <dd className="break-words text-ink">{value}</dd>
            </div>
          ))}
          <div className="py-2">
            <dt className="font-semibold text-ink">Purpose</dt>
            <dd className="break-words whitespace-pre-wrap text-ink">{request.purpose}</dd>
          </div>
        </dl>
      </section>
      <section aria-label="Decision" className="space-y-6 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            if (!file || title.trim() === '') {
              notify('Give the certificate a title and attach the file.', true)
              return
            }
            void finish(() => issueCertificate(request.requestId, title.trim(), file), `Issued to ${request.requesterLoginId}.`, 'Couldn’t issue.')
          }}
          className="space-y-3"
        >
          <SubHeading>Issue certificate</SubHeading>
          <p className="text-sm text-ink-muted">Issuing adds the file to the requester’s documents with a verification code.</p>
          <TextField label={`Certificate title for ${request.requesterLoginId}`} value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
          <div>
            <label htmlFor="issue-file" className="block text-sm font-medium text-ink">
              Certificate file
            </label>
            <input id="issue-file" type="file" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </div>
          <Button type="submit" disabled={busy}>
            Issue
          </Button>
        </form>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            if (note.trim() === '') {
              notify('Give the reason for refusing.', true)
              return
            }
            void finish(() => refuseCertificate(request.requestId, note.trim()), 'Refused.', 'Couldn’t refuse.')
          }}
          className="space-y-3 border-t border-line pt-5"
        >
          <SubHeading>Reason to refuse</SubHeading>
          <TextAreaField label="Reason to refuse" rows={3} value={note} maxLength={500} hint="The requester sees this note." onChange={(event) => setNote(event.target.value)} />
          <Button type="submit" variant="secondary" disabled={busy}>
            Refuse
          </Button>
        </form>
      </section>
    </div>
  )
}

/**
 * /admin/certificates and /admin/certificates/:requestId: open certificate requests, each issued into the requester's
 * vault with a file, or refused with a reason.
 */
export function CertificateDeskPage() {
  const { requestId } = useParams()
  const queue = useApiQuery(queryKeys.career.certificateQueue('REQUESTED'), (signal) => getCertificateQueue('REQUESTED', signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text })
  const navigateDone = (useLocation().state as { done?: string } | null)?.done
  const id = Number(requestId)
  const open = requestId !== undefined && queue.status === 'loaded' ? queue.data.find((request) => request.requestId === id) : undefined
  return (
    <div className="max-w-6xl">
      <title>Certificates · Stacc</title>
      <PageHeader title="Certificate requests" description="Issue certificates into students’ documents, or refuse with a reason." />
      <div className="mt-6 space-y-4">
        <p role="status" className="sr-only">
          {requestId === undefined ? (navigateDone ?? '') : ''}
        </p>
        {message && requestId !== undefined && <Alert tone={message.tone}>{message.text}</Alert>}
        {requestId === undefined && navigateDone && <Alert tone="success">{navigateDone}</Alert>}
        {queue.status !== 'loaded' ? (
          <Card>
            <SectionState request={queue} what="requests" />
          </Card>
        ) : requestId === undefined ? (
          <Queue queue={queue.data} />
        ) : open ? (
          <Decide key={open.requestId} request={open} notify={notify} />
        ) : (
          <Card>
            <EmptyState title="Request not waiting" description="It has been decided or cancelled, or it does not exist." />
          </Card>
        )}
      </div>
    </div>
  )
}
