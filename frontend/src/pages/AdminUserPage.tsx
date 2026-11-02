import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { getAcademicSessions, getDepartments, getPrograms } from '../api/adminAcademicApi'
import {
  addFacultyProfile,
  addStudentProfile,
  getUser,
  issueTemporaryPassword,
  updateUserDetails,
  updateUserRoles,
  updateUserStatus,
} from '../api/adminUsersApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { formatDateTime } from '../courses/dates'
import type { RoleName, UserDetail } from '../types/users'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextField } from '../ui'
import { refusal, ROLES, roleWord, SIGN_IN_AGAIN, statusTone, statusWord, USERS_DENIED } from '../users/userText'

/** Sends a change, shows the backend's refusal in safe words, and puts the new account into the cache. */
function useSave(accountId: number) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function save(send: () => Promise<UserDetail>): Promise<boolean> {
    if (busy) {
      return false
    }
    setBusy(true)
    setError(null)
    try {
      queryClient.setQueryData(queryKeys.users.one(accountId), await send())
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.all, refetchType: 'none' })
      return true
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
      return false
    } finally {
      setBusy(false)
    }
  }
  return { busy, error, save }
}

function StatusCard({ user, isSelf, onDone }: { user: UserDetail; isSelf: boolean; onDone: (message: string) => void }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const active = user.status === 'ACTIVE'
  return (
    <Card title="Status" description="Disabling ends every session at once and keeps all history. Accounts are never deleted.">
      <p className="text-sm text-ink">
        This account is <Badge tone={statusTone(user.status)}>{statusWord(user.status)}</Badge>
      </p>
      {isSelf && active ? (
        <p className="mt-2 text-sm text-ink-muted">You cannot disable your own account.</p>
      ) : confirming ? (
        <StatusConfirmation
          question={active ? `Disable ${user.loginId}?` : `Activate ${user.loginId}?`}
          detail={active ? 'They are signed out everywhere and cannot sign in until the account is activated again.' : 'They can sign in again with their password.'}
          confirmLabel={active ? 'Disable account' : 'Activate account'}
          busyLabel="Saving…"
          onConfirm={async () => {
            const saved = await updateUserStatus(user.accountId, active ? 'DISABLED' : 'ACTIVE')
            queryClient.setQueryData(queryKeys.users.one(user.accountId), saved)
            void queryClient.invalidateQueries({ queryKey: queryKeys.users.all, refetchType: 'none' })
            setConfirming(false)
            onDone(active ? 'Account disabled.' : 'Account activated.')
          }}
          onCancel={() => setConfirming(false)}
          describeRefusal={refusal}
        />
      ) : (
        <Button className="mt-3" variant={active ? 'danger' : 'secondary'} onClick={() => setConfirming(true)}>
          {active ? 'Disable account' : 'Activate account'}
        </Button>
      )}
    </Card>
  )
}

