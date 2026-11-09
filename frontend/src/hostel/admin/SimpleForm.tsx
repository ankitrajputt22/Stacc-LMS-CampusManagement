import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { Button } from '../../components/Button'
import { border, fieldClasses } from '../../teaching/formStyles'
import { refusalText } from '../hostelText'

export type SimpleField = {
  name: string
  label: string
  kind: 'text' | 'textarea' | 'checkbox'
  maxLength?: number
  required?: boolean
  /** Shown but not editable, such as a code fixed after creation. */
  readOnly?: boolean
  hint?: string
  /** For a checkbox: a note shown while it is unticked after being ticked, such as what deactivating means. */
  offNote?: string
}

export type SimpleValues = Record<string, string | boolean>

type SimpleFormProps = {
  heading: string
  fields: SimpleField[]
  initial: SimpleValues
  submitLabel: string
  onSubmit: (values: SimpleValues) => Promise<void>
  onCancel: () => void
}

/**
 * A small contained form for one hostel, room, or bed. Every field has a real label; required text is checked before
 * sending and the backend checks everything again. A refusal, such as a taken number or an occupied bed (409), is shown
 * in the backend's words and keeps what was entered. A 403 never signs the user out.
 */
export function SimpleForm({ heading, fields, initial, submitLabel, onSubmit, onCancel }: SimpleFormProps) {
  const id = useId()
  const formRef = useRef<HTMLFormElement>(null)
  const busyRef = useRef(false)
  const [values, setValues] = useState<SimpleValues>(initial)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('input:not([readonly]), textarea')?.focus()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) {
      return
    }
    const missing = fields.find((field) => field.required && !field.readOnly && String(values[field.name] ?? '').trim() === '')
    if (missing) {
      setError(`${missing.label} is required.`)
      document.getElementById(`${id}-${missing.name}`)?.focus()
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      await onSubmit(values)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusalText(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate aria-labelledby={`${id}-heading`} className="rounded-panel border border-line-strong bg-surface p-4">
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        {heading}
      </p>
      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        {fields.map((field) => {
          const fieldId = `${id}-${field.name}`
          const hintId = field.hint ? `${fieldId}-hint` : undefined
          if (field.kind === 'checkbox') {
            const checked = values[field.name] === true
            return (
              <div key={field.name} className="sm:col-span-2">
                <label className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-ink sm:min-h-8">
                  <input
                    id={fieldId}
                    type="checkbox"
                    checked={checked}
                    aria-describedby={!checked && initial[field.name] === true && field.offNote ? `${fieldId}-off` : undefined}
                    onChange={(event) => setValues((current) => ({ ...current, [field.name]: event.target.checked }))}
                    className="size-4 accent-stacc-primary"
                  />
                  {field.label}
                </label>
                {!checked && initial[field.name] === true && field.offNote && (
                  <p id={`${fieldId}-off`} className="mt-1 text-sm text-ink-muted">
                    {field.offNote}
                  </p>
                )}
              </div>
            )
          }
          const common = {
            id: fieldId,
            value: String(values[field.name] ?? ''),
            maxLength: field.maxLength,
            readOnly: field.readOnly,
            'aria-describedby': hintId,
            onChange: (event: { target: { value: string } }) =>
              setValues((current) => ({ ...current, [field.name]: event.target.value })),
          }
          return (
            <div key={field.name} className={field.kind === 'textarea' ? 'sm:col-span-2' : ''}>
              <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
                {field.label}
                {!field.required && !field.readOnly && <span className="font-normal text-ink-muted"> (optional)</span>}
              </label>
              {field.hint && (
                <p id={hintId} className="mt-0.5 text-xs text-ink-muted">
                  {field.hint}
                </p>
              )}
              {field.kind === 'textarea' ? (
                <textarea {...common} rows={3} className={`py-2 ${fieldClasses} ${border(false)}`} />
              ) : (
                <input
                  {...common}
                  type="text"
                  autoComplete="off"
                  className={`h-11 ${fieldClasses} ${border(false)} ${field.readOnly ? 'bg-surface-muted text-ink-muted' : ''}`}
                />
              )}
            </div>
          )
        })}
      </div>
      <div role="alert">
        {error && <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error}</p>}
      </div>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
