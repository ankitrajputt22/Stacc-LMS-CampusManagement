import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'
import { getAcademicSessions, getDepartments, getPrograms } from '../api/adminAcademicApi'
import { createUser } from '../api/adminUsersApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import type { RoleName } from '../types/users'
import { Alert, Button, ButtonLink, Card, PageHeader, SelectField, TextField } from '../ui'
import { checkNewUser, EMPTY, type Errors, type Values } from '../users/newUser'
import { ROLES, refusal, roleWord } from '../users/userText'

function NewUserForm() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const programs = useApiQuery(queryKeys.admin.programs(), getPrograms)
  const sessions = useApiQuery(queryKeys.admin.sessions(), getAcademicSessions)
  const departments = useApiQuery(queryKeys.admin.departments(), getDepartments)
  const [values, setValues] = useState<Values>(EMPTY)
  const [roles, setRoles] = useState<RoleName[]>([])
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)

  const set = (name: keyof Values) => (event: { target: { value: string } }) => {
    setValues((current) => ({ ...current, [name]: event.target.value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
  }

  function toggleRole(role: RoleName, on: boolean) {
    setRoles((current) => (on ? ROLES.filter((value) => value === role || current.includes(value)) : current.filter((value) => value !== role)))
    setErrors((current) => ({ ...current, roles: undefined }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = checkNewUser(values, roles)
    setErrors(found)
    if (Object.keys(found).length > 0 || busy) {
      return
    }
    setBusy(true)
    setFormError(null)
    try {
      const created = await createUser({
        loginId: values.loginId.trim(),
        fullName: values.fullName.trim(),
        email: values.email.trim() === '' ? null : values.email.trim(),
        roles,
        temporaryPassword: values.temporaryPassword,
        studentProfile: roles.includes('STUDENT')
          ? {
              programId: Number(values.programId),
              admissionSessionId: Number(values.admissionSessionId),
              rollNumber: values.rollNumber.trim() === '' ? null : values.rollNumber.trim(),
            }
          : null,
        facultyProfile: roles.includes('FACULTY') ? { departmentId: Number(values.departmentId) } : null,
      })
      await queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
      navigate(`/admin/users/${created.accountId}`, {
        state: { notice: `Account ${created.loginId} created. Give the person the temporary password; they must change it at first sign-in.` },
      })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setFormError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setBusy(false)
    }
  }

  const programList = programs.status === 'loaded' ? programs.data.filter((program) => program.status === 'ACTIVE') : []
  const sessionList = sessions.status === 'loaded' ? sessions.data : []
  const departmentList =
    departments.status === 'loaded' ? departments.data.filter((department) => department.status === 'ACTIVE') : []

  return (
    <form noValidate onSubmit={submit} className="space-y-4">
      <Card title="Account" description="The login ID is the student or employee ID and never changes. There is no public signup.">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Login ID" value={values.loginId} onChange={set('loginId')} error={errors.loginId} maxLength={100} autoComplete="off" />
          <TextField label="Full name" value={values.fullName} onChange={set('fullName')} error={errors.fullName} maxLength={150} autoComplete="off" />
          <TextField
            label="Email (optional)"
            type="email"
            value={values.email}
            onChange={set('email')}
            error={errors.email}
            maxLength={254}
            autoComplete="off"
          />
          <div>
            <TextField
              label="Temporary password"
              type={showPassword ? 'text' : 'password'}
              value={values.temporaryPassword}
              onChange={set('temporaryPassword')}
              error={errors.temporaryPassword}
              hint="10 to 128 characters, not a common password, and not containing the login ID. It expires in a few days, and the person must change it at first sign-in."
              maxLength={128}
              autoComplete="new-password"
            />
            <label className="mt-2 flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} />
              Show password
            </label>
          </div>
        </div>
        <fieldset className="mt-4" aria-describedby={errors.roles ? 'roles-error' : undefined}>
          <legend className="text-sm font-medium text-ink">Roles</legend>
          <div className="mt-2 flex flex-wrap gap-4">
            {ROLES.map((role) => (
              <label key={role} className="flex items-center gap-2 text-sm text-ink">
                <input type="checkbox" checked={roles.includes(role)} onChange={(event) => toggleRole(role, event.target.checked)} />
                {roleWord(role)}
              </label>
            ))}
          </div>
          {errors.roles && (
            <p id="roles-error" className="mt-1.5 text-sm text-danger">
              {errors.roles}
            </p>
          )}
        </fieldset>
      </Card>

      {roles.includes('STUDENT') && (
        <Card title="Student profile" description="The program and the session of original admission. Enrollment is added later, under Academic Admin.">
          <div className="grid gap-4 sm:grid-cols-3">
            <SelectField label="Program" value={values.programId} onChange={set('programId')} error={errors.programId}>
              <option value="">Choose a program</option>
              {programList.map((program) => (
                <option key={program.id} value={String(program.id)}>
                  {program.code} · {program.name}
                </option>
              ))}
            </SelectField>
            <SelectField label="Admission session" value={values.admissionSessionId} onChange={set('admissionSessionId')} error={errors.admissionSessionId}>
              <option value="">Choose a session</option>
              {sessionList.map((session) => (
                <option key={session.id} value={String(session.id)}>
                  {session.code}
                </option>
              ))}
            </SelectField>
            <TextField label="Roll number (optional)" value={values.rollNumber} onChange={set('rollNumber')} maxLength={30} autoComplete="off" />
          </div>
        </Card>
      )}

      {roles.includes('FACULTY') && (
        <Card title="Faculty profile" description="The primary department. It never limits what the person teaches.">
          <SelectField label="Primary department" value={values.departmentId} onChange={set('departmentId')} error={errors.departmentId}>
            <option value="">Choose a department</option>
            {departmentList.map((department) => (
              <option key={department.id} value={String(department.id)}>
                {department.code} · {department.name}
              </option>
            ))}
          </SelectField>
        </Card>
      )}

      {formError && <Alert tone="error">{formError}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Creating account…' : 'Create account'}
        </Button>
        <ButtonLink to="/admin/users" variant="secondary">
          Cancel
        </ButtonLink>
      </div>
    </form>
  )
}

/** /admin/users/new: the college creates an account, its roles, a temporary password, and the profiles it needs. */
export function AdminUserCreatePage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  return (
    <div className="max-w-4xl">
      <title>Add user · Stacc</title>
      <PageHeader eyebrow="ERP · Manage" title="Add user" breadcrumb={<Breadcrumb parent={{ to: '/admin/users', label: 'Users' }} current="Add user" />} />
      {isAdmin ? (
        <NewUserForm />
      ) : (
        <StatePanel>
          <AccessDeniedState title="This page is for admin accounts." message="If you think you should, contact the college administration." />
        </StatePanel>
      )}
    </div>
  )
}
