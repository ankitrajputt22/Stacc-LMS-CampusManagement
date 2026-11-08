import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { archiveRubric, createRubric, editRubric, getRubrics } from '../api/lmsRubricsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { RubricView } from '../courses/RubricView'
import { SectionState } from '../courses/SectionState'
import type { LmsRubric, LmsRubricInput } from '../types/lms'
import { Alert, Badge, Button, Card, TextAreaField, TextField } from '../ui'
import {
  checkRubric,
  type CriterionDraft,
  draftOfRubric,
  draftTotal,
  emptyCriterion,
  emptyRubric,
  MAX_CRITERIA,
  MAX_LEVELS,
  type RubricDraft,
} from './rubricDraft'

const FORBIDDEN = 'You don’t have access to change this course’s rubrics. If your access changed recently, sign out and sign in again.'

function refusalText(failure: unknown, fallback: string): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return FORBIDDEN
  }
  const message = getApiError(failure)?.message
  return message && status !== null && status < 500 ? message : fallback
}

/** One criterion of the form with its levels. */
function CriterionFields({
  index,
  criterion,
  count,
  onChange,
  onRemove,
}: {
  index: number
  criterion: CriterionDraft
  count: number
  onChange: (criterion: CriterionDraft) => void
  onRemove: () => void
}) {
  const number = index + 1
  return (
    <fieldset className="space-y-3 rounded-control border border-line p-3">
      <legend className="px-1 text-sm font-medium text-ink">Criterion {number}</legend>
      <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
        <TextField label={`Criterion ${number} title`} value={criterion.title} maxLength={200} onChange={(event) => onChange({ ...criterion, title: event.target.value })} />
        <TextField
          label={`Criterion ${number} maximum points`}
          inputMode="decimal"
          value={criterion.maxPoints}
          onChange={(event) => onChange({ ...criterion, maxPoints: event.target.value })}
        />
      </div>
      <TextField
        label={`Criterion ${number} description (optional)`}
        value={criterion.description}
        maxLength={1000}
        onChange={(event) => onChange({ ...criterion, description: event.target.value })}
      />
      {criterion.levels.map((level, levelIndex) => (
        <div key={levelIndex} className="grid gap-2 sm:grid-cols-[1fr_8rem_auto] sm:items-end">
          <TextField
            label={`Criterion ${number}, level ${levelIndex + 1} title`}
            value={level.title}
            maxLength={100}
            onChange={(event) =>
              onChange({ ...criterion, levels: criterion.levels.map((entry, at) => (at === levelIndex ? { ...entry, title: event.target.value } : entry)) })
            }
          />
          <TextField
            label={`Criterion ${number}, level ${levelIndex + 1} points`}
            inputMode="decimal"
            value={level.points}
            onChange={(event) =>
              onChange({ ...criterion, levels: criterion.levels.map((entry, at) => (at === levelIndex ? { ...entry, points: event.target.value } : entry)) })
            }
          />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onChange({ ...criterion, levels: criterion.levels.filter((_, at) => at !== levelIndex) })}
          >
            Remove level<span className="sr-only">{` ${levelIndex + 1} of criterion ${number}`}</span>
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        {criterion.levels.length < MAX_LEVELS && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onChange({ ...criterion, levels: [...criterion.levels, { title: '', description: '', points: '' }] })}
          >
            Add level<span className="sr-only">{` to criterion ${number}`}</span>
          </Button>
        )}
        {count > 1 && (
          <Button variant="secondary" size="sm" onClick={onRemove}>
            Remove criterion<span className="sr-only">{` ${number}`}</span>
          </Button>
        )}
      </div>
    </fieldset>
  )
}

