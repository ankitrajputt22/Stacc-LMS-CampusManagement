import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { cancelCertificateRequest, downloadDocument, getMyCertificateRequests, getMyDocuments, requestCertificate, uploadDocument } from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { primaryLinkClasses } from '../components/actionStyles'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useFileDownload } from '../courses/useFileDownload'
import { failureText } from '../engagement/engagementText'
import type { CertificateRequest, CertificateType, VaultDocument } from '../types/career'
import { Alert, Button, Card, DataTable, LinkTabs, SelectField, TextAreaField, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

const TYPES: Record<CertificateType, string> = {
  BONAFIDE: 'Bonafide certificate',
  TRANSCRIPT: 'Transcript',
  CHARACTER: 'Character certificate',
  COURSE_COMPLETION: 'Course completion',
  OTHER: 'Other',
}
const REQUEST_STATUS = { REQUESTED: 'Requested', ISSUED: 'Issued', REFUSED: 'Refused', CANCELLED: 'Cancelled' } as const
const STATUS_TONE: Record<CertificateRequest['status'], string> = {
  REQUESTED: 'border-orange-200 bg-orange-50 text-warning',
  ISSUED: 'border-success-line bg-success-soft text-ink',
  REFUSED: 'border-danger/40 bg-danger/5 text-danger',
  CANCELLED: 'border-line-strong bg-surface-muted text-ink-muted',
}

export type DocumentsSection = 'documents' | 'upload' | 'requests' | 'request'

type Notify = (text: string, error?: boolean) => void

const backLinkClasses =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'
const secondaryLinkClasses =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-line-strong bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-9'

function sizeText(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function useRun(notify: Notify) {
  const queryClient = useQueryClient()
  return async (action: () => Promise<unknown>, text: string, fallback: string): Promise<boolean> => {
    try {
      await action()
      notify(text)
      await queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
      return true
    } catch (caught) {
      const failure = failureText(caught, fallback)
      if (failure) {
        notify(failure, true)
      }
      return false
    }
  }
}

function MyDocuments() {
  const documents = useApiQuery(queryKeys.career.documents(), (signal) => getMyDocuments(signal))
  const downloads = useFileDownload(downloadDocument)
  return (
    <Card
      title="My documents"
      padding="none"
      action={
        <div className="flex flex-wrap gap-2">
          <Link to="/documents/upload" className={secondaryLinkClasses}>
            Upload a document
          </Link>
          <Link to="/documents/requests/new" className={primaryLinkClasses}>
            Request a certificate
          </Link>
        </div>
      }
    >
      {documents.status !== 'loaded' ? (
        <SectionState request={documents} what="your documents" />
      ) : documents.data.length === 0 ? (
        <EmptyState title="No documents yet." description="Issued certificates and your uploads appear here." />
      ) : (
        <DataTable<VaultDocument>
          caption="My documents"
          rows={documents.data}
          rowKey={(document) => document.documentId}
          columns={[
            {
              key: 'title',
              header: 'Title',
              primary: true,
              cell: (document) => (
                <>
                  <span className="font-semibold break-words text-ink">{document.title}</span>
                  {document.verificationCode && !document.withdrawn && (
                    <span className="mt-0.5 block text-xs text-ink-muted">
                      Verification code <span className="font-mono text-ink">{document.verificationCode}</span>
                      {' · '}
                      <Link to="/verify-document" className={textLinkClasses}>
                        Verify a document
                      </Link>
                    </span>
                  )}
                </>
              ),
            },
            {
              key: 'origin',
              header: 'Origin',
              cell: (document) => (
                <span className="flex flex-wrap items-center gap-2">
                  {document.origin === 'ISSUED' ? 'Issued by the college' : 'Uploaded by you'}
                  {document.withdrawn && (
                    <span className="rounded-sm border border-line-strong bg-surface-muted px-1.5 py-0.5 text-xs font-medium text-ink-muted">Withdrawn</span>
                  )}
                </span>
              ),
            },
            { key: 'created', header: 'Created', cell: (document) => <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time> },
            {
              key: 'file',
              header: 'File',
              cell: (document) => (
                <span className="break-all">
                  {document.fileName}
                  <span className="block text-xs text-ink-muted">{sizeText(document.sizeBytes)}</span>
                </span>
              ),
            },
            {
              key: 'action',
              header: 'Action',
              cell: (document) => (
                <Button
                  size="sm"
                  disabled={downloads.downloadingId === document.documentId}
                  onClick={() => void downloads.download({ id: document.documentId, originalFileName: document.fileName })}
                >
                  Download<span className="sr-only">{` ${document.title}`}</span>
                </Button>
              ),
            },
          ]}
        />
      )}
    </Card>
  )
}

function UploadDocument({ notify }: { notify: Notify }) {
  const run = useRun(notify)
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  return (
    <div className="max-w-3xl space-y-4">
      <Link to="/documents" className={backLinkClasses}>
        <span aria-hidden="true">←</span> My documents
      </Link>
      <Card title="Upload a document" description="Keep your own copies here, such as a workshop certificate. Only you can open them.">
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (!file || title.trim() === '') {
              notify('Give a title and choose a file.', true)
              return
            }
            setBusy(true)
            void run(() => uploadDocument(title.trim(), file), 'Uploaded.', 'Couldn’t upload.')
              .then((ok) => ok && navigate('/documents', { state: { done: 'Uploaded.' } }))
              .finally(() => setBusy(false))
          }}
        >
          <TextField label="Title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
          <div>
            <label htmlFor="vault-file" className="block text-sm font-medium text-ink">
              File
            </label>
            <input id="vault-file" type="file" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </div>
          <div className="flex flex-wrap gap-3">
            <Button type="submit" disabled={busy}>
              Upload
            </Button>
            <Link to="/documents" className={secondaryLinkClasses}>
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}

function CertificateRequests({ notify }: { notify: Notify }) {
  const requests = useApiQuery(queryKeys.career.certificateRequests(), (signal) => getMyCertificateRequests(signal))
  const run = useRun(notify)
  const [confirming, setConfirming] = useState<number | null>(null)
  return (
    <Card
      title="Certificate requests"
      padding="none"
      action={
        <Link to="/documents/requests/new" className={primaryLinkClasses}>
          Request a certificate
        </Link>
      }
    >
      {requests.status !== 'loaded' ? (
        <SectionState request={requests} what="requests" />
      ) : requests.data.length === 0 ? (
        <EmptyState title="No requests yet." description="Ask the college office for a certificate." />
      ) : (
        <DataTable<CertificateRequest>
          caption="Certificate requests"
          rows={requests.data}
          rowKey={(request) => request.requestId}
          columns={[
            { key: 'number', header: 'Request', primary: true, cell: (request) => <span className="font-semibold">#{request.requestId}</span> },
            {
              key: 'type',
              header: 'Certificate',
              cell: (request) => (
                <>
                  {TYPES[request.type]}
                  {request.decisionNote && <span className="mt-1 block text-sm text-ink-muted">Decision note: {request.decisionNote}</span>}
                </>
              ),
            },
            { key: 'purpose', header: 'Purpose', cell: (request) => <span className="break-words">{request.purpose}</span> },
            { key: 'requested', header: 'Requested', cell: (request) => <time dateTime={request.createdAt}>{formatDateTime(request.createdAt)}</time> },
            {
              key: 'status',
              header: 'Status',
              cell: (request) => (
                <span className={`inline-block rounded-sm border px-1.5 py-0.5 text-xs font-medium ${STATUS_TONE[request.status]}`}>{REQUEST_STATUS[request.status]}</span>
              ),
            },
            {
              key: 'action',
              header: 'Action',
              cell: (request) =>
                request.status !== 'REQUESTED' ? (
                  <span aria-label="None">–</span>
                ) : confirming === request.requestId ? (
                  <span className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      onClick={() =>
                        void run(() => cancelCertificateRequest(request.requestId), 'Request cancelled.', 'Couldn’t cancel.').then(() => setConfirming(null))
                      }
                    >
                      Yes, cancel #{request.requestId}
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setConfirming(null)}>
                      Keep it
                    </Button>
                  </span>
                ) : (
                  <Button size="sm" variant="secondary" onClick={() => setConfirming(request.requestId)}>
                    Cancel request<span className="sr-only">{` #${request.requestId}`}</span>
                  </Button>
                ),
            },
          ]}
        />
      )}
    </Card>
  )
}

function RequestCertificate({ notify }: { notify: Notify }) {
  const run = useRun(notify)
  const navigate = useNavigate()
  const [type, setType] = useState<CertificateType>('BONAFIDE')
  const [purpose, setPurpose] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <div className="max-w-3xl space-y-4">
      <Link to="/documents/requests" className={backLinkClasses}>
        <span aria-hidden="true">←</span> Certificate requests
      </Link>
      <Card title="Request a certificate" description="The college office issues it into your documents, or tells you why it cannot.">
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (purpose.trim() === '') {
              notify('Say what the certificate is for.', true)
              return
            }
            setBusy(true)
            void run(() => requestCertificate(type, purpose.trim()), 'Request sent to the college office.', 'Couldn’t send the request.')
              .then((ok) => ok && navigate('/documents/requests', { state: { done: 'Request sent to the college office.' } }))
              .finally(() => setBusy(false))
          }}
        >
          <SelectField label="Certificate" value={type} onChange={(event) => setType(event.target.value as CertificateType)}>
            {(Object.keys(TYPES) as CertificateType[]).map((value) => (
              <option key={value} value={value}>
                {TYPES[value]}
              </option>
            ))}
          </SelectField>
          <TextAreaField label="Purpose" rows={3} value={purpose} maxLength={500} hint="Such as: for opening a bank account." onChange={(event) => setPurpose(event.target.value)} />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" disabled={busy}>
              Send request
            </Button>
            <Link to="/documents/requests" className={secondaryLinkClasses}>
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}

