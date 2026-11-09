import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { searchAuditEvents } from '../api/auditApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import type { AuditAction, AuditFilters, AuditRow } from '../types/audit'
import { Button, Card, DataTable, PageHeader, Pagination, SelectField, TextField } from '../ui'

const PAGE_SIZE = 20
const DENIED = 'You don’t have access to the audit log.'

const ACTION_WORDS: Record<AuditAction, string> = {
  USER_CREATED: 'Account created',
  USER_DETAILS_CHANGED: 'Official details corrected',
  USER_STATUS_CHANGED: 'Status changed',
  USER_ROLES_CHANGED: 'Roles changed',
  USER_TEMPORARY_PASSWORD_ISSUED: 'Temporary password issued',
  STUDENT_PROFILE_CREATED: 'Student profile created',
  FACULTY_PROFILE_CREATED: 'Faculty profile created',
}

/** A calendar date typed in the browser, as the moment its day starts there; empty stays empty. */
function startOfLocalDay(date: string, plusDays = 0): string | undefined {
  if (date === '') {
    return undefined
  }
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day + plusDays).toISOString()
}

const columns = [
  { key: 'when', header: 'When', primary: true, cell: (row: AuditRow) => formatDateTime(row.occurredAt) },
  { key: 'who', header: 'By', cell: (row: AuditRow) => row.actorLoginId ?? <span className="text-ink-muted">System</span> },
  { key: 'what', header: 'Action', cell: (row: AuditRow) => ACTION_WORDS[row.action] ?? row.action },
  {
    key: 'target',
    header: 'Account',
    cell: (row: AuditRow) =>
      row.targetType === 'USER_ACCOUNT' && row.targetId !== null ? (
        <Link to={`/admin/users/${row.targetId}`} className="text-stacc-primary underline-offset-4 hover:underline">
          {row.targetLoginId ?? `Account ${row.targetId}`}
        </Link>
      ) : (
        <span className="text-ink-muted">—</span>
      ),
  },
  { key: 'details', header: 'Details', cell: (row: AuditRow) => row.details ?? '' },
]

function AuditLog() {
  const [action, setAction] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const filters: AuditFilters = {
    action: action === '' ? undefined : (action as AuditAction),
    from: startOfLocalDay(from),
    // The end date is included: the range ends where the next day starts.
    to: startOfLocalDay(to, 1),
    q: query === '' ? undefined : query,
    page: page - 1,
    size: PAGE_SIZE,
  }
  const request = useApiQuery(queryKeys.audit.events(filters), (signal) => searchAuditEvents(filters, signal))

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery(text.trim())
    setPage(1)
  }

  let body
  switch (request.status) {
    case 'loading':
      body = <LoadingState label="Loading the audit log…" />
      break
    case 'denied':
      body = <AccessDeniedState title={DENIED} message="If your access changed recently, sign out and sign in again." />
      break
    case 'failed':
    case 'notFound':
      body = <ErrorState title="Couldn’t load the audit log." message="Check the dates and your connection, then try again." onRetry={request.retry} />
      break
    case 'loaded':
      body = (
        <>
          <DataTable
            caption="Audit log"
            columns={columns}
            rows={request.data.items}
            rowKey={(row) => row.auditEventId}
            empty={<EmptyState title="No recorded actions match." description="Change the filters or the search." />}
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={request.data.totalElements} onPage={setPage} noun="events" />
        </>
      )
  }

  return (
    <Card title="Recorded actions" description="Every sensitive change, newest first. Records are never changed or deleted." padding="none">
      <div className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5 lg:grid-cols-4">
        <SelectField label="Action" value={action} onChange={(event) => { setAction(event.target.value); setPage(1) }}>
          <option value="">Any action</option>
          {(Object.keys(ACTION_WORDS) as AuditAction[]).map((value) => (
            <option key={value} value={value}>
              {ACTION_WORDS[value]}
            </option>
          ))}
        </SelectField>
        <TextField label="From" type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(1) }} />
        <TextField label="To" type="date" value={to} onChange={(event) => { setTo(event.target.value); setPage(1) }} />
        <form role="search" onSubmit={search} className="flex items-end gap-2">
          <TextField
            className="min-w-0 flex-1"
            label="Search login IDs"
            type="search"
            value={text}
            maxLength={100}
            autoComplete="off"
            onChange={(event) => setText(event.target.value)}
          />
          <Button type="submit" variant="secondary">
            Find
          </Button>
        </form>
      </div>
      <div aria-live="polite">{body}</div>
    </Card>
  )
}

/** /admin/audit: the read-only audit log for admins. */
export function AdminAuditPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  return (
    <div className="max-w-6xl">
      <title>Audit log · Stacc</title>
      <PageHeader eyebrow="ERP · Manage" title="Audit log" description={isAdmin ? 'Who changed what, and when.' : undefined} />
      {isAdmin ? (
        <AuditLog />
      ) : (
        <StatePanel>
          <AccessDeniedState title="This page is for admin accounts." message="If you think you should, contact the college administration." />
        </StatePanel>
      )}
    </div>
  )
}
