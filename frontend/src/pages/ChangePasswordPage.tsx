import { type FormEvent, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { changePassword } from '../api/authApi'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, PageHeader, TextField } from '../ui'

/** The same limits the backend's password policy uses; the backend still checks everything. */
export const PASSWORD_MIN_LENGTH = 10
export const PASSWORD_MAX_LENGTH = 128

type Field = 'currentPassword' | 'newPassword' | 'confirmPassword'
type Values = Record<Field, string>
type Errors = Partial<Record<Field, string>>

const FIELD_ORDER: Field[] = ['currentPassword', 'newPassword', 'confirmPassword']
const EMPTY: Values = { currentPassword: '', newPassword: '', confirmPassword: '' }

// Characters, not UTF-16 units, so the count matches what the backend counts.
const lengthOf = (text: string) => [...text].length

function checked(values: Values): Errors {
  const errors: Errors = {}
  if (values.currentPassword === '') errors.currentPassword = 'Enter your current password.'
  const length = lengthOf(values.newPassword)
  if (values.newPassword.trim() === '') errors.newPassword = 'Choose a new password.'
  else if (length < PASSWORD_MIN_LENGTH) errors.newPassword = `Use at least ${PASSWORD_MIN_LENGTH} characters.`
  else if (length > PASSWORD_MAX_LENGTH) errors.newPassword = `Use at most ${PASSWORD_MAX_LENGTH} characters.`
  if (values.confirmPassword !== values.newPassword) errors.confirmPassword = 'The two new passwords do not match.'
  return errors
}

type ChangePasswordPageProps = {
  /** The college requires a new password: the page stands alone, and signing out is the only other way on. */
  required?: boolean
}

/**
 * Change password, for the signed-in person. Passwords are sent exactly as typed and kept only in this form; they
 * are cleared once the change is saved.
 */
export function ChangePasswordPage({ required = false }: ChangePasswordPageProps) {
  const { passwordChanged, logout } = useAuth()
  const submittingRef = useRef(false)
  const [values, setValues] = useState<Values>(EMPTY)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
    setSaved(false)
  }

  function focusFirst(found: Errors) {
    const first = FIELD_ORDER.find((field) => found[field])
    if (first) document.getElementById(`change-password-${first}`)?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found = checked(values)
    setErrors(found)
    setFormError(null)
    if (Object.values(found).some(Boolean)) {
      focusFirst(found)
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    try {
      await changePassword(values.currentPassword, values.newPassword)
      setValues(EMPTY)
      setSaved(true)
      // After a required change the rest of the app opens again, at the page that was asked for.
      passwordChanged()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status === 401) {
        // The session has ended; the app is already on its way to the sign-in page.
        return
      }
      const message = getApiError(failure)?.message
      if (status === 400 && message) {
        const field: Field = message.includes('current password') ? 'currentPassword' : 'newPassword'
        setErrors({ [field]: message })
        focusFirst({ [field]: message })
      } else {
        setFormError('Your password could not be changed. Try again.')
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <div className="max-w-lg">
      <PageHeader
        title="Change password"
        description={
          required
            ? 'The college has asked you to choose a new password before you continue.'
            : 'Choose a new password. Every other device signed in to your account will be signed out.'
        }
      />
      {saved && (
        <Alert tone="success" title="Password changed." className="mb-4">
          Other devices were signed out. This one stays signed in.
        </Alert>
      )}
      {formError && (
        <Alert tone="error" className="mb-4">
          {formError}
        </Alert>
      )}
      <Card>
        <form onSubmit={submit} noValidate aria-label="Change password" className="grid gap-4">
          <TextField
            id="change-password-currentPassword"
            label={required ? 'Temporary password' : 'Current password'}
            type="password"
            autoComplete="current-password"
            required
            value={values.currentPassword}
            error={errors.currentPassword}
            onChange={(event) => change('currentPassword', event.target.value)}
          />
          <TextField
            id="change-password-newPassword"
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            maxLength={PASSWORD_MAX_LENGTH * 2}
            hint={`At least ${PASSWORD_MIN_LENGTH} characters, without your login ID. A short phrase is easy to remember.`}
            value={values.newPassword}
            error={errors.newPassword}
            onChange={(event) => change('newPassword', event.target.value)}
          />
          <TextField
            id="change-password-confirmPassword"
            label="New password again"
            type="password"
            autoComplete="new-password"
            required
            value={values.confirmPassword}
            error={errors.confirmPassword}
            onChange={(event) => change('confirmPassword', event.target.value)}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Changing…' : 'Change password'}
            </Button>
            {required && (
              <Button variant="secondary" onClick={() => void logout()}>
                Sign out
              </Button>
            )}
          </div>
        </form>
      </Card>
    </div>
  )
}
