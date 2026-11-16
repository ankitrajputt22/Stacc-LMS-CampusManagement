import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { Link } from 'react-router'
import { draftIntoQuestionBank } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { questionTypeLabel } from '../teaching/questionText'
import type { AiQuestionBankDraft } from '../types/ai'
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
 * Generate into the bank (v0.14.1): AI drafts questions of the chosen types into the course's question bank, each
 * Needs review and marked as an AI draft with its sources. Choice and true/false questions whose key the AI does not
 * reproduce are dropped. Sent only on the button; nothing is offered for practice or put in a quiz.
 */
export function BankDraftPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const queryClient = useQueryClient()
  const [count, setCount] = useState('5')
  const [types, setTypes] = useState<QuestionType[]>(['SINGLE_CHOICE', 'TRUE_FALSE'])
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>('MEDIUM')
  const [error, setError] = useState<string | null>(null)
  const { state, run } = useAiAction<AiQuestionBankDraft>('faculty')
  const running = state.status === 'running'

  async function submit(event: FormEvent) {
    event.preventDefault()
    const questionCount = Number(count)
    if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 10) {
      setError('Choose from 1 to 10 questions.')
      return
    }
    if (types.length === 0) {
      setError('Choose at least one question type.')
      return
    }
    setError(null)
    const result = await run((signal) =>
      draftIntoQuestionBank(lmsCourseId, { questionCount, questionTypes: types, difficulty, topicId: null }, signal),
    )
    if (result && result.added > 0) {
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.questions(lmsCourseId) })
    }
  }

  return (
    <section aria-labelledby={`${id}-heading`} aria-busy={running} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Draft into the question bank
      </h2>
      <p className={hintClasses}>
        Questions go into the question bank as Needs review, marked as AI drafts with the course sources they came
        from. Review and edit each one before using it.
      </p>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Questions" type="number" min={1} max={10} value={count} onChange={(event) => setCount(event.target.value)} />
          <SelectField label="Difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value as QuestionDifficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </SelectField>
        </div>
        <fieldset>
          <legend className={labelClasses}>Question types</legend>
          <div className="mt-1 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink">
            {TYPES.map((type) => (
              <label key={type} className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={types.includes(type)}
                  onChange={(event) =>
                    setTypes((current) => (event.target.checked ? [...current, type] : current.filter((kind) => kind !== type)))
                  }
                />
                {questionTypeLabel(type)}
              </label>
            ))}
          </div>
        </fieldset>
        {error && <p role="alert" className="text-sm text-danger">{error}</p>}
        <div>
          <Button type="submit" disabled={running}>
            {running ? 'Drafting questions…' : 'Draft questions'}
          </Button>
        </div>
      </form>
      <div aria-live="polite" className="mt-3 empty:hidden">
        {state.status === 'done' &&
          (state.data.added > 0 ? (
            <AiMessage tone="success">
              <p className="font-medium">
                Added {plural(state.data.added, 'question', 'questions')} to the question bank as Needs review.
              </p>
              {state.data.droppedForKeyMismatch > 0 && (
                <p>
                  {plural(state.data.droppedForKeyMismatch, 'question was', 'questions were')} dropped because the AI
                  answered it differently from its own key.
                </p>
              )}
              <Link to={`/teaching/${lmsCourseId}/questions`} className={textLinkClasses}>
                Review them in the question bank
              </Link>
            </AiMessage>
          ) : (
            <AiMessage tone="note">
              No questions were added.
              {state.data.droppedForKeyMismatch > 0
                ? ` ${plural(state.data.droppedForKeyMismatch, 'draft was', 'drafts were')} dropped because the AI answered it differently from its own key.`
                : ' The course sources did not give enough to draft from.'}
            </AiMessage>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-3">{state.failure.message}</AiMessage>}
    </section>
  )
}
