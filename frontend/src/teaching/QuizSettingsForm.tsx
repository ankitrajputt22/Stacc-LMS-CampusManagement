import { type FormEvent, useId, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { localTimeZoneName } from '../courses/dates'
import { FormattedTextField } from '../formatting/FormattedTextField'
import type { LmsQuizSettingsInput, QuizResultRelease, QuizType } from '../types/lms'
import { Alert, Button, SelectField, TextField } from '../ui'
import {
  checkSettings,
  QUIZ_INSTRUCTIONS_MAX,
  QUIZ_TITLE_MAX,
  QUIZ_TYPES,
  type QuizSettingsDraft,
  RESULT_RELEASES,
} from './quizSettings'

export const QUIZ_FORBIDDEN =
  'You don’t have access to change this course’s quizzes. If your access changed recently, sign out and sign in again.'

type QuizSettingsFormProps = {
  initial: QuizSettingsDraft
  /** After publishing, the type, shuffling, and negative marking stay as they are. */
  published?: boolean
  submitLabel: string
  onSubmit: (input: LmsQuizSettingsInput) => Promise<void>
  onCancel?: () => void
}

function refusal(failure: unknown): { message: string | null; fields: Record<string, string> } {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return { message: null, fields: {} }
  }
  if (status === 403) {
    return { message: QUIZ_FORBIDDEN, fields: {} }
  }
  const apiError = getApiError(failure)
  const fields = Object.fromEntries((apiError?.fieldErrors ?? []).map((error) => [error.field, error.message]))
  const message =
    Object.keys(fields).length > 0
      ? null
      : apiError?.message && status !== null && status < 500
        ? apiError.message
        : 'Couldn’t save the quiz. Check your connection and try again.'
  return { message, fields }
}

/**
 * A quiz's settings: title, instructions, type, time limit, availability, attempts, shuffling, and negative marking.
 * Times are entered in the browser's own time zone and sent as absolute moments. Obvious slips are caught before
 * sending; the backend decides.
 */
export function QuizSettingsForm({ initial, published = false, submitLabel, onSubmit, onCancel }: QuizSettingsFormProps) {
  const id = useId()
  const [draft, setDraft] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function update(change: Partial<QuizSettingsDraft>) {
    setDraft((current) => ({ ...current, ...change }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    const checked = checkSettings(draft)
    setErrors(checked.errors)
    setMessage(null)
    if (checked.input === null) {
      return
    }
    setBusy(true)
    try {
      await onSubmit(checked.input)
    } catch (failure) {
      const refused = refusal(failure)
      setErrors(refused.fields)
      setMessage(refused.message)
    } finally {
      setBusy(false)
    }
  }

  const zone = localTimeZoneName()
  return (
    <form className="space-y-4" onSubmit={(event) => void submit(event)} noValidate>
      <TextField
        label="Title"
        value={draft.title}
        maxLength={QUIZ_TITLE_MAX}
        error={errors.title}
        onChange={(event) => update({ title: event.target.value })}
      />
      <FormattedTextField
        id={`${id}-instructions`}
        label="Instructions (optional)"
        value={draft.instructions}
        onChange={(instructions) => update({ instructions })}
        format={draft.instructionsFormat}
        onFormatChange={(instructionsFormat) => update({ instructionsFormat })}
        rows={3}
        maxLength={QUIZ_INSTRUCTIONS_MAX}
        error={errors.instructions}
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <SelectField
          label="Quiz type"
          value={draft.type}
          disabled={published}
          hint={published ? 'Fixed after publishing.' : 'A practice quiz takes only questions offered for practice.'}
          onChange={(event) => update({ type: event.target.value as QuizType })}
        >
          {QUIZ_TYPES.map((type) => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Time limit in minutes (optional)"
          inputMode="numeric"
          value={draft.timeLimitMinutes}
          hint="Empty means no time limit."
          error={errors.timeLimitMinutes}
          onChange={(event) => update({ timeLimitMinutes: event.target.value })}
        />
        <TextField
          label="Attempts allowed (optional)"
          inputMode="numeric"
          value={draft.maxAttempts}
          hint="Empty means unlimited."
          error={errors.maxAttempts}
          onChange={(event) => update({ maxAttempts: event.target.value })}
        />
      </div>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-ink">Availability</legend>
        <p className="text-sm text-ink-muted">Times are in your time zone ({zone}). Leave a time empty for no limit.</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label="Opens (optional)"
            type="datetime-local"
            value={draft.availableFrom}
            error={errors.availableFrom}
            onChange={(event) => update({ availableFrom: event.target.value })}
          />
          <TextField
            label="Due (optional)"
            type="datetime-local"
            value={draft.dueAt}
            error={errors.dueAt}
            onChange={(event) => update({ dueAt: event.target.value })}
          />
          <TextField
            label="Closes (optional)"
            type="datetime-local"
            value={draft.availableUntil}
            hint="No attempt can start after this."
            error={errors.availableUntil}
            onChange={(event) => update({ availableUntil: event.target.value })}
          />
        </div>
      </fieldset>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-ink">Shuffling and marking</legend>
        {published && <p className="text-sm text-ink-muted">These stay as they are after publishing.</p>}
        <label className="flex items-center gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={draft.shuffleQuestions}
            disabled={published}
            onChange={(event) => update({ shuffleQuestions: event.target.checked })}
          />
          Shuffle questions for each attempt
        </label>
        <label className="flex items-center gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={draft.shuffleOptions}
            disabled={published}
            onChange={(event) => update({ shuffleOptions: event.target.checked })}
          />
          Shuffle choices for each attempt
        </label>
        <TextField
          label="Negative marking (% of a question’s marks)"
          className="max-w-xs"
          inputMode="decimal"
          value={draft.negativeMarkPercent}
          disabled={published}
          hint="Taken off for a wrong objective answer. Unanswered questions lose nothing; 0 means none."
          error={errors.negativeMarkPercent}
          onChange={(event) => update({ negativeMarkPercent: event.target.value })}
        />
      </fieldset>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-ink">Results</legend>
        <SelectField
          label="Students see their result"
          className="max-w-sm"
          value={draft.resultRelease}
          hint="Only once every written answer is marked."
          onChange={(event) => update({ resultRelease: event.target.value as QuizResultRelease })}
        >
          {RESULT_RELEASES.map((release) => (
            <option key={release.value} value={release.value}>
              {release.label}
            </option>
          ))}
        </SelectField>
        <label className="flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={draft.showAnswers} onChange={(event) => update({ showAnswers: event.target.checked })} />
          Show the correct answers and explanations with results
        </label>
      </fieldset>
      {message && <Alert tone="error">{message}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
