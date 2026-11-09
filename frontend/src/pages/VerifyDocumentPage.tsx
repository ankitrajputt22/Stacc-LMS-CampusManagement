import { type FormEvent, useState } from 'react'
import { useSearchParams } from 'react-router'
import { verifyDocument } from '../api/careerApi'
import { formatDateTime } from '../courses/dates'
import type { Authenticity } from '../types/career'
import { Alert, Button, Card, TextField } from '../ui'

/**
 * /verify-document: the public check that a Stacc verification code belongs to an authentic document. No sign-in; it
 * shows only whether it is genuine, its title, and its issue date.
 */
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
    <main className="mx-auto max-w-xl px-4 py-10">
      <title>Verify a document · Stacc</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Verify a document</h1>
      <p className="mt-2 text-sm text-ink-muted">Check that a certificate was issued by the college through Stacc.</p>
      <div className="mt-6 space-y-4">
        <Card title="Verification code">
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => void submit(event)}>
            <div className="min-w-56 flex-1">
              <TextField label="Code" value={code} maxLength={60} hint="Such as D12-0A1B2C3D4E5F6071" onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button type="submit">Verify</Button>
          </form>
        </Card>
        {error && <Alert tone="error">{error}</Alert>}
        {result && (
          <Alert tone={result.authentic ? 'success' : 'error'}>
            <p className="font-medium">{result.message}</p>
            {result.authentic && result.title && result.issuedAt && (
              <p className="mt-1">
                “{result.title}”, issued {formatDateTime(result.issuedAt)}
              </p>
            )}
          </Alert>
        )}
      </div>
    </main>
  )
}