function RolesCard({ user, isSelf, onDone }: { user: UserDetail; isSelf: boolean; onDone: (message: string) => void }) {
  const [roles, setRoles] = useState<RoleName[]>(user.roles)
  const [confirming, setConfirming] = useState(false)
  const queryClient = useQueryClient()
  const changed = roles.length !== user.roles.length || roles.some((role) => !user.roles.includes(role))
  return (
    <Card title="Roles" description="Roles decide which areas the person can open. Saving a change signs them out so it applies at once.">
      <fieldset>
        <legend className="sr-only">Roles of {user.loginId}</legend>
        <div className="flex flex-wrap gap-4">
          {ROLES.map((role) => (
            <label key={role} className="flex items-center gap-2 text-sm text-ink">
              <input
                type="checkbox"
                checked={roles.includes(role)}
                disabled={isSelf && role === 'ADMIN' && user.roles.includes('ADMIN')}
                onChange={(event) =>
                  setRoles((current) =>
                    event.target.checked ? ROLES.filter((value) => value === role || current.includes(value)) : current.filter((value) => value !== role),
                  )
                }
              />
              {roleWord(role)}
            </label>
          ))}
        </div>
      </fieldset>
      {isSelf && <p className="mt-2 text-sm text-ink-muted">You cannot remove your own Admin role.</p>}
      {roles.length === 0 && <p className="mt-2 text-sm text-danger">Choose at least one role.</p>}
      {confirming ? (
        <StatusConfirmation
          question={`Change the roles of ${user.loginId}?`}
          detail={`New roles: ${roles.map(roleWord).join(', ')}. They are signed out everywhere and get the new roles at their next sign-in. Profiles are kept.`}
          confirmLabel="Save roles"
          busyLabel="Saving…"
          onConfirm={async () => {
            const saved = await updateUserRoles(user.accountId, roles)
            queryClient.setQueryData(queryKeys.users.one(user.accountId), saved)
            void queryClient.invalidateQueries({ queryKey: queryKeys.users.all, refetchType: 'none' })
            setConfirming(false)
            onDone('Roles saved.')
          }}
          onCancel={() => setConfirming(false)}
          describeRefusal={refusal}
        />
      ) : (
        <Button className="mt-3" variant="secondary" disabled={!changed || roles.length === 0} onClick={() => setConfirming(true)}>
          Save roles
        </Button>
      )}
    </Card>
  )
}

function DetailsCard({ user, onDone }: { user: UserDetail; onDone: (message: string) => void }) {
  const [fullName, setFullName] = useState(user.fullName ?? '')
  const [email, setEmail] = useState(user.email ?? '')
  const [nameError, setNameError] = useState<string | null>(null)
  const { busy, error, save } = useSave(user.accountId)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (fullName.trim() === '') {
      setNameError('Enter the full name.')
      return
    }
    setNameError(null)
    if (await save(() => updateUserDetails(user.accountId, fullName.trim(), email.trim() === '' ? null : email.trim()))) {
      onDone('Official details saved.')
    }
  }
  return (
    <Card title="Official details" description="Set by the college. The person can change only their preferred name and phone.">
      <form noValidate onSubmit={submit} className="space-y-3">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Full name" value={fullName} onChange={(event) => setFullName(event.target.value)} error={nameError} maxLength={150} />
          <TextField label="Email (optional)" type="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={254} />
        </div>
        {user.preferredName && <p className="text-sm text-ink-muted">Preferred name: {user.preferredName}</p>}
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" variant="secondary" aria-disabled={busy || undefined}>
          {busy ? 'Saving…' : 'Save details'}
        </Button>
      </form>
    </Card>
  )
}

function PasswordCard({ user, onDone }: { user: UserDetail; onDone: (message: string) => void }) {
  const [password, setPassword] = useState('')
  const [show, setShow] = useState(false)
  const [lengthError, setLengthError] = useState<string | null>(null)
  const { busy, error, save } = useSave(user.accountId)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password.length < 10 || password.length > 128) {
      setLengthError('Use 10 to 128 characters.')
      return
    }
    setLengthError(null)
    if (await save(() => issueTemporaryPassword(user.accountId, password))) {
      setPassword('')
      onDone('Temporary password set. Give it to the person; they must change it at their next sign-in.')
    }
  }
  return (
    <Card
      title="Temporary password"
      description="Replaces the password with one the person must change at their next sign-in. It expires in a few days, and every session ends. No one can see an existing password."
    >
      {user.passwordChangeRequired && user.temporaryPasswordExpiresAt && (
        <p className="mb-3 text-sm text-ink-muted">
          The current temporary password expires {formatDateTime(user.temporaryPasswordExpiresAt)}.
        </p>
      )}
      <form noValidate onSubmit={submit} className="space-y-3">
        <TextField
          label="New temporary password"
          type={show ? 'text' : 'password'}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={lengthError}
          hint="10 to 128 characters, not a common password, and not containing the login ID."
          maxLength={128}
          autoComplete="new-password"
        />
        <label className="flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={show} onChange={(event) => setShow(event.target.checked)} />
          Show password
        </label>
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" variant="secondary" aria-disabled={busy || undefined}>
          {busy ? 'Saving…' : 'Set temporary password'}
        </Button>
      </form>
    </Card>
  )
}

