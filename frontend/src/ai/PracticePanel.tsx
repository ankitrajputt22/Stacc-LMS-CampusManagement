import { type FormEvent, useId, useRef, useState } from 'react'
import { generateCoursePracticeQuestions } from '../api/aiApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiPracticeQuestion, AiPracticeSet } from '../types/ai'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { fieldErrorClasses, hintClasses, labelClasses, panelClasses } from './aiStyles'
import { type CourseTopicChoice, CourseTopicOptions } from './CourseTopicOptions'
import { useAiAction } from './useAiAction'

const COUNT_ERROR = 'Choose between 1 and 10 questions.'

function PracticeItem({ question, number }: { question: AiPracticeQuestion; number: number }) {
  const id = useId()
  const [shown, setShown] = useState(false)
  return (
    <li className="rounded-control border border-line px-3 py-3 sm:px-4">
      <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">
        <span className="font-semibold">{number}. </span>
        {question.question}
      </p>
      <button
        type="button"
        aria-expanded={shown}
        aria-controls={`${id}-answer`}
        onClick={() => setShown((current) => !current)}
        className="mt-2 inline-flex min-h-11 items-center rounded-control text-sm font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0"
      >
        {shown ? 'Hide answer' : 'Show answer'}
        <span className="sr-only"> for question {number}</span>
      </button>
      <div id={`${id}-answer`} hidden={!shown} className="mt-2">
        <h4 className="text-sm font-semibold text-ink">Suggested answer</h4>
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{question.suggestedAnswer}</p>
        <AiCitations citations={question.citations} headingLevel={4} />
      </div>
    </li>
  )
}

/** Practice questions for self-study, for the whole course or one topic. Nothing is saved or graded. */
export function PracticePanel({ lmsCourseId, topics }: { lmsCourseId: number; topics: CourseTopicChoice[] }) {
  const id = useId()
  const countRef = useRef<HTMLInputElement>(null)
  const [count, setCount] = useState('5')
  const [topicId, setTopicId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { state, run } = useAiAction<AiPracticeSet>('student')
  const running = state.status === 'running'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (running) {
      return
    }
    const questionCount = Number(count)
    if (!/^\d+$/.test(count.trim()) || questionCount < 1 || questionCount > 10) {
      setError(COUNT_ERROR)
      countRef.current?.focus()
      return
    }
    setError(null)
    await run((signal) =>
      generateCoursePracticeQuestions(lmsCourseId, questionCount, topicId === '' ? null : Number(topicId), signal),
    )
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Practice questions
      </h2>
      <p className={hintClasses}>For practice only — does not affect your grade.</p>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-[10rem_minmax(0,1fr)]">
        <div>
          <label htmlFor={`${id}-count`} className={labelClasses}>
            Number of questions
          </label>
          <input
            ref={countRef}
            id={`${id}-count`}
            type="number"
            inputMode="numeric"
            min={1}
            max={10}
            value={count}
            onChange={(event) => {
              setCount(event.target.value)
              setError(null)
            }}
            aria-invalid={error !== null || undefined}
            aria-describedby={error ? `${id}-count-error` : `${id}-count-hint`}
            className={`${fieldClasses} ${border(error !== null)} h-11 sm:h-9`}
          />
          <p id={`${id}-count-hint`} className={hintClasses}>
            From 1 to 10.
          </p>
          {error && (
            <p id={`${id}-count-error`} className={fieldErrorClasses}>
              {error}
            </p>
          )}
        </div>
        <div>
          <label htmlFor={`${id}-topic`} className={labelClasses}>
            Topic (optional)
          </label>
          <select
            id={`${id}-topic`}
            value={topicId}
            onChange={(event) => setTopicId(event.target.value)}
            className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}
          >
            <option value="">Whole course</option>
            <CourseTopicOptions topics={topics} />
          </select>
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" disabled={running}>
            Generate practice questions
          </Button>
        </div>
      </form>

      <div aria-live="polite" className="mt-4 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating practice questions…</p>}
        {state.status === 'done' &&
          (state.data.insufficientContext || state.data.questions.length === 0 ? (
            <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>
          ) : (
            <ol aria-label="Practice questions" className="space-y-3">
              {state.data.questions.map((question, index) => (
                <PracticeItem key={`${index}-${question.question}`} question={question} number={index + 1} />
              ))}
            </ol>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
