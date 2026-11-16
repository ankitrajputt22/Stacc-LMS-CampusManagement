import { type FormEvent, useState } from 'react'
import { suggestRubric } from '../api/aiApi'
import { useFacultyAssignments } from '../teaching/useFacultyAssignments'
import type { RubricDraft } from '../teaching/rubricDraft'
import { rubricDraftOf } from './rubricDraftOf'
import type { AiRubricSuggestion } from '../types/ai'
import { Button, Card, SelectField } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { DRAFT_WARNING } from './aiText'
import { useAiAction } from './useAiAction'

/**
 * Rubric suggestions (v0.14.5): AI suggests criteria and levels for one assignment, adding up to its maximum marks.
 * Nothing is saved: Use in rubric form opens the normal form filled in, to edit and create.
 */
export function RubricSuggestionPanel({ lmsCourseId, onUse }: { lmsCourseId: number; onUse: (draft: RubricDraft) => void }) {
  const assignments = useFacultyAssignments(lmsCourseId)
  const [assignmentId, setAssignmentId] = useState('')
  const [count, setCount] = useState('4')
  const { state, run } = useAiAction<AiRubricSuggestion>('faculty')
  const running = state.status === 'running'
  const choices = assignments.status === 'loaded' ? assignments.data.filter((assignment) => assignment.status !== 'ARCHIVED') : []

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (assignmentId) {
      await run((signal) => suggestRubric(Number(assignmentId), Number(count), signal))
    }
  }

  return (
    <Card title="Suggest a rubric with AI" description="Criteria and levels for one assignment that add up to its maximum marks.">
      <form noValidate onSubmit={submit} className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-end">
        <SelectField label="Assignment" value={assignmentId} onChange={(event) => setAssignmentId(event.target.value)}>
          <option value="">Choose an assignment</option>
          {choices.map((assignment) => (
            <option key={assignment.id} value={assignment.id}>
              {assignment.title} ({assignment.maxMarks} marks)
            </option>
          ))}
        </SelectField>
        <SelectField label="Criteria" value={count} onChange={(event) => setCount(event.target.value)}>
          {[2, 3, 4, 5, 6, 7, 8].map((number) => (
            <option key={number} value={number}>{number}</option>
          ))}
        </SelectField>
        <Button type="submit" disabled={running || !assignmentId}>{running ? 'Suggesting…' : 'Suggest rubric'}</Button>
      </form>
      <div aria-live="polite" className="mt-3 empty:hidden">
        {state.status === 'done' &&
          (state.data.insufficientContext ? (
            <AiMessage tone="note">AI couldn’t suggest a rubric that adds up to the assignment’s marks. Try again or write one.</AiMessage>
          ) : (
            <div className="space-y-2 text-sm">
              <AiMessage tone="warning">{DRAFT_WARNING}</AiMessage>
              <ul className="space-y-1">
                {state.data.criteria.map((criterion) => (
                  <li key={criterion.title}>
                    <span className="font-medium text-ink">{criterion.title}</span> · {criterion.maxPoints} points
                    {criterion.levels.length > 0 && ` · ${criterion.levels.map((level) => `${level.title} ${level.points}`).join(', ')}`}
                  </li>
                ))}
              </ul>
              <AiCitations citations={state.data.citations} headingLevel={4} />
              <Button size="sm" onClick={() => onUse(rubricDraftOf(state.data as AiRubricSuggestion))}>Use in rubric form</Button>
            </div>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-3">{state.failure.message}</AiMessage>}
    </Card>
  )
}
