import { type InputHTMLAttributes, type ReactNode, type Ref, type SelectHTMLAttributes, type TextareaHTMLAttributes, useId } from 'react'
import { controlBorder, controlClasses } from './styles'

type FieldLabelProps = {
  /** The visible label. Every field has one. */
  label: ReactNode
  /** A short help line under the field. */
  hint?: ReactNode
  /** The message of a field that needs fixing. The field is then marked invalid and the message read with it. */
  error?: string | null
  className?: string
}

/** The label, hint, and error around one control, with their ids wired to it. */
function FieldFrame({ id, label, hint, error, className = '', children }: FieldLabelProps & { id: string; children: ReactNode }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      {children}
      {hint && (
        <p id={`${id}-hint`} className="mt-1 text-sm leading-5 text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

function describedBy(id: string, hint: unknown, error: unknown): string | undefined {
  const ids = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ')
  return ids || undefined
}

type TextFieldProps = FieldLabelProps & Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> & { ref?: Ref<HTMLInputElement> }

export function TextField({ label, hint, error, className, id: givenId, ...input }: TextFieldProps) {
  const generated = useId()
  const id = givenId ?? generated
  return (
    <FieldFrame id={id} label={label} hint={hint} error={error} className={className}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={`${controlClasses} ${controlBorder(Boolean(error))} h-11 sm:h-9`}
        {...input}
      />
    </FieldFrame>
  )
}

type TextAreaFieldProps = FieldLabelProps &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> & { ref?: Ref<HTMLTextAreaElement> }

export function TextAreaField({ label, hint, error, className, id: givenId, rows = 4, ...textarea }: TextAreaFieldProps) {
  const generated = useId()
  const id = givenId ?? generated
  return (
    <FieldFrame id={id} label={label} hint={hint} error={error} className={className}>
      <textarea
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={`${controlClasses} ${controlBorder(Boolean(error))} py-2 leading-6`}
        {...textarea}
      />
    </FieldFrame>
  )
}

type SelectFieldProps = FieldLabelProps &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> & { ref?: Ref<HTMLSelectElement>; children: ReactNode }

export function SelectField({ label, hint, error, className, id: givenId, children, ...select }: SelectFieldProps) {
  const generated = useId()
  const id = givenId ?? generated
  return (
    <FieldFrame id={id} label={label} hint={hint} error={error} className={className}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className={`${controlClasses} ${controlBorder(Boolean(error))} h-11 sm:h-9`}
        {...select}
      >
        {children}
      </select>
    </FieldFrame>
  )
}
