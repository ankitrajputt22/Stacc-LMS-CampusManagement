import { type FormEvent, useId, useRef, useState } from 'react'
import { askCourseAi } from '../api/aiApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiAnswer, AiAskMode } from '../types/ai'
import { SelectField } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { fieldErrorClasses, hintClasses, labelClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

const MAX_QUESTION = 1000

export type AskModule = { id: number; title: string }

const MODES: { value: AiAskMode; label: string; hint: string }[] = [
  { value: 'COURSE', label: 'Course', hint: 'Published pages and files of this course.' },
  { value: 'COURSE_AND_NOTES', label: 'Course + my notes', hint: 'The course and your own notes.' },
  { value: 'NOTES_ONLY', label: 'My notes only', hint: 'Only your own notes in this course.' },
]

/**
 * Ask My Course (v0.13.9): one question at a time, sent only when the student presses Ask, searching the course, the
 * course and their notes, or their notes only, optionally within one module. The answer is plain text with source
 * cards linking to the course pages they come from.
 */
export function AskPanel({ lmsCourseId, modules = [] }: { lmsCourseId: number; modules?: AskModule[] }) {
  const id = useId()
  const fieldRef = useRef<HTMLTextAreaElement>(null)
  const [question, setQuestion] = useState('')
  const [mode, setMode] = useState<AiAskMode>('COURSE')
  const [moduleId, setModuleId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { state, run } = useAiAction<AiAnswer>('student')
  const running = state.status === 'running'
  const scoped = mode !== 'NOTES_ONLY' && moduleId !== ''

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (running) {
      return
    }
    const trimmed = question.trim()
    if (trimmed.length < 2) {
      setError('Enter a question of at least 2 characters.')
      fieldRef.current?.focus()
      return
    }
    setError(null)
    await run((signal) => askCourseAi(lmsCourseId, trimmed, signal, { mode, moduleId: scoped ? Number(moduleId) : null }))
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Ask My Course
      </h2>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3">
        <fieldset>
          <legend className={labelClasses}>Search</legend>
          <div className="mt-1 grid gap-2 sm:grid-cols-3">
            {MODES.map((choice) => (
              <label
                key={choice.value}
                className={`flex cursor-pointer gap-2 rounded-control border px-3 py-2 text-sm ${mode === choice.value ? 'border-stacc-primary bg-blue-50' : 'border-line'}`}
              >
                <input
                  type="radio"
                  name={`${id}-mode`}
                  className="mt-1"
                  checked={mode === choice.value}
                  onChange={() => setMode(choice.value)}
                />
                <span>
                  <span className="block font-medium text-ink">{choice.label}</span>
                  <span className="block text-ink-muted">{choice.hint}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        {mode !== 'NOTES_ONLY' && modules.length > 0 && (
          <SelectField label="Module" value={moduleId} onChange={(event) => setModuleId(event.target.value)}>
            <option value="">Whole course</option>
            {modules.map((module) => (
              <option key={module.id} value={module.id}>
                {module.title}
              </option>
            ))}
          </SelectField>
        )}
        <div>
          <label htmlFor={`${id}-question`} className={labelClasses}>
            Question
          </label>
          <textarea
            ref={fieldRef}
            id={`${id}-question`}
            rows={4}
            maxLength={MAX_QUESTION}
            value={question}
            onChange={(event) => {
              setQuestion(event.target.value)
              setError(null)
            }}
            aria-invalid={error !== null || undefined}
            aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`}
            className={`${fieldClasses} ${border(error !== null)} py-2 leading-6`}
          />
          <p id={`${id}-hint`} className={hintClasses}>
            Up to 1,000 characters. Answers use only what you chose to search.
          </p>
          {error && (
            <p id={`${id}-error`} className={fieldErrorClasses}>
              {error}
            </p>
          )}
        </div>
        <div>
          <Button type="submit" disabled={running}>
            Ask
          </Button>
        </div>
      </form>

      <div aria-live="polite" className="mt-4 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating answer…</p>}
        {state.status === 'done' &&
          (state.data.insufficientContext || state.data.answer === null ? (
            <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>
          ) : (
            <div className="rounded-control border border-line px-3 py-3">
              <h3 className="text-sm font-semibold text-ink">Answer</h3>
              {/* Plain text: React shows it as text, never as HTML. */}
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{state.data.answer}</p>
              <AiCitations citations={state.data.citations} pageLink={(pageId) => `/courses/${lmsCourseId}/pages/${pageId}`} />
            </div>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
