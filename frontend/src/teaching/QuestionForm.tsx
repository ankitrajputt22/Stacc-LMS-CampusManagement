import { useId, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { BLOOM_LEVELS } from '../courses/bloomText'
import { FormattedTextField } from '../formatting/FormattedTextField'
import type { BloomLevel, LmsOutcome, LmsQuestionInput, QuestionDifficulty, QuestionType } from '../types/lms'
import { Alert, Button, SelectField, TextAreaField, TextField } from '../ui'
import { DIFFICULTIES, QUESTION_TYPES } from './questionText'
import { changeType, checkDraft, inputOf, MAX_OPTIONS, type QuestionDraft, usesOptions } from './questionDraft'

type QuestionFormProps = {
  initial: QuestionDraft
  /** Active outcomes of the course, and an archived one the question already has. */
  outcomes: LmsOutcome[]
  submitLabel: string
  onSubmit: (input: LmsQuestionInput) => Promise<void>
  onCancel?: () => void
}

function refusal(failure: unknown): { message: string | null; fields: Record<string, string> } {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return { message: null, fields: {} }
  }
  if (status === 403) {
    return {
      message: 'You don’t have access to change this course’s question bank. If your access changed recently, sign out and sign in again.',
      fields: {},
    }
  }
  const apiError = getApiError(failure)
  const fields: Record<string, string> = {}
  for (const fieldError of apiError?.fieldErrors ?? []) {
    // Errors about one option belong to the options as a whole.
    fields[fieldError.field.startsWith('options') ? 'options' : fieldError.field] = fieldError.message
  }
  const message =
    Object.keys(fields).length > 0
      ? null
      : apiError?.message && status !== null && status < 500
        ? apiError.message
        : 'Couldn’t save the question. Check your connection and try again.'
  return { message, fields }
}

/**
 * Adding or editing a question with its answers. The parts shown follow the type: options with the correct ones marked,
 * the true or false answer, accepted short answers, or marking guidance. Obvious slips are caught before sending; the
 * backend decides.
 */
