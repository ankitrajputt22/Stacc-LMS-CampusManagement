import { type FormEvent, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { generateFacultyAssessmentDraft } from '../api/aiApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiAssessmentDraft, AiCitation, AiDifficulty, AiQuestionType } from '../types/ai'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { DIFFICULTY_LABELS, DRAFT_WARNING, QUESTION_TYPE_LABELS } from './aiText'
import { fieldErrorClasses, hintClasses, labelClasses, panelClasses } from './aiStyles'
import { type EditableQuestion, assignmentPrefill, totalMarks } from './assignmentPrefill'
import { useAiAction } from './useAiAction'

const DIFFICULTIES: AiDifficulty[] = ['EASY', 'MEDIUM', 'HARD', 'MIXED']
const QUESTION_TYPES: AiQuestionType[] = ['MCQ', 'SHORT_ANSWER', 'LONG_ANSWER', 'MIXED']
const MAX_NOTE = 500

type EditableDraft = { title: string; instructions: string; questions: (EditableQuestion & { citations: AiCitation[] })[] }

function editable(draft: AiAssessmentDraft): EditableDraft {
  return {
    title: draft.titleSuggestion ?? '',
    instructions: draft.instructionsSuggestion ?? '',
    questions: draft.questions.map((question) => ({
      questionType: question.questionType,
      prompt: question.prompt,
      options: question.options,
      suggestedAnswer: question.suggestedAnswer,
      marks: String(question.suggestedMarks),
      citations: question.citations,
    })),
  }
}

/** The generated draft, editable here only. "Use in assignment form" opens the normal form; it saves nothing. */
function DraftEditor({ lmsCourseId, draft }: { lmsCourseId: number; draft: AiAssessmentDraft }) {
  const id = useId()
  const navigate = useNavigate()
  const titleRef = useRef<HTMLInputElement>(null)
  const [value, setValue] = useState(() => editable(draft))
  const [titleError, setTitleError] = useState<string | null>(null)
  const total = totalMarks(value.questions)

  function updateQuestion(index: number, change: Partial<EditableQuestion>) {
    setValue((current) => ({
      ...current,
      questions: current.questions.map((question, position) => (position === index ? { ...question, ...change } : question)),
    }))
  }

  function openAssignmentForm() {
    if (value.title.trim() === '') {
      setTitleError('Enter a title before using the draft.')
      titleRef.current?.focus()
      return
    }
    // Only the form's own Create button creates an assignment, and it starts as a draft.
    navigate(`/teaching/${lmsCourseId}`, {
      state: { assignmentDraft: assignmentPrefill(value.title, value.instructions, value.questions) },
    })
  }

  return (
    <div className="mt-4">
      <AiMessage tone="warning">
        <strong className="font-semibold">{DRAFT_WARNING}</strong>
      </AiMessage>
      <div className="mt-4 grid gap-4">
        <div>
          <label htmlFor={`${id}-title`} className={labelClasses}>
            Title
          </label>
          <input
            ref={titleRef}
            id={`${id}-title`}
            value={value.title}
            maxLength={200}
            onChange={(event) => {
              setValue((current) => ({ ...current, title: event.target.value }))
              setTitleError(null)
            }}
            aria-invalid={titleError !== null || undefined}
            aria-describedby={titleError ? `${id}-title-error` : undefined}
            className={`${fieldClasses} ${border(titleError !== null)} h-11 sm:h-9`}
          />
          {titleError && (
            <p id={`${id}-title-error`} className={fieldErrorClasses}>
              {titleError}
            </p>
          )}
        </div>
        <div>
          <label htmlFor={`${id}-instructions`} className={labelClasses}>
            Instructions for students
          </label>
          <textarea
            id={`${id}-instructions`}
            rows={3}
            value={value.instructions}
            onChange={(event) => setValue((current) => ({ ...current, instructions: event.target.value }))}
            className={`${fieldClasses} ${border(false)} py-2 leading-6`}
          />
        </div>
      </div>

      <h3 className="mt-6 text-sm font-semibold text-ink">Questions</h3>
      <ol aria-label="Draft questions" className="mt-2 space-y-3">
        {value.questions.map((question, index) => (
          <li key={index} className="rounded-control border border-line px-3 py-3 sm:px-4">
            <p className="text-sm font-semibold text-ink">
              Question {index + 1} · {QUESTION_TYPE_LABELS[question.questionType]}
            </p>
            <label htmlFor={`${id}-q${index}-prompt`} className={`${labelClasses} mt-2`}>
              Question text
            </label>
            <textarea
              id={`${id}-q${index}-prompt`}
              rows={3}
              value={question.prompt}
              onChange={(event) => updateQuestion(index, { prompt: event.target.value })}
              className={`${fieldClasses} ${border(false)} py-2 leading-6`}
            />
            {question.questionType === 'MCQ' && (
              <fieldset className="mt-2">
                <legend className={labelClasses}>Options</legend>
                {question.options.map((option, optionIndex) => (
                  <div key={optionIndex}>
                    <label htmlFor={`${id}-q${index}-o${optionIndex}`} className="sr-only">
                      Option {optionIndex + 1} of question {index + 1}
                    </label>
                    <input
                      id={`${id}-q${index}-o${optionIndex}`}
                      value={option}
                      onChange={(event) =>
                        updateQuestion(index, {
                          options: question.options.map((current, position) =>
                            position === optionIndex ? event.target.value : current,
                          ),
                        })
                      }
                      className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}
                    />
                  </div>
                ))}
              </fieldset>
            )}
            <label htmlFor={`${id}-q${index}-answer`} className={`${labelClasses} mt-2`}>
              Suggested answer (for you; not added to the assignment)
            </label>
            <textarea
              id={`${id}-q${index}-answer`}
              rows={2}
              value={question.suggestedAnswer}
              onChange={(event) => updateQuestion(index, { suggestedAnswer: event.target.value })}
              className={`${fieldClasses} ${border(false)} py-2 leading-6`}
            />
            <label htmlFor={`${id}-q${index}-marks`} className={`${labelClasses} mt-2`}>
              Suggested marks
            </label>
            <input
              id={`${id}-q${index}-marks`}
              inputMode="decimal"
              value={question.marks}
              onChange={(event) => updateQuestion(index, { marks: event.target.value })}
              className={`${fieldClasses} ${border(false)} h-11 sm:h-9 sm:max-w-[10rem]`}
            />
            <AiCitations citations={question.citations} headingLevel={4} />
          </li>
        ))}
      </ol>

      <p className="mt-4 text-sm text-ink">
        Total suggested marks: <span className="font-medium">{total === null ? 'not set' : total}</span>
      </p>
      <p className={hintClasses}>
        Use in assignment form opens the normal assignment form with the title, instructions and questions, and total
        marks filled in. Nothing is saved until you review it, choose a due date, and create it yourself. It starts as a
        draft, and students see it only after you publish it.
      </p>
      <div className="mt-3">
        <Button variant="secondary" onClick={openAssignmentForm}>
          Use in assignment form
        </Button>
      </div>
    </div>
  )
}