function ProfilesCard({ user, onDone }: { user: UserDetail; onDone: (message: string) => void }) {
  const needsStudent = user.roles.includes('STUDENT') && user.studentProfile === null
  const needsFaculty = user.roles.includes('FACULTY') && user.facultyProfile === null
  const programs = useApiQuery(needsStudent ? queryKeys.admin.programs() : null, getPrograms)
  const sessions = useApiQuery(needsStudent ? queryKeys.admin.sessions() : null, getAcademicSessions)
  const departments = useApiQuery(needsFaculty ? queryKeys.admin.departments() : null, getDepartments)
  const [programId, setProgramId] = useState('')
  const [sessionId, setSessionId] = useState('')
  const [rollNumber, setRollNumber] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const { busy, error, save } = useSave(user.accountId)

  if (!user.studentProfile && !user.facultyProfile && !needsStudent && !needsFaculty) {
    return null
  }
  return (
    <Card title="Academic profiles" description="Academic details for the student and faculty roles. Corrections are made under Academic Admin → People.">
      <div className="space-y-4">
        {user.studentProfile && (
          <p className="text-sm text-ink">
            <span className="font-medium">Student:</span> {user.studentProfile.programCode} · admitted {user.studentProfile.admissionSessionCode}
            {user.studentProfile.rollNumber && ` · roll ${user.studentProfile.rollNumber}`} · {user.studentProfile.status === 'ACTIVE' ? 'Active' : 'Inactive'}
          </p>
        )}
        {user.facultyProfile && (
          <p className="text-sm text-ink">
            <span className="font-medium">Faculty:</span> {user.facultyProfile.departmentCode} · {user.facultyProfile.departmentName} ·{' '}
            {user.facultyProfile.status === 'ACTIVE' ? 'Active' : 'Inactive'}
          </p>
        )}
        {needsStudent && (
          <div className="grid gap-3 sm:grid-cols-4 sm:items-end">
            <SelectField label="Program" value={programId} onChange={(event) => setProgramId(event.target.value)}>
              <option value="">Choose a program</option>
              {(programs.status === 'loaded' ? programs.data : [])
                .filter((program) => program.status === 'ACTIVE')
                .map((program) => (
                  <option key={program.id} value={String(program.id)}>
                    {program.code}
                  </option>
                ))}
            </SelectField>
            <SelectField label="Admission session" value={sessionId} onChange={(event) => setSessionId(event.target.value)}>
              <option value="">Choose a session</option>
              {(sessions.status === 'loaded' ? sessions.data : []).map((session) => (
                <option key={session.id} value={String(session.id)}>
                  {session.code}
                </option>
              ))}
            </SelectField>
            <TextField label="Roll number (optional)" value={rollNumber} onChange={(event) => setRollNumber(event.target.value)} maxLength={30} />
            <Button
              variant="secondary"
              disabled={programId === '' || sessionId === ''}
              aria-disabled={busy || undefined}
              onClick={async () => {
                const done = await save(() =>
                  addStudentProfile(user.accountId, {
                    programId: Number(programId),
                    admissionSessionId: Number(sessionId),
                    rollNumber: rollNumber.trim() === '' ? null : rollNumber.trim(),
                  }),
                )
                if (done) onDone('Student profile created.')
              }}
            >
              Create student profile
            </Button>
          </div>
        )}
        {needsFaculty && (
          <div className="grid gap-3 sm:grid-cols-4 sm:items-end">
            <SelectField className="sm:col-span-3" label="Primary department" value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
              <option value="">Choose a department</option>
              {(departments.status === 'loaded' ? departments.data : [])
                .filter((department) => department.status === 'ACTIVE')
                .map((department) => (
                  <option key={department.id} value={String(department.id)}>
                    {department.code} · {department.name}
                  </option>
                ))}
            </SelectField>
            <Button
              variant="secondary"
              disabled={departmentId === ''}
              aria-disabled={busy || undefined}
              onClick={async () => {
                if (await save(() => addFacultyProfile(user.accountId, { departmentId: Number(departmentId) }))) {
                  onDone('Faculty profile created.')
                }
              }}
            >
              Create faculty profile
            </Button>
          </div>
        )}
        {error && <Alert tone="error">{error}</Alert>}
      </div>
    </Card>
  )
}

