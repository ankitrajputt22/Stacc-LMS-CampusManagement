import { useState } from 'react'
import { getRubrics, chooseAssignmentRubric } from '../api/lmsRubricsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import type { LmsFacultyAssignment } from '../types/lms'
import { Button, SelectField } from '../ui'
import type { useContentChanges } from './useContentChanges'

type Changes = ReturnType<typeof useContentChanges>

/**
 * Which rubric an assignment is graded with. Change rubric offers the course's active rubrics; one whose total differs
 * from the assignment's maximum marks is shown but cannot be chosen. Grades already given keep their own scores.
 */
export function AssignmentRubric({
  lmsCourseId,
  assignment,
  name,
  changes,
}: {
  lmsCourseId: number
  assignment: LmsFacultyAssignment
  name: string
  changes: Changes
}) {
  const [open, setOpen] = useState(false)
  const rubrics = useApiQuery(open ? queryKeys.lms.rubrics(lmsCourseId) : null, (signal) => getRubrics(lmsCourseId, signal))
  const [chosen, setChosen] = useState(assignment.rubric ? String(assignment.rubric.id) : '')
  const active = rubrics.status === 'loaded' ? rubrics.data.filter((rubric) => rubric.status === 'ACTIVE') : []

  return (
    <div className="mt-1.5">
      <p className="text-sm text-ink-muted">
        {assignment.rubric ? `Rubric: ${assignment.rubric.title} (${assignment.rubric.totalPoints} points)` : 'No rubric.'}
      </p>
      {open ? (
        <div className="mt-2 rounded-control border border-line p-3">
          {rubrics.status === 'loading' && <p className="text-sm text-ink-muted">Loading rubrics…</p>}
          <SelectField label={`Rubric for ${name}`} value={chosen} onChange={(event) => setChosen(event.target.value)}>
            <option value="">No rubric</option>
            {active.map((rubric) => {
              const fits = Number(rubric.totalPoints) === Number(assignment.maxMarks)
              return (
                <option key={rubric.id} value={rubric.id} disabled={!fits}>
                  {fits ? `${rubric.title} (${rubric.totalPoints} points)` : `${rubric.title} (${rubric.totalPoints} points; needs ${assignment.maxMarks})`}
                </option>
              )
            })}
          </SelectField>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <Button
              size="sm"
              onClick={async () => {
                const result = await changes.run(() => chooseAssignmentRubric(assignment.id, chosen === '' ? null : Number(chosen)), {
                  done: `Saved the rubric of ${name}.`,
                })
                if (result?.ok) {
                  setOpen(false)
                }
              }}
            >
              Save rubric
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <Button size="sm" variant="secondary" className="mt-1" onClick={() => setOpen(true)}>
          Change rubric<span className="sr-only">{` of ${name}`}</span>
        </Button>
      )}
    </div>
  )
}
