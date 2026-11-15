import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { Link } from 'react-router'
import { draftQuiz } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { questionTypeLabel } from '../teaching/questionText'
import type { AiQuizDraft } from '../types/ai'
import type { QuestionDifficulty, QuestionType } from '../types/lms'
import { Button, SelectField, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { AiMessage } from './AiMessage'
import { hintClasses, labelClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

const TYPES: QuestionType[] = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'DESCRIPTIVE']

function plural(count: number, one: string, many: string) {
  return `${count} ${count === 1 ? one : many}`
}

/**
 * Quiz drafts (v0.14.3): an ordinary draft quiz from reviewed bank questions of the chosen kinds, picked at random, with
 * AI drafting only the gap into the bank (as Needs review) when allowed. Publishing the quiz still needs every question
 * reviewed. Sent only on the button.
 */
export function QuizDraftPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [type, setType] = useState<'GRADED' | 'PRACTICE'>('GRADED')
  const [count, setCount] = useState('10')
  const [types, setTypes] = useState<QuestionType[]>(['SINGLE_CHOICE', 'TRUE_FALSE'])
  const [difficulty, setDifficulty] = useState<QuestionDifficulty | ''>('')
  const [fillGaps, setFillGaps] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { state, run } = useAiAction<AiQuizDraft>('faculty')
  const running = state.status === 'running'

  async function submit(event: FormEvent) {
    event.preventDefault()
    const questionCount = Number(count)
    if (!title.trim()) {
      setError('Enter a title.')
      return
    }
    if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 30) {
      setError('Choose from 1 to 30 questions.')
      return
    }
    if (types.length === 0) {
      setError('Choose at least one question type.')
      return
    }
    setError(null)
    const result = await run((signal) =>
      draftQuiz(lmsCourseId, {
        title: title.trim(), type, questionCount, questionTypes: types, difficulty: difficulty || null, fillGapsWithAi: fillGaps,
      }, signal),
    )
    if (result) {
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    }
  }

  return (
    <section aria-labelledby={`${id}-heading`} aria-busy={running} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Build a draft quiz
      </h2>
      <p className={hintClasses}>
        Uses reviewed questions from the question bank first. AI drafts only what the bank is short of, and only if you
        allow it; those questions need review before the quiz can be published.
      </p>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3">
        <TextField label="Quiz title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
        <div className="grid gap-3 sm:grid-cols-3">
          <SelectField label="Quiz type" value={type} onChange={(event) => setType(event.target.value as 'GRADED' | 'PRACTICE')}>
            <option value="GRADED">Graded</option>
            <option value="PRACTICE">Practice</option>
          </SelectField>
          <TextField label="Questions" type="number" min={1} max={30} value={count} onChange={(event) => setCount(event.target.value)} />
          <SelectField label="Difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value as QuestionDifficulty | '')}>
            <option value="">Any</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </SelectField>
        </div>
        <fieldset>
          <legend className={labelClasses}>Question types</legend>
          <div className="mt-1 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink">
            {TYPES.map((kind) => (
              <label key={kind} className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={types.includes(kind)}
                  onChange={(event) => setTypes((current) => (event.target.checked ? [...current, kind] : current.filter((k) => k !== kind)))}
                />
                {questionTypeLabel(kind)}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="inline-flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={fillGaps} onChange={(event) => setFillGaps(event.target.checked)} />
          Let AI draft questions the bank is short of
        </label>
        {error && <p role="alert" className="text-sm text-danger">{error}</p>}
        <div>
          <Button type="submit" disabled={running}>
            {running ? 'Building quiz…' : 'Build draft quiz'}
          </Button>
        </div>
      </form>
      <div aria-live="polite" className="mt-3 empty:hidden">
        {state.status === 'done' && (
          <AiMessage tone="success">
            <p className="font-medium">
              Draft quiz made with {plural(state.data.fromBank, 'reviewed bank question', 'reviewed bank questions')}
              {state.data.fromAi > 0 && ` and ${plural(state.data.fromAi, 'AI draft', 'AI drafts')} that need review`}.
            </p>
            {state.data.missing > 0 && <p>{plural(state.data.missing, 'question is', 'questions are')} still missing.</p>}
            {state.data.aiNote && <p>{state.data.aiNote}</p>}
            <Link to={`/teaching/${lmsCourseId}/quizzes/${state.data.quizId}`} className={textLinkClasses}>
              Open the draft quiz
            </Link>
          </AiMessage>
        )}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-3">{state.failure.message}</AiMessage>}
    </section>
  )
}