function UserAccountView({ accountId }: { accountId: number }) {
  const { account } = useAuth()
  const location = useLocation()
  const [notice, setNotice] = useState<string | null>((location.state as { notice?: string } | null)?.notice ?? null)
  const request = useApiQuery(queryKeys.users.one(accountId), (signal) => getUser(accountId, signal))

  switch (request.status) {
    case 'loading':
      return (
        <StatePanel>
          <LoadingState label="Loading the account…" />
        </StatePanel>
      )
    case 'denied':
      return (
        <StatePanel>
          <AccessDeniedState title={USERS_DENIED} message={SIGN_IN_AGAIN} />
        </StatePanel>
      )
    case 'notFound':
      return (
        <StatePanel>
          <EmptyState title="Account not found." />
        </StatePanel>
      )
    case 'failed':
      return (
        <StatePanel>
          <ErrorState title="Couldn’t load the account." message="Check your connection and try again." onRetry={request.retry} />
        </StatePanel>
      )
    case 'loaded': {
      const user = request.data
      const isSelf = account?.accountId === user.accountId
      return (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <span>{user.loginId}</span>
            {user.roles.map((role) => (
              <Badge key={role} tone="info">
                {roleWord(role)}
              </Badge>
            ))}
            <Badge tone={statusTone(user.status)}>{statusWord(user.status)}</Badge>
            {user.passwordChangeRequired && <Badge tone="warning">Must change password</Badge>}
          </div>
          {notice && (
            <Alert tone="success">
              <span role="status">{notice}</span>
            </Alert>
          )}
          <StatusCard user={user} isSelf={isSelf} onDone={setNotice} />
          <RolesCard key={user.roles.join()} user={user} isSelf={isSelf} onDone={setNotice} />
          <DetailsCard key={`${user.fullName}|${user.email}`} user={user} onDone={setNotice} />
          <PasswordCard user={user} onDone={setNotice} />
          <ProfilesCard user={user} onDone={setNotice} />
        </div>
      )
    }
  }
}

/** /admin/users/:accountId: one account with its status, roles, official details, password, and profiles. */
export function AdminUserPage() {
  const { account } = useAuth()
  const { accountId } = useParams()
  const id = Number(accountId)
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const cached = useQueryClient().getQueryData<UserDetail>(queryKeys.users.one(id))
  return (
    <div className="max-w-4xl">
      <title>User · Stacc</title>
      <PageHeader
        eyebrow="ERP · Manage"
        title={cached?.fullName ?? cached?.loginId ?? 'User'}
        breadcrumb={<Breadcrumb parent={{ to: '/admin/users', label: 'Users' }} current={cached?.loginId} />}
      />
      {!isAdmin ? (
        <StatePanel>
          <AccessDeniedState title="This page is for admin accounts." message="If you think you should, contact the college administration." />
        </StatePanel>
      ) : Number.isInteger(id) && id > 0 ? (
        <UserAccountView accountId={id} />
      ) : (
        <StatePanel>
          <EmptyState title="Account not found." />
        </StatePanel>
      )}
    </div>
  )
}
