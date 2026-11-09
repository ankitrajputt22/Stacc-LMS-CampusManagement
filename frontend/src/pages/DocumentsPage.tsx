import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { cancelCertificateRequest, downloadDocument, getMyCertificateRequests, getMyDocuments, requestCertificate, uploadDocument } from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useFileDownload } from '../courses/useFileDownload'
import { failureText } from '../engagement/engagementText'
import type { CertificateType } from '../types/career'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextField } from '../ui'

const TYPES: Record<CertificateType, string> = {
  BONAFIDE: 'Bonafide certificate',
  TRANSCRIPT: 'Transcript',
  CHARACTER: 'Character certificate',
  COURSE_COMPLETION: 'Course completion',
  OTHER: 'Other',
}
const REQUEST_STATUS = { REQUESTED: 'Requested', ISSUED: 'Issued', REFUSED: 'Refused', CANCELLED: 'Cancelled' } as const

/** /documents: the person's vault (issued certificates with verification codes, and uploads) and certificate requests. */
export function DocumentsPage() {
  const queryClient = useQueryClient()
  const documents = useApiQuery(queryKeys.career.documents(), (signal) => getMyDocuments(signal))
  const requests = useApiQuery(queryKeys.career.certificateRequests(), (signal) => getMyCertificateRequests(signal))
  const downloads = useFileDownload(downloadDocument)
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0)
  const [type, setType] = useState<CertificateType>('BONAFIDE')
  const [purpose, setPurpose] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function run(action: () => Promise<unknown>, text: string, fallback: string) {
    try {
      await action()
      setMessage({ tone: 'success', text })
      await queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
    } catch (caught) {
      const failure = failureText(caught, fallback)
      if (failure) {
        setMessage({ tone: 'error', text: failure })
      }
    }
  }

  return (
    <div className="max-w-4xl">
      <title>Documents · Stacc</title>
      <PageHeader title="Documents" description="Certificates the college issued you, your own uploads, and certificate requests." />
      <div className="mt-6 space-y-6">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <Card title="My documents" padding="none">
          {documents.status !== 'loaded' ? (
            <SectionState request={documents} what="your documents" />
          ) : documents.data.length === 0 ? (
            <EmptyState title="No documents yet." description="Issued certificates and your uploads appear here." />
          ) : (
            <ul className="divide-y divide-line text-sm">
              {documents.data.map((document) => (
                <li key={document.documentId} className="space-y-1 px-4 py-3 sm:px-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-ink">{document.title}</span>
                    <Badge tone={document.origin === 'ISSUED' ? 'brand' : 'neutral'}>{document.origin === 'ISSUED' ? 'Issued by the college' : 'Uploaded by you'}</Badge>
                    {document.withdrawn && <Badge tone="danger">Withdrawn</Badge>}
                  </div>
                  <p className="text-ink-muted">{formatDateTime(document.createdAt)} · {document.fileName}</p>
                  {document.verificationCode && !document.withdrawn && (
                    <p className="text-ink">
                      Verification code <span className="font-mono">{document.verificationCode}</span>
                      <span className="text-ink-muted"> · anyone can check it at /verify-document</span>
                    </p>
                  )}
                  <Button size="sm" variant="secondary" disabled={downloads.downloadingId === document.documentId}
                    onClick={() => void downloads.download({ id: document.documentId, originalFileName: document.fileName })}>
                    Download<span className="sr-only">{` ${document.title}`}</span>
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title="Upload a document">
          <form
            className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              if (!file || title.trim() === '') {
                setMessage({ tone: 'error', text: 'Give a title and choose a file.' })
                return
              }
              void run(() => uploadDocument(title.trim(), file), 'Uploaded.', 'Couldn’t upload.').then(() => {
                setTitle('')
                setFile(null)
                setFileKey((key) => key + 1)
              })
            }}
          >
            <TextField label="Title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
            <div>
              <label htmlFor={`vault-file-${fileKey}`} className="block text-sm font-medium text-ink">File</label>
              <input key={fileKey} id={`vault-file-${fileKey}`} type="file" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
            </div>
            <Button type="submit" variant="secondary">Upload</Button>
          </form>
        </Card>
        <Card title="Certificate requests" padding="none">
          <form
            className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-[14rem_1fr_auto] sm:items-end sm:px-5"
            onSubmit={(event) => {
              event.preventDefault()
              void run(() => requestCertificate(type, purpose.trim()), 'Request sent to the college office.', 'Couldn’t send the request.').then(() => setPurpose(''))
            }}
          >
            <SelectField label="Certificate" value={type} onChange={(event) => setType(event.target.value as CertificateType)}>
              {(Object.keys(TYPES) as CertificateType[]).map((value) => <option key={value} value={value}>{TYPES[value]}</option>)}
            </SelectField>
            <TextField label="Purpose" value={purpose} maxLength={500} onChange={(event) => setPurpose(event.target.value)} />
            <Button type="submit">Request</Button>
          </form>
          {requests.status !== 'loaded' ? (
            <SectionState request={requests} what="requests" />
          ) : requests.data.length === 0 ? (
            <EmptyState title="No requests yet." description="Ask for a certificate above." />
          ) : (
            <ul className="divide-y divide-line text-sm">
              {requests.data.map((request) => (
                <li key={request.requestId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 sm:px-5">
                  <span>
                    <span className="font-medium text-ink">{TYPES[request.type]}</span>
                    <span className="text-ink-muted"> · {request.purpose} · {formatDateTime(request.createdAt)} · </span>
                    <Badge tone={request.status === 'ISSUED' ? 'success' : request.status === 'REQUESTED' ? 'info' : 'neutral'}>{REQUEST_STATUS[request.status]}</Badge>
                    {request.decisionNote && <span className="block text-ink-muted">{request.decisionNote}</span>}
                  </span>
                  {request.status === 'REQUESTED' && (
                    <Button size="sm" variant="secondary" onClick={() => void run(() => cancelCertificateRequest(request.requestId), 'Request cancelled.', 'Couldn’t cancel.')}>
                      Cancel request
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