export function DocumentsPage({ section = 'documents' }: { section?: DocumentsSection }) {
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string; at: DocumentsSection } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text, at: section })
  const done = (useLocation().state as { done?: string } | null)?.done
  const shown =
    message && (message.tone === 'success' || message.at === section)
      ? message
      : done && (section === 'documents' || section === 'requests')
        ? { tone: 'success' as const, text: done }
        : null
  return (
    <>
      <title>Documents · Stacc</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Documents</h1>
      <p className="mt-2 mb-5 text-[0.95rem] leading-6 text-ink-muted sm:text-base">
        Certificates the college issued you, your own uploads, and certificate requests.
      </p>
      <LinkTabs
        label="Documents"
        tabs={[
          { to: '/documents', label: 'My documents', current: section === 'documents' || section === 'upload' },
          { to: '/documents/requests', label: 'Certificate requests', current: section === 'requests' || section === 'request' },
        ]}
      />
      <div className="space-y-4">
        <p role="status" className="sr-only">
          {shown?.tone === 'success' ? shown.text : ''}
        </p>
        {shown && <Alert tone={shown.tone}>{shown.text}</Alert>}
        {section === 'documents' && <MyDocuments />}
        {section === 'upload' && <UploadDocument notify={notify} />}
        {section === 'requests' && <CertificateRequests notify={notify} />}
        {section === 'request' && <RequestCertificate notify={notify} />}
      </div>
    </>
  )
}
