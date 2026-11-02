import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { searchUsers } from '../api/adminUsersApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import type { AccountStatus, RoleName, UserFilters, UserSummary } from '../types/users'
import { Badge, Button, ButtonLink, Card, DataTable, PageHeader, Pagination, SelectField, TextField } from '../ui'
import { ROLES, roleWord, SIGN_IN_AGAIN, statusTone, statusWord, USERS_DENIED } from '../users/userText'

const PAGE_SIZE = 20

const columns = [
  {
    key: 'user',
    header: 'User',
    primary: true,
    cell: (row: UserSummary) => (
      <>
        <Link to={`/admin/users/${row.accountId}`} className="font-medium text-stacc-primary underline-offset-4 hover:underline">
          {row.fullName ?? row.loginId}
        </Link>
        <span className="block text-sm font-normal text-ink-muted">{row.loginId}</span>
      </>
    ),
  },
  { key: 'email', header: 'Email', cell: (row: UserSummary) => row.email ?? <span className="text-ink-muted">—</span> },
  { key: 'roles', header: 'Roles', cell: (row: UserSummary) => row.roles.map(roleWord).join(', ') },
  {
    key: 'status',
    header: 'Status',
    cell: (row: UserSummary) => (
      <span className="flex flex-wrap gap-1">
        <Badge tone={statusTone(row.status)}>{statusWord(row.status)}</Badge>
        {row.passwordChangeRequired && <Badge tone="warning">Must change password</Badge>}
      </span>
    ),
  },
]

function UserList() {
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const filters: UserFilters = {
    role: role === '' ? undefined : (role as RoleName),
    status: status === '' ? undefined : (status as AccountStatus),
    q: query === '' ? undefined : query,
    page: page - 1,
    size: PAGE_SIZE,
  }
  const request = useApiQuery(queryKeys.users.list(filters), (signal) => searchUsers(filters, signal))

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQuery(text.trim())
    setPage(1)
  }

  let body
  switch (request.status) {
    case 'loading':
      body = <LoadingState label="Loading users…" />
      break
    case 'denied':
      body = <AccessDeniedState title={USERS_DENIED} message={SIGN_IN_AGAIN} />
      break
    case 'failed':
    case 'notFound':
      body = <ErrorState title="Couldn’t load users." message="Check your connection and try again." onRetry={request.retry} />
      break
    case 'loaded':
      body = (
        <>
          <DataTable
            caption="Users"
            columns={columns}
            rows={request.data.items}
            rowKey={(row) => row.accountId}
            empty={<EmptyState title="No users match." description="Change the filters or the search." />}
          />
          <Pagination page={page} pageSize={PAGE_SIZE} total={request.data.totalElements} onPage={setPage} noun="users" />
        </>
      )
  }

  return (
    <Card title="Accounts" description="Every account of the college. Accounts are disabled, never deleted." padding="none">
      <div className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-3 sm:px-5">
        <SelectField label="Role" value={role} onChange={(event) => { setRole(event.target.value); setPage(1) }}>
          <option value="">Any role</option>
          {ROLES.map((value) => (
            <option key={value} value={value}>
              {roleWord(value)}
            </option>
          ))}
        </SelectField>
        <SelectField label="Status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(1) }}>
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="DISABLED">Disabled</option>
        </SelectField>
        <form role="search" onSubmit={search} className="flex items-end gap-2">
          <TextField
            className="min-w-0 flex-1"
            label="Search users"
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

/** Admin user management at /admin/users: find accounts and open one, or add a new account. */
export function AdminUsersPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  return (
    <div className="max-w-6xl">
      <title>Users · Stacc</title>
      <PageHeader
        eyebrow="ERP · Manage"
        title="Users"
        description={isAdmin ? 'Find accounts, add new ones, and manage their roles, status, and passwords.' : undefined}
        actions={isAdmin ? <ButtonLink to="/admin/users/new">Add user</ButtonLink> : undefined}
      />
      {isAdmin ? (
        <UserList />
      ) : (
        <StatePanel>
          <AccessDeniedState title="This page is for admin accounts." message="If you think you should, contact the college administration." />
        </StatePanel>
      )}
    </div>
  )
}
