import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { importUsers } from '../api/adminUsersApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { StatusConfirmation } from '../components/StatusConfirmation'
import type { ImportResult, ImportRow } from '../types/users'
import { Alert, Badge, Button, ButtonLink, Card, DataTable, PageHeader, TextField } from '../ui'
import { refusal } from '../users/userText'

const EXAMPLE = `login_id,full_name,email,roles,department,program,admission_session,roll_number
26CSE001,Riya Menon,riya.menon@college.example,STUDENT,CSE,BTECH-CSE,2026-27,26CSE001
EMP301,"Das, Kiran",,FACULTY,CSE,,,
EMP302,Asha Pillai,,FACULTY;ADMIN,CSE,,,`

const columns = [
  { key: 'line', header: 'Line', primary: true, cell: (row: ImportRow) => `Line ${row.line}` },
  { key: 'login', header: 'Login ID', cell: (row: ImportRow) => row.loginId || <span className="text-ink-muted">—</span> },
  {
    key: 'result',
    header: 'Result',
    cell: (row: ImportRow) =>
      row.errors.length === 0 ? (
        <Badge tone="success">Ready</Badge>
      ) : (
        <ul className="list-disc space-y-0.5 pl-4 text-danger">
          {row.errors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      ),
  },
]

function ImportForm() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [password, setPassword] = useState('')
  const [show, setShow] = useState(false)
  const [checked, setChecked] = useState<ImportResult | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<number | null>(null)

  function changed() {
    setChecked(null)
    setConfirming(false)
    setDone(null)
    setError(null)
  }

  async function check() {
    if (!file) {
      setError('Choose a CSV file.')
      return
    }
    if (password.length < 10 || password.length > 128) {
      setError('The temporary password must be 10 to 128 characters.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      setChecked(await importUsers(file, password, true))
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setBusy(false)
    }
  }

  const ready = checked !== null && checked.rowsWithErrors === 0

  return (
    <div className="space-y-4">
      <Card
        title="CSV file"
        description="UTF-8 text, at most 500 accounts and 1 MB. The first line names the columns exactly as below. Separate several roles with ;. Department, program, and admission session use their codes; students need all three, faculty need the department. Passwords never go in the file."
      >
        <pre className="overflow-x-auto rounded-control border border-line bg-surface-muted p-3 text-xs leading-5 text-ink">{EXAMPLE}</pre>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="import-file" className="block text-sm font-medium text-ink">
              File to import
            </label>
            <input
              id="import-file"
              type="file"
              accept=".csv,text/csv"
              className="mt-1 block text-sm text-ink"
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null)
                changed()
              }}
            />
          </div>
          <div>
            <TextField
              label="Temporary password for every account"
              type={show ? 'text' : 'password'}
              value={password}
              maxLength={128}
              autoComplete="new-password"
              hint="10 to 128 characters, not common, and not containing any login ID. Each person must change it at first sign-in; it expires in a few days."
              onChange={(event) => {
                setPassword(event.target.value)
                changed()
              }}
            />
            <label className="mt-2 flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={show} onChange={(event) => setShow(event.target.checked)} />
              Show password
            </label>
          </div>
        </div>
        {error && (
          <Alert tone="error" className="mt-3">
            {error}
          </Alert>
        )}
        <Button className="mt-4" variant="secondary" aria-disabled={busy || undefined} onClick={() => void check()}>
          {busy && !confirming ? 'Checking…' : 'Check file'}
        </Button>
      </Card>

      {done !== null && (
        <Alert tone="success">
          <span role="status">
            {done === 1 ? '1 account was created.' : `${done} accounts were created.`} Give each person their login ID and
            the temporary password.
          </span>
        </Alert>
      )}

      {checked && done === null && (
        <Card
          title="Check result"
          description={
            ready
              ? `All ${checked.rows.length} rows are ready. Nothing has been created yet.`
              : `${checked.rowsWithErrors} of ${checked.rows.length} rows have problems. Fix the file and check it again; nothing is created while any row has a problem.`
          }
          padding="none"
        >
          <DataTable caption="Rows of the file" columns={columns} rows={checked.rows} rowKey={(row) => row.line} />
          {ready && (
            <div className="border-t border-line px-4 py-4 sm:px-5">
              {confirming ? (
                <StatusConfirmation
                  question={`Create ${checked.rows.length} accounts?`}
                  detail="Every account is created with its roles and profiles, all at once, and each must change the temporary password at first sign-in. Accounts are never deleted; they can only be disabled."
                  confirmLabel="Create accounts"
                  busyLabel="Creating accounts…"
                  onConfirm={async () => {
                    if (!file) {
                      return
                    }
                    const result = await importUsers(file, password, false)
                    if (result.created === 0) {
                      setChecked(result)
                      setConfirming(false)
                      return
                    }
                    setDone(result.created)
                    setConfirming(false)
                    await queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
                  }}
                  onCancel={() => setConfirming(false)}
                  describeRefusal={refusal}
                />
              ) : (
                <Button onClick={() => setConfirming(true)}>Import {checked.rows.length} accounts</Button>
              )}
            </div>
          )}
        </Card>
      )}
    </div>
  )
}

/** /admin/users/import: check a CSV file of accounts, then create them all at once. */
export function AdminUserImportPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  return (
    <div className="max-w-5xl">
      <title>Import users · Stacc</title>
      <PageHeader
        eyebrow="ERP · Manage"
        title="Import users"
        breadcrumb={<Breadcrumb parent={{ to: '/admin/users', label: 'Users' }} current="Import" />}
        description={isAdmin ? 'Create many accounts from one CSV file. Check it first; nothing is created until every row is valid.' : undefined}
        actions={isAdmin ? <ButtonLink to="/admin/users" variant="secondary">Back to users</ButtonLink> : undefined}
      />
      {isAdmin ? (
        <ImportForm />
      ) : (
        <StatePanel>
          <AccessDeniedState title="This page is for admin accounts." message="If you think you should, contact the college administration." />
        </StatePanel>
      )}
    </div>
  )
}
