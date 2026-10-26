import { type FormEvent, useId, useRef, useState } from 'react'
import { askCourseAi } from '../api/aiApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiAnswer } from '../types/ai'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { fieldErrorClasses, hintClasses, labelClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

const MAX_QUESTION = 1000

/** One question at a time, sent only when the student presses Ask. The answer is plain text with its sources. */
export function AskPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const fieldRef = useRef<HTMLTextAreaElement>(null)
  const [question, setQuestion] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { state, run } = useAiAction<AiAnswer>('student')
  const running = state.status === 'running'

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
    await run((signal) => askCourseAi(lmsCourseId, trimmed, signal))
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Ask a question
      </h2>
      <form noValidate onSubmit={submit} className="mt-3">
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
          Up to 1,000 characters. Answers use only this course’s published materials.
        </p>
        {error && (
          <p id={`${id}-error`} className={fieldErrorClasses}>
            {error}
          </p>
        )}
        <div className="mt-3">
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
            <div>
              <h3 className="text-sm font-semibold text-ink">Answer</h3>
              {/* Plain text: React shows it as text, never as HTML. */}
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{state.data.answer}</p>
              <AiCitations citations={state.data.citations} />
            </div>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
