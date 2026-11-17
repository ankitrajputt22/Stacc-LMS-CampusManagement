import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useRef, useState } from 'react'
import { askCourseAi, saveAnswer } from '../api/aiApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiAnswer, AiAnswerStyle, AiAskMode, AiCitation } from '../types/ai'
import { SelectField } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { fieldErrorClasses, hintClasses, labelClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

const MAX_QUESTION = 1000
/** Earlier turns sent with a follow-up; the backend takes at most three. */
const MAX_HISTORY = 3

export type AskModule = { id: number; title: string }

const MODES: { value: AiAskMode; label: string; hint: string }[] = [
  { value: 'COURSE', label: 'Course', hint: 'Published pages and files of this course.' },
  { value: 'COURSE_AND_NOTES', label: 'Course + my notes', hint: 'The course and your own notes.' },
  { value: 'NOTES_ONLY', label: 'My notes only', hint: 'Only your own notes in this course.' },
]

/** One question and its answer on this page. Kept only while the page is open (decision 6). */
type Turn = { question: string; answer: string; citations: AiCitation[]; style: AiAnswerStyle; saved: boolean }

const STYLE_LABEL: Record<AiAnswerStyle, string> = { NORMAL: '', SIMPLER: 'Simpler', EXAMPLE: 'With an example' }

/**
 * Ask My Course (v0.13.9) with follow-ups (v0.15.1): one question at a time, sent only on a button, searching the
 * course, the course and the student's notes, or their notes only, optionally within one module. Answers stay on this
 * page only: follow-ups, Explain simpler, and Give an example send up to three earlier turns, and nothing is kept once
 * the page is left unless the student presses Save answer.
 */
export function AskPanel({ lmsCourseId, modules = [], assignments = [] }: {
  lmsCourseId: number
  modules?: AskModule[]
  /** The course's published assignments still due; help with one is hints only (v0.15.4). */
  assignments?: { id: number; title: string }[]
}) {
  const id = useId()
  const queryClient = useQueryClient()
  const fieldRef = useRef<HTMLTextAreaElement>(null)
  const [question, setQuestion] = useState('')
  const [followUp, setFollowUp] = useState('')
  const [mode, setMode] = useState<AiAskMode>('COURSE')
  const [moduleId, setModuleId] = useState('')
  const [assignmentId, setAssignmentId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [turns, setTurns] = useState<Turn[]>([])
  const [noAnswer, setNoAnswer] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState<number | null>(null)
  const { state, run } = useAiAction<AiAnswer>('student')
  const running = state.status === 'running'
  const scoped = mode !== 'NOTES_ONLY' && moduleId !== ''

  async function ask(text: string, earlier: Turn[], style: AiAnswerStyle) {
    setNoAnswer(false)
    setSaveMessage(null)
    const history = earlier.slice(-MAX_HISTORY).map((turn) => ({ question: turn.question, answer: turn.answer }))
    const answer = await run((signal) =>
      askCourseAi(lmsCourseId, text, signal, {
        mode, moduleId: scoped ? Number(moduleId) : null, history, style, assignmentId: assignmentId ? Number(assignmentId) : null,
      }),
    )
    if (answer === null) {
      return
    }
    if (answer.insufficientContext || answer.answer === null) {
      setNoAnswer(true)
      return
    }
    setTurns([...earlier, { question: text, answer: answer.answer, citations: answer.citations, style, saved: false }])
  }

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
    // A new question starts a new set of turns.
    await ask(trimmed, [], 'NORMAL')
  }

  async function submitFollowUp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = followUp.trim()
    if (running || trimmed.length < 2) {
      return
    }
    setFollowUp('')
    await ask(trimmed, turns, 'NORMAL')
  }

  async function restyle(style: AiAnswerStyle) {
    const last = turns.at(-1)
    if (!last || running) {
      return
    }
    await ask(last.question, turns, style)
  }

  async function save(index: number) {
    const turn = turns[index]
    if (saving !== null || turn.saved) {
      return
    }
    setSaving(index)
    try {
      await saveAnswer(lmsCourseId, turn.question, turn.answer, turn.citations)
      setTurns((current) => current.map((item, position) => (position === index ? { ...item, saved: true } : item)))
      setSaveMessage('Saved. You can find it under Saved answers.')
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.savedAnswers(lmsCourseId) })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const message = getApiError(failure)?.message
        setSaveMessage(message && status !== null && status < 500 ? message : 'Couldn’t save the answer. Try again.')
      }
    } finally {
      setSaving(null)
    }
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-2xl font-bold text-ink">
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
        {assignments.length > 0 && (
          <div>
            <SelectField
              label="Help with an assignment"
              value={assignmentId}
              onChange={(event) => setAssignmentId(event.target.value)}
              hint={assignmentId ? 'Answers will be hints and guiding questions only, never the solution.' : undefined}
            >
              <option value="">Not about an assignment</option>
              {assignments.map((assignment) => (
                <option key={assignment.id} value={assignment.id}>{assignment.title}</option>
              ))}
            </SelectField>
          </div>
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
            Up to 1,000 characters. Answers use only what you chose to search, and stay on this page until you leave it
            unless you save them.
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

      {turns.length > 0 && (
        <ol className="mt-4 space-y-3">
          {turns.map((turn, index) => (
            <li key={`${index}-${turn.question}`} className="rounded-control border border-line px-3 py-3">
              <p className="text-sm text-ink-muted">
                <span className="font-medium text-ink">You asked:</span> {turn.question}
                {turn.style !== 'NORMAL' && ` (${STYLE_LABEL[turn.style]})`}
              </p>
              <h3 className="mt-2 text-sm font-semibold text-ink">Answer</h3>
              {/* Plain text: React shows it as text, never as HTML. */}
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{turn.answer}</p>
              <AiCitations citations={turn.citations} pageLink={(pageId) => `/courses/${lmsCourseId}/pages/${pageId}`} />
              <div className="mt-2">
                <Button size="sm" variant="ghost" disabled={turn.saved || saving !== null} onClick={() => void save(index)}>
                  {turn.saved ? 'Saved' : saving === index ? 'Saving…' : 'Save answer'}
                  {!turn.saved && <span className="sr-only">{` to “${turn.question}”`}</span>}
                </Button>
              </div>
            </li>
          ))}
        </ol>
      )}

      <div aria-live="polite" className="mt-3 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating answer…</p>}
        {noAnswer && <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>}
        {saveMessage && <p className="text-sm text-ink-muted">{saveMessage}</p>}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-3">{state.failure.message}</AiMessage>}

      {turns.length > 0 && (
        <div className="mt-4 grid gap-3 border-t border-line pt-3">
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" disabled={running} onClick={() => void restyle('SIMPLER')}>
              Explain simpler
            </Button>
            <Button size="sm" variant="secondary" disabled={running} onClick={() => void restyle('EXAMPLE')}>
              Give an example
            </Button>
            <Button size="sm" variant="ghost" disabled={running} onClick={() => setTurns([])}>
              Start over
            </Button>
          </div>
          <form noValidate onSubmit={submitFollowUp} className="grid gap-2 sm:grid-cols-[1fr_auto] sm:items-end">
            <div>
              <label htmlFor={`${id}-follow-up`} className={labelClasses}>
                Follow-up question
              </label>
              <input
                id={`${id}-follow-up`}
                maxLength={MAX_QUESTION}
                value={followUp}
                onChange={(event) => setFollowUp(event.target.value)}
                className={`${fieldClasses} ${border(false)}`}
              />
            </div>
            <Button type="submit" disabled={running || followUp.trim().length < 2}>
              Ask follow-up
            </Button>
          </form>
        </div>
      )}
    </section>
  )
}
