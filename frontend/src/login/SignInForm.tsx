import { isAxiosError } from 'axios'
import { useRef, useState, type FormEvent } from 'react'
import { getApiError } from '../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { EyeIcon, EyeOffIcon } from './PortalIcons'

// The same text for a wrong password, an unknown ID, and a disabled account, like the backend.
const INVALID_CREDENTIALS = 'Invalid login ID or password.'
const CONNECTION_PROBLEM = 'Unable to connect to Stacc. Please try again.'
const SERVER_PROBLEM = 'Stacc could not sign you in right now. Please try again.'
const REJECTED_INPUT = 'Check your college ID and password, then try again.'
// The same text whoever is slowed down, like the backend: it never says whether the ID exists.
const TOO_MANY_ATTEMPTS = 'Too many sign-in attempts. Wait a few minutes and try again.'
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
  if (error.response.status === 429) {
    return { message: TOO_MANY_ATTEMPTS, fields: {} }
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

// Dark fields on the portal's navy background; the label stays for screen readers, the placeholder says the same.
const inputClasses =
  'block h-16 w-full border bg-portal-navy px-6 text-base text-white placeholder:text-white/80 placeholder:italic focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-portal-cta'

function inputBorder(hasError: boolean): string {
  return hasError ? 'border-red-300' : 'border-portal-navy hover:border-white/40'
}

const fieldErrorClasses = 'mt-2 text-sm font-medium text-red-200'

/**
 * The one Stacc sign-in form: a college ID or employee ID and a password, sent to the one login endpoint. It never
 * chooses or sends a role. After a successful sign-in the page moves on, because the account is then signed in.
 */
export function SignInForm({ autoFocus = true }: { autoFocus?: boolean }) {
  const { login } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const loginIdRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

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
    <form onSubmit={handleSubmit} noValidate className="mt-8">
      <div>
        <label htmlFor="login-id" className="sr-only">
          College ID / Employee ID
        </label>
        <input
          ref={loginIdRef}
          id="login-id"
          name="loginId"
          type="text"
          placeholder="Enter your college ID or employee ID"
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          autoFocus={autoFocus}
          required
          value={loginId}
          onChange={(event) => setLoginId(event.target.value)}
          aria-invalid={fieldErrors.loginId ? true : undefined}
          aria-describedby={fieldErrors.loginId ? 'login-id-error' : undefined}
          className={`${inputClasses} ${inputBorder(Boolean(fieldErrors.loginId))}`}
        />
        {fieldErrors.loginId && (
          <p id="login-id-error" className={fieldErrorClasses}>
            {fieldErrors.loginId}
          </p>
        )}
      </div>

      <div className="mt-5">
        <label htmlFor="password" className="sr-only">
          Password
        </label>
        <div className="relative">
          <input
            ref={passwordRef}
            id="password"
            name="password"
            type={showPassword ? 'text' : 'password'}
            placeholder="Enter your password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            className={`${inputClasses} pr-16 ${inputBorder(Boolean(fieldErrors.password))}`}
          />
          <button
            type="button"
            onClick={() => setShowPassword((shown) => !shown)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            aria-controls="password"
            className="absolute inset-y-0 right-0 flex w-16 items-center justify-center text-white/90 hover:text-white focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-white"
          >
            {showPassword ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        </div>
        {fieldErrors.password && (
          <p id="password-error" className={fieldErrorClasses}>
            {fieldErrors.password}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-[0.95rem] text-white">
        <span>One account for ERP and LMS</span>
        <a
          href="#password-help"
          className="inline-flex min-h-11 items-center font-medium underline underline-offset-4 hover:text-portal-bar-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white sm:min-h-0"
        >
          Forgot password
        </a>
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="mx-auto mt-4 flex h-16 min-w-40 items-center justify-center bg-portal-cta px-8 text-base font-semibold text-white shadow-sm hover:bg-portal-cta-hover focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white disabled:cursor-wait disabled:bg-portal-cta-hover"
      >
        {isSubmitting ? 'Signing in…' : 'Sign in'}
      </button>

      {/* Always present, so screen readers announce a message the moment it appears. */}
      <div role="alert">
        {formError && (
          <p className="mt-5 border-l-4 border-danger bg-white px-4 py-3 text-sm leading-5 text-danger">
            <span className="font-semibold">Sign-in failed. </span>
            {formError}
          </p>
        )}
      </div>
    </form>
  )
}
