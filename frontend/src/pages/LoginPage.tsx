import { isAxiosError } from 'axios'
import { useRef, useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { getApiError } from '../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { LOGIN_PATH } from '../auth/RequireAuth'
import { Button } from '../components/Button'
import { Brand } from '../components/navigation/Brand'

const HOME_PATH = '/'

// The same text for a wrong password, an unknown ID, and a disabled account, like the backend.
const INVALID_CREDENTIALS = 'Invalid login ID or password.'
const CONNECTION_PROBLEM = 'Unable to connect to Stacc. Please try again.'
const SERVER_PROBLEM = 'Stacc could not sign you in right now. Please try again.'
const REJECTED_INPUT = 'Check your college ID and password, then try again.'
const LOGIN_ID_REQUIRED = 'Enter your college ID or employee ID.'
const PASSWORD_REQUIRED = 'Enter your password.'

type FieldErrors = {
  loginId?: string
  password?: string
}

type Failure = {
  message: string
  fields: FieldErrors
}

// Turns a failed login into text that is safe and useful to show. Nothing raw is displayed.
function describeFailure(error: unknown): Failure {
  if (!isAxiosError(error)) {
    return { message: SERVER_PROBLEM, fields: {} }
  }
  if (!error.response) {
    return { message: CONNECTION_PROBLEM, fields: {} }
  }
  if (error.response.status === 401) {
    return { message: INVALID_CREDENTIALS, fields: {} }
  }
  if (error.response.status === 400) {
    const fields: FieldErrors = {}

    for (const fieldError of getApiError(error)?.fieldErrors ?? []) {
      if (fieldError.field === 'loginId' || fieldError.field === 'password') {
        fields[fieldError.field] ??= fieldError.message
      }
    }

    const hasFieldErrors = Object.keys(fields).length > 0

    return { message: hasFieldErrors ? '' : REJECTED_INPUT, fields }
  }

  return { message: SERVER_PROBLEM, fields: {} }
}

// Where to go after signing in: the page the visitor first asked for, if it is a page of this app.
function destinationFrom(state: unknown): string {
  const from =
    typeof state === 'object' && state !== null
      ? (state as Record<string, unknown>).from
      : undefined

  if (typeof from !== 'string' || !from.startsWith('/')) {
    return HOME_PATH
  }

  const target = new URL(from, window.location.origin)

  if (target.origin !== window.location.origin || target.pathname === LOGIN_PATH) {
    return HOME_PATH
  }

  return target.pathname + target.search + target.hash
}

const inputClasses =
  'mt-1.5 block h-11 w-full rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:text-sm'

function inputBorder(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const location = useLocation()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const loginIdRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  // Covers both a visitor who is already signed in and the moment a login succeeds.
  if (isAuthenticated) {
    return <Navigate to={destinationFrom(location.state)} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (isSubmitting) {
      return
    }

    // Only "is anything there" is checked here. The backend has the real rules.
    const missing: FieldErrors = {}
    if (loginId.trim() === '') {
      missing.loginId = LOGIN_ID_REQUIRED
    }
    if (password === '') {
      missing.password = PASSWORD_REQUIRED
    }

    setFieldErrors(missing)
    setFormError('')

    if (missing.loginId) {
      loginIdRef.current?.focus()
      return
    }
    if (missing.password) {
      passwordRef.current?.focus()
      return
    }

    setIsSubmitting(true)

    try {
      // Both values go to the backend exactly as typed.
      await login(loginId, password)
    } catch (error) {
      const failure = describeFailure(error)

      setFieldErrors(failure.fields)
      setFormError(failure.message)
      setIsSubmitting(false)

      if (failure.message === INVALID_CREDENTIALS) {
        setPassword('')
      }

      const fieldToFix = failure.fields.loginId ? loginIdRef : passwordRef
      fieldToFix.current?.focus()
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-surface sm:bg-canvas">
      <title>Sign in · Stacc</title>

      <header className="px-4 py-4 sm:px-6 lg:px-8">
        <Brand asLink={false} />
      </header>

      <main className="flex flex-1 justify-center px-4 pt-4 pb-12 sm:px-6 sm:pt-12">
        <div className="w-full max-w-[26rem]">
          <div className="sm:rounded-panel sm:border sm:border-line sm:bg-surface sm:p-8">
            <h1 className="text-xl font-semibold tracking-tight text-ink">
              Sign in to Stacc
            </h1>
            <p className="mt-1.5 text-sm leading-6 text-ink-muted">
              Use your college account to continue.
            </p>

            <form onSubmit={handleSubmit} noValidate className="mt-6">
              <div>
                <label
                  htmlFor="login-id"
                  className="block text-sm font-medium text-ink"
                >
                  College ID / Employee ID
                </label>
                <input
                  ref={loginIdRef}
                  id="login-id"
                  name="loginId"
                  type="text"
                  autoComplete="username"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  autoFocus
                  required
                  value={loginId}
                  onChange={(event) => setLoginId(event.target.value)}
                  aria-invalid={fieldErrors.loginId ? true : undefined}
                  aria-describedby={
                    fieldErrors.loginId ? 'login-id-error' : undefined
                  }
                  className={`${inputClasses} ${inputBorder(Boolean(fieldErrors.loginId))}`}
                />
                {fieldErrors.loginId && (
                  <p id="login-id-error" className="mt-1.5 text-sm text-danger">
                    {fieldErrors.loginId}
                  </p>
                )}
              </div>

              <div className="mt-4">
                <label
                  htmlFor="password"
                  className="block text-sm font-medium text-ink"
                >
                  Password
                </label>
                <input
                  ref={passwordRef}
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={fieldErrors.password ? true : undefined}
                  aria-describedby={
                    fieldErrors.password ? 'password-error' : undefined
                  }
                  className={`${inputClasses} ${inputBorder(Boolean(fieldErrors.password))}`}
                />
                {fieldErrors.password && (
                  <p id="password-error" className="mt-1.5 text-sm text-danger">
                    {fieldErrors.password}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                fullWidth
                disabled={isSubmitting}
                className="mt-6 disabled:cursor-wait disabled:bg-stacc-primary-hover"
              >
                {isSubmitting ? 'Signing in…' : 'Sign in'}
              </Button>

              {/* Always present, so screen readers announce a message the moment it appears. */}
              <div role="alert">
                {formError && (
                  <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
                    <span className="font-semibold">Sign-in failed. </span>
                    {formError}
                  </p>
                )}
              </div>
            </form>
          </div>

          <p className="mt-5 text-sm leading-6 text-ink-muted sm:px-8">
            Accounts are issued by your college. If you cannot sign in, contact
            the college administration.
          </p>
        </div>
      </main>
    </div>
  )
}
