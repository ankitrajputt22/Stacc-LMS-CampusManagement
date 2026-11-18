import { type FormEvent, useState } from 'react'
import { useSearchParams } from 'react-router'
import { verifyDocument } from '../api/careerApi'
import { formatDateTime } from '../courses/dates'
import type { Authenticity } from '../types/career'
import { PortalEmblem } from '../login/PortalEmblem'
import { Alert, Button, TextField } from '../ui'

export function VerifyDocumentPage() {
  const [params] = useSearchParams()
  const [code, setCode] = useState(params.get('code') ?? '')
  const [result, setResult] = useState<Authenticity | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    try {
      setResult(await verifyDocument(code.trim()))
    } catch {
      setResult(null)
      setError('Couldn’t check the code right now. Try again.')
    }
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
          <PortalEmblem className="size-10" />
          <span className="border-l border-line-strong pl-3 leading-tight">
            <span className="block text-xl font-extrabold tracking-[0.08em] text-portal-navy">STACC</span>
            <span className="block text-xs font-semibold tracking-wide text-portal-navy">COLLEGE ERP &amp; LMS</span>
          </span>
        </div>
      </header>
      <main className="mx-auto max-w-2xl px-4 py-10">
        <title>Verify a document · Stacc</title>
        <div className="rounded-panel border border-line bg-surface px-5 py-6 sm:px-8">
          <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Verify a document</h1>
          <p className="mt-2 text-[0.95rem] text-ink-muted">Check that a certificate was issued by the college through Stacc.</p>
          <form className="mt-6 flex flex-wrap items-end gap-3" onSubmit={(event) => void submit(event)}>
            <div className="min-w-56 flex-1">
              <TextField label="Code" value={code} maxLength={60} hint="The verification code printed on the document, such as D12-0A1B2C3D4E5F6071." onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button type="submit">Verify</Button>
          </form>
          {(error || result) && <hr className="my-6 border-line" />}
          {error && <Alert tone="error">{error}</Alert>}
          {result && (
            <div aria-live="polite" className="space-y-4">
              <Alert tone={result.authentic ? 'success' : 'error'}>
                <p className="text-base font-semibold">{result.message}</p>
              </Alert>
              {result.authentic && result.title && result.issuedAt && (
                <div>
                  <p className="text-xl font-bold text-ink">{result.title}</p>
                  <p className="mt-1 text-[0.95rem]">
                    <span className="mr-4 text-ink-muted">Issued</span>
                    <time dateTime={result.issuedAt}>{formatDateTime(result.issuedAt)}</time>
                  </p>
                </div>
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
