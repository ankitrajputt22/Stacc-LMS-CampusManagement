import { type FormEvent, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { checkIdCard } from '../api/careerApi'
import type { IdCheck } from '../types/career'
import { Alert, Button, Card, PageHeader, TextField } from '../ui'

/** /verify-id: staff type the code under a person's ID QR code and see whose card it is, while the code is valid. */
export function VerifyIdPage() {
  const [code, setCode] = useState('')
  const [result, setResult] = useState<IdCheck | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    try {
      setResult(await checkIdCard(code.trim()))
    } catch (failure) {
      setResult(null)
      if (getHttpStatus(failure) !== 401) {
        setError(getHttpStatus(failure) === 403 ? 'You don’t have access to check ID cards.' : 'Couldn’t check the code.')
      }
    }
  }

  return (
    <div className="max-w-xl">
      <title>Check ID · Stacc</title>
      <PageHeader title="Check an ID card" description="Type the code shown under the person's ID QR code." />
      <div className="mt-6 space-y-4">
        <Card title="Code">
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => void submit(event)}>
            <div className="min-w-56 flex-1">
              <TextField label="ID code" value={code} maxLength={40} autoComplete="off" onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button type="submit">Check</Button>
          </form>
        </Card>
        {error && <Alert tone="error">{error}</Alert>}
        {result && (
          <Alert tone={result.valid ? 'success' : 'error'}>
            <p className="font-medium">{result.message}</p>
            {result.valid && (
              <p className="mt-1">
                {result.name} · {result.loginId} · {result.roles.join(', ')}
                {result.program && ` · ${result.program}`}
                {result.department && ` · ${result.department}`}
              </p>
            )}
          </Alert>
        )}
      </div>
    </div>
  )
}