/** Generates an assessment draft from the course's published content, for the teacher to review and edit. */
export function AssessmentDraftPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const countRef = useRef<HTMLInputElement>(null)
  const [count, setCount] = useState('5')
  const [difficulty, setDifficulty] = useState<AiDifficulty>('MEDIUM')
  const [questionType, setQuestionType] = useState<AiQuestionType>('MIXED')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [generation, setGeneration] = useState(0)
  const { state, run } = useAiAction<AiAssessmentDraft>('faculty')
  const running = state.status === 'running'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (running) {
      return
    }
    const questionCount = Number(count)
    if (!/^\d+$/.test(count.trim()) || questionCount < 1 || questionCount > 20) {
      setError('Choose between 1 and 20 questions.')
      countRef.current?.focus()
      return
    }
    setError(null)
    const done = await run((signal) =>
      generateFacultyAssessmentDraft(
        lmsCourseId,
        { questionCount, difficulty, questionType, additionalInstruction: note.trim() === '' ? null : note.trim() },
        signal,
      ),
    )
    if (done) {
      setGeneration((current) => current + 1)
    }
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Assessment Draft Generator
      </h2>
      <p className={hintClasses}>
        Drafts questions from this course’s published materials. It never creates, publishes, or grades an assignment.
      </p>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-3">
        <div>
          <label htmlFor={`${id}-count`} className={labelClasses}>
            Question count
          </label>
          <input
            ref={countRef}
            id={`${id}-count`}
            type="number"
            inputMode="numeric"
            min={1}
            max={20}
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
            From 1 to 20.
          </p>
          {error && (
            <p id={`${id}-count-error`} className={fieldErrorClasses}>
              {error}
            </p>
          )}
        </div>
        <div>
          <label htmlFor={`${id}-difficulty`} className={labelClasses}>
            Difficulty
          </label>
          <select
            id={`${id}-difficulty`}
            value={difficulty}
            onChange={(event) => setDifficulty(event.target.value as AiDifficulty)}
            className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}
          >
            {DIFFICULTIES.map((value) => (
              <option key={value} value={value}>
                {DIFFICULTY_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-type`} className={labelClasses}>
            Question type
          </label>
          <select
            id={`${id}-type`}
            value={questionType}
            onChange={(event) => setQuestionType(event.target.value as AiQuestionType)}
            className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}
          >
            {QUESTION_TYPES.map((value) => (
              <option key={value} value={value}>
                {QUESTION_TYPE_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-3">
          <label htmlFor={`${id}-note`} className={labelClasses}>
            Additional instruction (optional)
          </label>
          <textarea
            id={`${id}-note`}
            rows={2}
            maxLength={MAX_NOTE}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            aria-describedby={`${id}-note-hint`}
            className={`${fieldClasses} ${border(false)} py-2 leading-6`}
          />
          <p id={`${id}-note-hint`} className={hintClasses}>
            For example, which topics to focus on. Up to 500 characters.
          </p>
        </div>
        <div className="sm:col-span-3">
          <Button type="submit" disabled={running}>
            Generate draft
          </Button>
        </div>
      </form>

      <div aria-live="polite" className="mt-4 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating draft…</p>}
        {state.status === 'done' && (state.data.insufficientContext || state.data.questions.length === 0) && (
          <AiMessage tone="note">
            The published course materials don’t support a draft with these settings. Try other settings, or publish
            more content and refresh AI knowledge.
          </AiMessage>
        )}
      </div>
      {state.status === 'done' && !state.data.insufficientContext && state.data.questions.length > 0 && (
        <DraftEditor key={generation} lmsCourseId={lmsCourseId} draft={state.data} />
      )}
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
