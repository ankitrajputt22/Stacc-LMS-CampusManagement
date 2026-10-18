import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'

export type FieldOption = { value: string; label: string }

/** One field of a master form, mirroring the backend's request field of the same name. */
export type FieldSpec = {
  name: string
  label: string
  kind: 'text' | 'integer' | 'decimal' | 'date' | 'select'
  hint?: string
  maxLength?: number
  /** Smallest allowed value, inclusive. */
  min?: number
  /** Values must be above this, such as credits above zero. */
  above?: number
  max?: number
  options?: FieldOption[]
  /** The first, empty choice of a select, such as "Choose a department". */
  choose?: string
}

export type Values = Record<string, string>

type MasterFormProps = {
  heading: string
  /** A short neutral note under the heading, such as what cannot change. */
  note?: string
  fields: FieldSpec[]
  initial: Values
  submitLabel: string
  /** Rules across fields, such as an end date after the start date. */
  validate?: (values: Values) => Partial<Record<string, string>>
  /** Sends the values. A refusal is thrown and shown here; what was entered stays. */
  onSubmit: (values: Values) => Promise<void>
  onCancel: () => void
  /** What a 403 says, such as "You don’t have access to academic enrollment administration." */
  deniedMessage?: string
}

const DECIMAL_PATTERN = /^\d{1,2}(\.\d{1,2})?$/
const INTEGER_PATTERN = /^\d{1,3}$/

function checked(fields: FieldSpec[], values: Values): Partial<Record<string, string>> {
  const errors: Partial<Record<string, string>> = {}
  for (const field of fields) {
    const value = values[field.name]?.trim() ?? ''
    if (value === '') {
      errors[field.name] = field.kind === 'select' ? `Choose ${field.label.toLowerCase()}.` : `Enter ${field.label.toLowerCase()}.`
      continue
    }
    if (field.maxLength !== undefined && value.length > field.maxLength) {
      errors[field.name] = `Use at most ${field.maxLength} characters.`
    } else if (field.kind === 'integer') {
      const number = Number(value)
      if (!INTEGER_PATTERN.test(value) || (field.min !== undefined && number < field.min) || (field.max !== undefined && number > field.max)) {
        errors[field.name] = `Enter a whole number from ${field.min ?? 0} to ${field.max}.`
      }
    } else if (field.kind === 'decimal') {
      const number = Number(value)
      if (!DECIMAL_PATTERN.test(value) || (field.above !== undefined && number <= field.above) || (field.max !== undefined && number > field.max)) {
        errors[field.name] = `Enter a number above ${field.above ?? 0} and at most ${field.max}, with at most 2 decimal places.`
      }
    }
  }
  return errors
}

function refusal(status: number | null, apiMessage: string | null, deniedMessage: string): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? 'Couldn’t save. Check the values and try again.'
    case 403:
      return `${deniedMessage} If your access changed recently, sign out and sign in again.`
    default:
      return 'Couldn’t save. Check your connection and try again. What you entered is still here.'
  }
}

/**
 * A small contained form for one academic master. Every field has a real label; obvious mistakes are caught before
 * sending, mirroring the backend's rules, and the backend checks everything again. While it sends, the button says
 * "Saving…" and a second press does nothing. A refusal is shown in safe words and keeps what was entered; a 403 never
 * signs the user out.
 */
export function MasterForm({
  heading,
  note,
  fields,
  initial,
  submitLabel,
  validate,
  onSubmit,
  onCancel,
  deniedMessage = 'You don’t have access to academic administration.',
}: MasterFormProps) {
  const id = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const submittingRef = useRef(false)
  const [values, setValues] = useState<Values>(initial)
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('input, select')?.focus()
  }, [])

  function focusFirst(found: Partial<Record<string, string>>) {
    const first = fields.find((field) => found[field.name])
    if (first) {
      document.getElementById(`${id}-${first.name}`)?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found = { ...checked(fields, values) }
    for (const [name, message] of Object.entries(validate?.(values) ?? {})) {
      if (message && !found[name]) {
        found[name] = message
      }
    }
    setFormError(null)
    setErrors(found)
    if (Object.values(found).some(Boolean)) {
      focusFirst(found)
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    try {
      await onSubmit(values)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        // A 401 is left to the shared client, which ends the session.
        const apiError = getApiError(failure)
        const fieldErrors = Object.fromEntries((apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]))
        const hasFieldErrors = status === 400 && Object.keys(fieldErrors).length > 0
        setErrors(fieldErrors)
        setFormError(hasFieldErrors ? 'Check the highlighted field.' : refusal(status, apiError?.message ?? null, deniedMessage))
        focusFirst(fieldErrors)
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <form
      ref={formRef}
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        {heading}
      </p>
      {note && <p className="mt-1 text-sm leading-6 text-ink-muted">{note}</p>}

      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        {fields.map((field) => {
          const fieldId = `${id}-${field.name}`
          const hintId = field.hint ? `${fieldId}-hint` : undefined
          const errorId = errors[field.name] ? `${fieldId}-error` : undefined
          const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
          const common = {
            id: fieldId,
            value: values[field.name] ?? '',
            'aria-invalid': errors[field.name] ? true : undefined,
            'aria-describedby': describedBy,
          }
          function change(value: string) {
            setValues((current) => ({ ...current, [field.name]: value }))
            setErrors((current) => ({ ...current, [field.name]: undefined }))
          }
          return (
            <div key={field.name} className={field.kind === 'text' && (field.maxLength ?? 0) > 50 ? 'sm:col-span-2' : ''}>
              <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
                {field.label}
              </label>
              {field.hint && (
                <p id={hintId} className="mt-0.5 text-xs text-ink-muted">
                  {field.hint}
                </p>
              )}
              {field.kind === 'select' ? (
                <select
                  {...common}
                  onChange={(event) => change(event.target.value)}
                  className={`h-11 ${fieldClasses} ${border(Boolean(errors[field.name]))}`}
                >
                  {field.choose && <option value="">{field.choose}</option>}
                  {field.options?.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  {...common}
                  type={field.kind === 'date' ? 'date' : 'text'}
                  inputMode={field.kind === 'integer' ? 'numeric' : field.kind === 'decimal' ? 'decimal' : undefined}
                  autoComplete="off"
                  maxLength={field.maxLength}
                  onChange={(event) => change(event.target.value)}
                  className={`h-11 ${fieldClasses} ${border(Boolean(errors[field.name]))}`}
                />
              )}
              {errors[field.name] && (
                <p id={errorId} className="mt-1.5 text-sm text-danger">
                  {errors[field.name]}
                </p>
              )}
            </div>
          )
        })}
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {formError}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={submitting || undefined}>
          {submitting ? 'Saving…' : submitLabel}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
