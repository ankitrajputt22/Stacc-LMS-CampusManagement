import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AdminHelpdeskCategory } from '../types/helpdesk'
import { CATEGORY_CODE_MAX, CATEGORY_DESCRIPTION_MAX, CATEGORY_NAME_MAX, refusalText } from './helpdeskText'

export type CategoryValues = { code: string; name: string; description: string; active: boolean }

type Field = 'code' | 'name' | 'description'

type CategoryFormProps = {
  /** The category being edited, or null for a new one. */
  editing: AdminHelpdeskCategory | null
  onSubmit: (values: CategoryValues) => Promise<void>
  onCancel: () => void
}

const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]*$/

function checked(values: CategoryValues, creating: boolean): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {}
  const code = values.code.trim()
  if (creating) {
    if (code === '') errors.code = 'Code is required.'
    else if (code.length > CATEGORY_CODE_MAX) errors.code = `Use at most ${CATEGORY_CODE_MAX} characters.`
    else if (!CODE_PATTERN.test(code)) errors.code = 'Use letters, digits, and underscores, starting with a letter.'
  }
  if (values.name.trim() === '') errors.name = 'Name is required.'
  else if (values.name.trim().length > CATEGORY_NAME_MAX) errors.name = `Use at most ${CATEGORY_NAME_MAX} characters.`
  if (values.description.trim().length > CATEGORY_DESCRIPTION_MAX) errors.description = `Use at most ${CATEGORY_DESCRIPTION_MAX} characters.`
  return errors
}

/**
 * Adds a helpdesk category or edits one. A new category is active; its code is set once and stored in uppercase by the
 * backend, so when editing the code is shown but cannot be changed. A refusal, such as a taken code (409), is shown in
 * the backend's words and keeps what was entered.
 */
export function CategoryForm({ editing, onSubmit, onCancel }: CategoryFormProps) {
  const id = useId()
  const creating = editing === null
  const formRef = useRef<HTMLFormElement>(null)
  const submittingRef = useRef(false)
  const [values, setValues] = useState<CategoryValues>({
    code: editing?.code ?? '',
    name: editing?.name ?? '',
    description: editing?.description ?? '',
    active: editing?.active ?? true,
  })
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('input:not([readonly]), textarea')?.focus()
  }, [])

  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const found = checked(values, creating)
    setErrors(found)
    setFormError(null)
    const first = (['code', 'name', 'description'] as Field[]).find((field) => found[field])
    if (first) {
      document.getElementById(`${id}-${first}`)?.focus()
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    try {
      await onSubmit(values)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const apiError = getApiError(failure)
        const fieldErrors: Partial<Record<Field, string>> = {}
        for (const error of apiError?.fieldErrors ?? []) {
          if (['code', 'name', 'description'].includes(error.field)) fieldErrors[error.field as Field] = error.message
        }
        if (status === 409) fieldErrors.code = apiError?.message ?? 'A category with this code already exists.'
        setErrors(fieldErrors)
        setFormError(Object.keys(fieldErrors).length > 0 ? 'Check the highlighted field.' : refusalText(status, apiError?.message ?? null, 'save the category'))
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  const describedBy = (field: Field, hint: boolean) =>
    [hint ? `${id}-${field}-hint` : '', errors[field] ? `${id}-${field}-error` : ''].filter(Boolean).join(' ') || undefined
  const errorText = (field: Field) =>
    errors[field] && (
      <p id={`${id}-${field}-error`} className="mt-1.5 text-sm text-danger">
        {errors[field]}
      </p>
    )
  const deactivating = editing !== null && editing.active && !values.active

  return (
    <form
      ref={formRef}
      onSubmit={submit}
      noValidate
      aria-labelledby={`${id}-heading`}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        {creating ? 'Add category' : `Edit ${editing.name}`}
      </p>
      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={`${id}-code`} className="block text-sm font-medium text-ink">
            Code
          </label>
          <p id={`${id}-code-hint`} className="mt-0.5 text-xs text-ink-muted">
            {creating ? 'Stored in uppercase, such as HOSTEL. It can’t be changed later.' : 'The code can’t be changed.'}
          </p>
          <input
            id={`${id}-code`}
            type="text"
            autoComplete="off"
            readOnly={!creating}
            maxLength={CATEGORY_CODE_MAX}
            value={values.code}
            aria-invalid={errors.code ? true : undefined}
            aria-describedby={describedBy('code', true)}
            onChange={(event) => change('code', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.code))} ${creating ? '' : 'bg-surface-muted text-ink-muted'}`}
          />
          {errorText('code')}
        </div>
        <div>
          <label htmlFor={`${id}-name`} className="block text-sm font-medium text-ink">
            Name
          </label>
          <input
            id={`${id}-name`}
            type="text"
            autoComplete="off"
            maxLength={CATEGORY_NAME_MAX}
            value={values.name}
            aria-invalid={errors.name ? true : undefined}
            aria-describedby={describedBy('name', false)}
            onChange={(event) => change('name', event.target.value)}
            className={`h-11 ${fieldClasses} ${border(Boolean(errors.name))}`}
          />
          {errorText('name')}
        </div>
        <div className="sm:col-span-2">
          <label htmlFor={`${id}-description`} className="block text-sm font-medium text-ink">
            Description <span className="font-normal text-ink-muted">(optional)</span>
          </label>
          <textarea
            id={`${id}-description`}
            rows={3}
            maxLength={CATEGORY_DESCRIPTION_MAX}
            value={values.description}
            aria-invalid={errors.description ? true : undefined}
            aria-describedby={describedBy('description', false)}
            onChange={(event) => change('description', event.target.value)}
            className={`py-2 ${fieldClasses} ${border(Boolean(errors.description))}`}
          />
          {errorText('description')}
        </div>
        {!creating && (
          <div className="sm:col-span-2">
            <label className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-ink sm:min-h-8">
              <input
                type="checkbox"
                checked={values.active}
                aria-describedby={deactivating ? `${id}-deactivate` : undefined}
                onChange={(event) => setValues((current) => ({ ...current, active: event.target.checked }))}
                className="size-4 accent-stacc-primary"
              />
              Active: can be chosen for new tickets
            </label>
            {deactivating && (
              <p id={`${id}-deactivate`} className="mt-1 text-sm text-ink-muted">
                Existing tickets keep this category, but it cannot be selected for new tickets.
              </p>
            )}
          </div>
        )}
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">{formError}</p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={submitting || undefined}>
          {submitting ? 'Saving…' : creating ? 'Add category' : 'Save changes'}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
