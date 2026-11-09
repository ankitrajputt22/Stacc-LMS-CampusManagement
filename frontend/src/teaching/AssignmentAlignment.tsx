import { useState } from 'react'
import { alignAssignment, getFacultyOutcomes } from '../api/lmsOutcomesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { alignmentText, BLOOM_LEVELS } from '../courses/bloomText'
import type { BloomLevel, LmsFacultyAssignment } from '../types/lms'
import { Button, SelectField } from '../ui'
import type { useContentChanges } from './useContentChanges'

type Changes = ReturnType<typeof useContentChanges>

/**
 * An assignment's alignment: which course outcomes it assesses and its Bloom level. Edit offers the course's active
 * outcomes (an archived one already chosen stays offered) and the six Bloom levels; Save sends both. Only the alignment
 * route changes; the assignment's definition is untouched.
 */
export function AssignmentAlignment({
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
  const outcomes = useApiQuery(open ? queryKeys.lms.facultyOutcomes(lmsCourseId) : null, (signal) =>
    getFacultyOutcomes(lmsCourseId, signal),
  )
  const [chosen, setChosen] = useState<Set<number>>(() => new Set(assignment.outcomes.map((outcome) => outcome.id)))
  const [bloom, setBloom] = useState<BloomLevel | ''>(assignment.bloomLevel ?? '')

  const choices =
    outcomes.status === 'loaded'
      ? outcomes.data.filter((outcome) => outcome.status === 'ACTIVE' || chosen.has(outcome.id))
      : []

  return (
    <div className="mt-1.5">
      <p className="text-sm text-ink-muted">{alignmentText(assignment)}</p>
      {open ? (
        <fieldset className="mt-2 rounded-control border border-line p-3">
          <legend className="px-1 text-sm font-medium text-ink">Alignment of {name}</legend>
          {outcomes.status === 'loading' && <p className="text-sm text-ink-muted">Loading outcomes…</p>}
          {outcomes.status === 'loaded' && choices.length === 0 && (
            <p className="text-sm text-ink-muted">This course has no outcomes yet. Add them under Outcomes.</p>
          )}
          <ul className="space-y-1">
            {choices.map((outcome) => (
              <li key={outcome.id}>
                <label className="flex items-start gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    className="mt-1"
                    checked={chosen.has(outcome.id)}
                    onChange={(event) => {
                      const next = new Set(chosen)
                      if (event.target.checked) {
                        next.add(outcome.id)
                      } else {
                        next.delete(outcome.id)
                      }
                      setChosen(next)
                    }}
                  />
                  <span>
                    <span className="font-semibold">{outcome.code}</span> {outcome.description}
                    {outcome.status === 'ARCHIVED' && ' (archived)'}
                  </span>
                </label>
              </li>
            ))}
          </ul>
          <SelectField label="Bloom level" className="mt-3" value={bloom} onChange={(event) => setBloom(event.target.value as BloomLevel | '')}>
            <option value="">None</option>
            {BLOOM_LEVELS.map((level) => (
              <option key={level.value} value={level.value}>
                {level.label}
              </option>
            ))}
          </SelectField>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <Button
              size="sm"
              onClick={async () => {
                const result = await changes.run(
                  () => alignAssignment(assignment.id, { bloomLevel: bloom === '' ? null : bloom, outcomeIds: [...chosen] }),
                  { done: `Saved the alignment of ${name}.` },
                )
                if (result?.ok) {
                  setOpen(false)
                }
              }}
            >
              Save alignment
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </fieldset>
      ) : (
        <Button size="sm" variant="secondary" className="mt-1" onClick={() => setOpen(true)}>
          Edit alignment<span className="sr-only"> of {name}</span>
        </Button>
      )}
    </div>
  )
}