export function QuestionForm({ initial, outcomes, submitLabel, onSubmit, onCancel }: QuestionFormProps) {
  const id = useId()
  const [draft, setDraft] = useState(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const shortAnswer = draft.type === 'SHORT_ANSWER'
  const optionWord = shortAnswer ? 'Accepted answer' : 'Option'

  function update(change: Partial<QuestionDraft>) {
    setDraft((current) => ({ ...current, ...change }))
  }

  function updateOption(index: number, change: Partial<{ text: string; correct: boolean }>) {
    setDraft((current) => ({
      ...current,
      options: current.options.map((option, at) => {
        if (at === index) {
          return { ...option, ...change }
        }
        // A single-choice question has one correct option: choosing another clears the rest.
        return current.type === 'SINGLE_CHOICE' && change.correct ? { ...option, correct: false } : option
      }),
    }))
  }

  async function submit() {
    if (busy) {
      return
    }
    const found = checkDraft(draft)
    setErrors(found)
    setMessage(null)
    if (Object.keys(found).length > 0) {
      return
    }
    setBusy(true)
    try {
      await onSubmit(inputOf(draft))
    } catch (failure) {
      const refused = refusal(failure)
      setErrors(refused.fields)
      setMessage(refused.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <SelectField
        label="Question type"
        value={draft.type}
        onChange={(event) => setDraft(changeType(draft, event.target.value as QuestionType))}
      >
        {QUESTION_TYPES.map((type) => (
          <option key={type.value} value={type.value}>
            {type.label}
          </option>
        ))}
      </SelectField>

      <FormattedTextField
        id={`${id}-text`}
        label="Question"
        value={draft.text}
        onChange={(text) => update({ text })}
        format={draft.textFormat}
        onFormatChange={(textFormat) => update({ textFormat })}
        rows={4}
        maxLength={20_000}
        error={errors.text}
      />

      {usesOptions(draft.type) && (
        <fieldset className="rounded-control border border-line p-3">
          <legend className="px-1 text-sm font-medium text-ink">{shortAnswer ? 'Accepted answers' : 'Options'}</legend>
          <p className="mb-2 text-sm text-ink-muted">
            {draft.type === 'SINGLE_CHOICE'
              ? 'Mark the one correct option.'
              : draft.type === 'MULTIPLE_CHOICE'
                ? 'Mark every correct option.'
                : 'Every answer listed is accepted. Plain text.'}
          </p>
          <ol className="space-y-2">
            {draft.options.map((option, index) => (
              <li key={index} className="flex flex-col gap-2 sm:flex-row sm:items-end">
                <TextField
                  label={`${optionWord} ${index + 1}`}
                  className="min-w-0 flex-1"
                  value={option.text}
                  maxLength={500}
                  onChange={(event) => updateOption(index, { text: event.target.value })}
                />
                {!shortAnswer && (
                  <label className="flex items-center gap-2 pb-2 text-sm text-ink">
                    <input
                      type={draft.type === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                      name={`${id}-correct`}
                      checked={option.correct}
                      onChange={(event) => updateOption(index, { correct: event.target.checked })}
                    />
                    Correct<span className="sr-only"> answer: {optionWord.toLowerCase()} {index + 1}</span>
                  </label>
                )}
                <Button
                  variant="secondary"
                  size="sm"
                  className="sm:mb-1"
                  aria-disabled={draft.options.length <= 1 || undefined}
                  onClick={() => {
                    if (draft.options.length > 1) {
                      update({ options: draft.options.filter((_, at) => at !== index) })
                    }
                  }}
                >
                  Remove<span className="sr-only"> {optionWord.toLowerCase()} {index + 1}</span>
                </Button>
              </li>
            ))}
          </ol>
          {errors.options && (
            <p className="mt-2 text-sm text-danger" role="alert">
              {errors.options}
            </p>
          )}
          {draft.options.length < MAX_OPTIONS && (
            <Button
              variant="secondary"
              size="sm"
              className="mt-3"
              onClick={() => update({ options: [...draft.options, { text: '', correct: shortAnswer }] })}
            >
              Add {optionWord.toLowerCase()}
            </Button>
          )}
        </fieldset>
      )}

      {draft.type === 'TRUE_FALSE' && (
        <fieldset className="rounded-control border border-line p-3">
          <legend className="px-1 text-sm font-medium text-ink">Correct answer</legend>
          <div className="flex gap-6">
            {(['true', 'false'] as const).map((value) => (
              <label key={value} className="flex items-center gap-2 text-sm text-ink">
                <input
                  type="radio"
                  name={`${id}-true-false`}
                  checked={draft.trueFalseAnswer === value}
                  onChange={() => update({ trueFalseAnswer: value })}
                />
                {value === 'true' ? 'True' : 'False'}
              </label>
            ))}
          </div>
          {errors.trueFalseAnswer && (
            <p className="mt-2 text-sm text-danger" role="alert">
              {errors.trueFalseAnswer}
            </p>
          )}
        </fieldset>
      )}

      {draft.type === 'DESCRIPTIVE' && (
        <TextAreaField
          label="Answer guidance (optional)"
          hint="Plain text for whoever marks the answer, such as the points a full answer covers."
          value={draft.answerGuidance}
          rows={3}
          maxLength={5_000}
          error={errors.answerGuidance}
          onChange={(event) => update({ answerGuidance: event.target.value })}
        />
      )}

      <TextAreaField
        label="Explanation (optional)"
        hint="Why the answer is right. Written in the same format as the question."
        value={draft.explanation}
        rows={3}
        maxLength={20_000}
        error={errors.explanation}
        onChange={(event) => update({ explanation: event.target.value })}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          label="Topic (optional)"
          value={draft.topic}
          maxLength={100}
          error={errors.topic}
          onChange={(event) => update({ topic: event.target.value })}
        />
        <SelectField
          label="Difficulty"
          value={draft.difficulty}
          onChange={(event) => update({ difficulty: event.target.value as QuestionDifficulty })}
        >
          {DIFFICULTIES.map((difficulty) => (
            <option key={difficulty.value} value={difficulty.value}>
              {difficulty.label}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Marks"
          inputMode="decimal"
          value={draft.marks}
          error={errors.marks}
          onChange={(event) => update({ marks: event.target.value })}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Course outcome (optional)"
          value={draft.outcomeId}
          error={errors.outcomeId}
          onChange={(event) => update({ outcomeId: event.target.value })}
        >
          <option value="">None</option>
          {outcomes.map((outcome) => (
            <option key={outcome.id} value={outcome.id}>
              {outcome.code}: {outcome.description}
              {outcome.status === 'ARCHIVED' ? ' (archived)' : ''}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Bloom level (optional)"
          value={draft.bloomLevel}
          onChange={(event) => update({ bloomLevel: event.target.value as BloomLevel | '' })}
        >
          <option value="">None</option>
          {BLOOM_LEVELS.map((level) => (
            <option key={level.value} value={level.value}>
              {level.label}
            </option>
          ))}
        </SelectField>
      </div>

      {message && <Alert tone="error">{message}</Alert>}
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button aria-disabled={busy || undefined} onClick={() => void submit()}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  )
}
