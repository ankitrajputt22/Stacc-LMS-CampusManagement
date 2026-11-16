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

  const rows: [string, string | null][] = result?.valid
    ? [
        ['Name', result.name],
        ['Login ID', result.loginId],
        ['Roles', result.roles.map((role) => role.charAt(0) + role.slice(1).toLowerCase().replace('_', ' ')).join(', ')],
        ['Program', result.program],
        ['Department', result.department],
      ]
    : []
  return (
    <div className="max-w-4xl">
      <title>Check ID · Stacc</title>
      <PageHeader title="Check an ID" description="Type the code shown under the person’s QR code." />
      <div className="mt-6">
        <Card title="Check an ID card" description="Enter the 16-character code from the person’s ID card. A code works for 90 seconds.">
          <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => void submit(event)}>
            <div className="min-w-56 flex-1">
              <TextField label="ID code" value={code} maxLength={40} autoComplete="off" onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button type="submit">Check</Button>
          </form>
          <div aria-live="polite" className="mt-5 space-y-3 empty:hidden">
            {error && <Alert tone="error">{error}</Alert>}
            {result && (
              <Alert tone={result.valid ? 'success' : 'error'}>
                <p className="text-lg font-bold">{result.message}</p>
                {/* An expired or unknown code shows the message only, never who it was. */}
                {result.valid && (
                  <dl className="mt-3 grid gap-x-8 gap-y-1.5 text-[0.95rem] sm:grid-cols-[9rem_minmax(0,1fr)]">
                    {rows
                      .filter((row): row is [string, string] => Boolean(row[1]))
                      .map(([term, value]) => (
                        <div key={term} className="contents">
                          <dt className="font-semibold text-ink">{term}</dt>
                          <dd className="break-words text-ink">{value}</dd>
                        </div>
                      ))}
                  </dl>
                )}
              </Alert>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
