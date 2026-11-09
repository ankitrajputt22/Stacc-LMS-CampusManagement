import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getCertificateQueue, issueCertificate, refuseCertificate } from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { CertificateRequest } from '../types/career'
import { Alert, Button, Card, PageHeader, TextField } from '../ui'

function RequestRow({ request, onDone }: { request: CertificateRequest; onDone: (text: string, error?: boolean) => void }) {
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [note, setNote] = useState('')
  return (
    <li className="space-y-2 px-4 py-4 text-sm sm:px-5">
      <p>
        <span className="font-semibold text-ink">{request.requesterLoginId}</span>
        <span className="text-ink-muted"> · {request.type.replace('_', ' ').toLowerCase()} · {request.purpose} · {formatDateTime(request.createdAt)}</span>
      </p>
      <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <TextField label={`Certificate title for ${request.requesterLoginId}`} value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
        <div>
          <label htmlFor={`issue-file-${request.requestId}`} className="block text-sm font-medium text-ink">Certificate file</label>
          <input id={`issue-file-${request.requestId}`} type="file" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
        </div>
        <Button
          onClick={() => {
            if (!file || title.trim() === '') {
              onDone('Give the certificate a title and attach the file.', true)
              return
            }
            void issueCertificate(request.requestId, title.trim(), file)
              .then(() => onDone(`Issued to ${request.requesterLoginId}.`))
              .catch((caught: unknown) => onDone(failureText(caught, 'Couldn’t issue.') ?? '', true))
          }}
        >
          Issue
        </Button>
      </div>
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-56 flex-1">
          <TextField label="Reason to refuse" value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />
        </div>
        <Button
          variant="secondary"
          onClick={() =>
            void refuseCertificate(request.requestId, note.trim())
              .then(() => onDone('Refused.'))
              .catch((caught: unknown) => onDone(failureText(caught, 'Couldn’t refuse.') ?? '', true))
          }
        >
          Refuse
        </Button>
      </div>
    </li>
  )
}

/** /admin/certificates: open certificate requests, issued into the requester's vault with a file, or refused with a reason. */
export function CertificateDeskPage() {
  const queryClient = useQueryClient()
  const queue = useApiQuery(queryKeys.career.certificateQueue('REQUESTED'), (signal) => getCertificateQueue('REQUESTED', signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const done = (text: string, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    if (!error) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
    }
  }
  return (
    <div className="max-w-4xl">
      <title>Certificates · Stacc</title>
      <PageHeader title="Certificate requests" description="Issue certificates into students' vaults, or refuse with a reason." />
      <div className="mt-6 space-y-4">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <Card title="Waiting" padding="none">
          {queue.status !== 'loaded' ? (
            <SectionState request={queue} what="requests" />
          ) : queue.data.length === 0 ? (
            <EmptyState title="Nothing waiting." description="New certificate requests appear here." />
          ) : (
            <ul className="divide-y divide-line">
              {queue.data.map((request) => <RequestRow key={request.requestId} request={request} onDone={done} />)}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
