import { type Ref, useState } from 'react'
import { border, fieldClasses } from '../teaching/formStyles'
import { Tabs } from '../ui'
import { FormattedText } from './FormattedText'
import type { TextFormat } from './textFormat'

export const FORMATTING_HELP =
  'Formatting: # Heading, - list item, **bold**, *italic*, [link text](https://…), `code`, ``` for a code block, | tables |, and $x^2$ for maths. HTML is not used.'

type FormattedTextFieldProps = {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  format: TextFormat
  onFormatChange: (format: TextFormat) => void
  rows: number
  maxLength: number
  error?: string
  textareaRef?: Ref<HTMLTextAreaElement>
  /** False when the text is always formatted, such as a syllabus: the format choice is not offered. */
  formatChoice?: boolean
}

/**
 * A text area for course text with its format: Formatted (the small formatting set, with Write and Preview tabs) or
 * Plain text (shown exactly as typed). What students will see is the preview; nothing is sent until the form is saved.
 */
export function FormattedTextField({
  id,
  label,
  value,
  onChange,
  format,
  onFormatChange,
  rows,
  maxLength,
  error,
  textareaRef,
  formatChoice = true,
}: FormattedTextFieldProps) {
  const [view, setView] = useState<'write' | 'preview'>('write')
  const hint = format === 'FORMATTED' ? FORMATTING_HELP : 'Plain text. Line breaks are kept.'
  const describedBy = error ? `${id}-hint ${id}-error` : `${id}-hint`

  const area = (
    <textarea
      ref={textareaRef}
      id={id}
      value={value}
      rows={rows}
      maxLength={maxLength}
      onChange={(event) => onChange(event.target.value)}
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy}
      className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(error))}`}
    />
  )

  return (
    <div>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <label htmlFor={id} className="block text-sm font-medium text-ink">
          {label}
        </label>
        {formatChoice && (
        <label className="flex items-center gap-2 text-sm text-ink">
          <span>Text format</span>
          <select
            value={format}
            onChange={(event) => {
              onFormatChange(event.target.value as TextFormat)
              setView('write')
            }}
            className={`h-9 w-auto py-1 ${fieldClasses} ${border(false)}`}
          >
            <option value="FORMATTED">Formatted</option>
            <option value="PLAIN">Plain text</option>
          </select>
        </label>
        )}
      </div>
      <p id={`${id}-hint`} className="mt-0.5 text-xs leading-5 text-ink-muted">
        {hint}
      </p>
      <div className="mt-1">
        {format === 'FORMATTED' ? (
          <Tabs
            label={`${label}: write or preview`}
            tabs={[
              { value: 'write', label: 'Write' },
              { value: 'preview', label: 'Preview' },
            ]}
            selected={view}
            onSelect={setView}
          >
            {view === 'write' ? (
              area
            ) : value.trim() === '' ? (
              <p className="rounded-control border border-line p-3 text-sm text-ink-muted">Nothing to preview yet.</p>
            ) : (
              <div className="rounded-control border border-line p-3">
                <FormattedText text={value} format="FORMATTED" className="text-sm leading-6 text-ink" headingLevel={4} />
              </div>
            )}
          </Tabs>
        ) : (
          area
        )}
      </div>
      {error && (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