/** A rubric's title, description, and criteria with optional levels; the total updates as points are typed. */
export function RubricForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: RubricDraft
  submitLabel: string
  onSubmit: (input: LmsRubricInput) => Promise<void>
  onCancel?: () => void
}) {
  const [draft, setDraft] = useState(initial)
  const [errors, setErrors] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const checked = checkRubric(draft)
    setErrors(checked.errors)
    if (checked.input === null) {
      return
    }
    setBusy(true)
    try {
      await onSubmit(checked.input)
    } catch (failure) {
      const message = refusalText(failure, 'Couldn’t save the rubric. Try again.')
      setErrors(message ? [message] : [])
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="space-y-4" onSubmit={(event) => void submit(event)} noValidate>
      <TextField label="Rubric title" value={draft.title} maxLength={200} onChange={(event) => setDraft({ ...draft, title: event.target.value })} />
      <TextAreaField
        label="Description (optional)"
        rows={2}
        value={draft.description}
        maxLength={5000}
        onChange={(event) => setDraft({ ...draft, description: event.target.value })}
      />
      {draft.criteria.map((criterion, index) => (
        <CriterionFields
          key={index}
          index={index}
          criterion={criterion}
          count={draft.criteria.length}
          onChange={(changed) => setDraft({ ...draft, criteria: draft.criteria.map((entry, at) => (at === index ? changed : entry)) })}
          onRemove={() => setDraft({ ...draft, criteria: draft.criteria.filter((_, at) => at !== index) })}
        />
      ))}
      <div className="flex flex-wrap items-center gap-3">
        {draft.criteria.length < MAX_CRITERIA && (
          <Button variant="secondary" size="sm" onClick={() => setDraft({ ...draft, criteria: [...draft.criteria, emptyCriterion()] })}>
            Add criterion
          </Button>
        )}
        <p className="text-sm text-ink" aria-live="polite">
          Total: {draftTotal(draft)} points
        </p>
      </div>
      {errors.length > 0 && (
        <Alert tone="error">
          <ul className="list-disc pl-5">
            {errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </Alert>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}

/**
 * The course's rubrics as faculty manage them: New rubric, each rubric's criteria and levels, Edit while it has not been
 * used for grading, and Archive, confirmed first. Nothing is deleted. An assignment chooses its rubric in Assignments.
 */
export function RubricsEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const queryClient = useQueryClient()
  const rubrics = useApiQuery(queryKeys.lms.rubrics(lmsCourseId), (signal) => getRubrics(lmsCourseId, signal))
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<number | null>(null)
  const [archiving, setArchiving] = useState<LmsRubric | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {adding && (
        <Card title="New rubric" description="An assignment can use a rubric whose total equals its maximum marks.">
          <RubricForm
            initial={emptyRubric()}
            submitLabel="Create rubric"
            onCancel={() => setAdding(false)}
            onSubmit={async (input) => {
              await createRubric(lmsCourseId, input)
              setAdding(false)
              await changed('Created the rubric.')
            }}
          />
        </Card>
      )}
      {archiving && (
        <StatusConfirmation
          question={`Archive “${archiving.title}”?`}
          detail="Assignments that use it keep it, and grades keep their scores. It can no longer change or be chosen. Nothing restores it."
          confirmLabel="Archive rubric"
          busyLabel="Archiving…"
          describeRefusal={(status, apiMessage) => (status === 403 ? FORBIDDEN : apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t archive. Try again.')}
          onCancel={() => setArchiving(null)}
          onConfirm={async () => {
            await archiveRubric(archiving.id)
            setArchiving(null)
            await changed('Archived the rubric.')
          }}
        />
      )}
      <Card
        title="Rubrics"
        padding="none"
        action={
          adding ? undefined : (
            <Button size="sm" onClick={() => setAdding(true)}>
              New rubric
            </Button>
          )
        }
      >
        {rubrics.status !== 'loaded' ? (
          <SectionState request={rubrics} what="rubrics" />
        ) : rubrics.data.length === 0 ? (
          <EmptyState title="No rubrics yet." description="Create a rubric, then choose it for an assignment under Assignments." />
        ) : (
          <ul className="divide-y divide-line">
            {rubrics.data.map((rubric) => (
              <li key={rubric.id} className="space-y-3 px-4 py-4 sm:px-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold text-ink">{rubric.title}</h3>
                    <span className="text-sm text-ink-muted">{rubric.totalPoints} points</span>
                    {rubric.status === 'ARCHIVED' && <Badge tone="neutral">Archived</Badge>}
                    {rubric.inUse && <Badge tone="info">Used for grading</Badge>}
                  </div>
                  {rubric.status === 'ACTIVE' && editing !== rubric.id && (
                    <div className="flex flex-wrap gap-2">
                      {!rubric.inUse && (
                        <Button variant="secondary" size="sm" onClick={() => setEditing(rubric.id)}>
                          Edit<span className="sr-only">{` rubric ${rubric.title}`}</span>
                        </Button>
                      )}
                      <Button variant="secondary" size="sm" onClick={() => setArchiving(rubric)}>
                        Archive<span className="sr-only">{` rubric ${rubric.title}`}</span>
                      </Button>
                    </div>
                  )}
                </div>
                {editing === rubric.id ? (
                  <RubricForm
                    initial={draftOfRubric(rubric)}
                    submitLabel="Save rubric"
                    onCancel={() => setEditing(null)}
                    onSubmit={async (input) => {
                      await editRubric(rubric.id, input)
                      setEditing(null)
                      await changed('Saved the rubric.')
                    }}
                  />
                ) : (
                  <RubricView rubric={rubric} />
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
