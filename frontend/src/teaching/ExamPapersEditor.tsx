import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  addExamBlueprint,
  addExamPaper,
  archiveExamBlueprint,
  buildExamPaper,
  editExamBlueprint,
  getExamBlueprints,
  getExamPapers,
} from '../api/lmsExamPapersApi'
import { getFacultyOutcomes } from '../api/lmsOutcomesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { BLOOM_LEVELS } from '../courses/bloomText'
import { SectionState } from '../courses/SectionState'
import type {
  BloomLevel,
  ExamBlueprint,
  ExamBlueprintRowInput,
  ExamPaperDetailsInput,
  ExamPaperStatus,
  LmsOutcome,
  QuestionDifficulty,
  QuestionType,
} from '../types/lms'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField, textLinkClasses } from '../ui'
import { blueprintRowText, PAPER_FORBIDDEN, paperFacts, paperRefusal, paperStatusLabel, parseMarks } from './examPaperText'
import { DIFFICULTIES, QUESTION_TYPES } from './questionText'

export function PaperStatusBadge({ status }: { status: ExamPaperStatus }) {
  const tone = status === 'FINAL' ? 'success' : status === 'ARCHIVED' ? 'neutral' : 'warning'
  return <Badge tone={tone}>{paperStatusLabel(status)}</Badge>
}

type RowDraft = {
  sectionTitle: string
  questionType: QuestionType | ''
  difficulty: QuestionDifficulty | ''
  bloomLevel: BloomLevel | ''
  outcomeId: string
  topic: string
  questionCount: string
  marksEach: string
}

const EMPTY_ROW: RowDraft = {
  sectionTitle: '',
  questionType: '',
  difficulty: '',
  bloomLevel: '',
  outcomeId: '',
  topic: '',
  questionCount: '1',
  marksEach: '1',
}

function draftOf(blueprint: ExamBlueprint | null): { title: string; rows: RowDraft[] } {
  if (!blueprint) {
    return { title: '', rows: [{ ...EMPTY_ROW }] }
  }
  return {
    title: blueprint.title,
    rows: blueprint.rows.map((row) => ({
      sectionTitle: row.sectionTitle ?? '',
      questionType: row.questionType ?? '',
      difficulty: row.difficulty ?? '',
      bloomLevel: row.bloomLevel ?? '',
      outcomeId: row.outcomeId === null ? '' : String(row.outcomeId),
      topic: row.topic ?? '',
      questionCount: String(row.questionCount),
      marksEach: String(row.marksEach),
    })),
  }
}

/** The blueprint's rows as the API takes them, or the first problem in words. */
function rowsOf(rows: RowDraft[]): ExamBlueprintRowInput[] | string {
  const inputs: ExamBlueprintRowInput[] = []
  for (const [index, row] of rows.entries()) {
    const count = Number(row.questionCount)
    const marks = parseMarks(row.marksEach)
    if (!/^\d{1,3}$/.test(row.questionCount.trim()) || count < 1 || count > 100) {
      return `Row ${index + 1}: enter a number of questions from 1 to 100.`
    }
    if (marks === null) {
      return `Row ${index + 1}: enter marks greater than zero, with at most 2 decimal places.`
    }
    inputs.push({
      sectionTitle: row.sectionTitle.trim() || null,
      questionType: row.questionType || null,
      difficulty: row.difficulty || null,
      bloomLevel: row.bloomLevel || null,
      outcomeId: row.outcomeId === '' ? null : Number(row.outcomeId),
      topic: row.topic.trim() || null,
      questionCount: count,
      marksEach: marks,
    })
  }
  return inputs
}

/** Adding or editing a blueprint: a title and rows, each with Move up, Move down, and Remove. */
function BlueprintForm({
  lmsCourseId,
  blueprint,
  outcomes,
  onDone,
}: {
  lmsCourseId: number
  blueprint: ExamBlueprint | null
  outcomes: LmsOutcome[]
  onDone: () => void
}) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState(() => draftOf(blueprint))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function changeRow(index: number, change: Partial<RowDraft>) {
    setDraft((current) => ({ ...current, rows: current.rows.map((row, at) => (at === index ? { ...row, ...change } : row)) }))
  }

  function moveRow(index: number, offset: number) {
    setDraft((current) => {
      const rows = [...current.rows]
      const [row] = rows.splice(index, 1)
      rows.splice(index + offset, 0, row)
      return { ...current, rows }
    })
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft.title.trim() === '') {
      setError('Enter a title.')
      return
    }
    const rows = rowsOf(draft.rows)
    if (typeof rows === 'string') {
      setError(rows)
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (blueprint) {
        await editExamBlueprint(blueprint.id, draft.title.trim(), rows)
      } else {
        await addExamBlueprint(lmsCourseId, draft.title.trim(), rows)
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
      onDone()
    } catch (refused) {
      setError(paperRefusal(refused, 'Couldn’t save the blueprint. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="space-y-4" onSubmit={(event) => void submit(event)} noValidate>
      <TextField
        label="Blueprint title"
        value={draft.title}
        maxLength={200}
        onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))}
      />
      <ol className="space-y-4">
        {draft.rows.map((row, index) => (
          <li key={index} className="space-y-3 rounded-md border border-line p-3">
            <p className="text-sm font-semibold text-ink">Row {index + 1}</p>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <TextField
                label={`Section, row ${index + 1}`}
                hint="Shown as a heading on the paper."
                value={row.sectionTitle}
                maxLength={100}
                onChange={(event) => changeRow(index, { sectionTitle: event.target.value })}
              />
              <TextField
                label={`Number of questions, row ${index + 1}`}
                inputMode="numeric"
                value={row.questionCount}
                onChange={(event) => changeRow(index, { questionCount: event.target.value })}
              />
              <TextField
                label={`Marks each, row ${index + 1}`}
                inputMode="decimal"
                value={row.marksEach}
                onChange={(event) => changeRow(index, { marksEach: event.target.value })}
              />
              <SelectField
                label={`Question type, row ${index + 1}`}
                value={row.questionType}
                onChange={(event) => changeRow(index, { questionType: event.target.value as QuestionType | '' })}
              >
                <option value="">Any type</option>
                {QUESTION_TYPES.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </SelectField>
              <SelectField
                label={`Difficulty, row ${index + 1}`}
                value={row.difficulty}
                onChange={(event) => changeRow(index, { difficulty: event.target.value as QuestionDifficulty | '' })}
              >
                <option value="">Any difficulty</option>
                {DIFFICULTIES.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </SelectField>
              <SelectField
                label={`Bloom level, row ${index + 1}`}
                value={row.bloomLevel}
                onChange={(event) => changeRow(index, { bloomLevel: event.target.value as BloomLevel | '' })}
              >
                <option value="">Any level</option>
                {BLOOM_LEVELS.map((choice) => (
                  <option key={choice.value} value={choice.value}>
                    {choice.label}
                  </option>
                ))}
              </SelectField>
              <SelectField
                label={`Outcome, row ${index + 1}`}
                value={row.outcomeId}
                onChange={(event) => changeRow(index, { outcomeId: event.target.value })}
              >
                <option value="">Any outcome</option>
                {outcomes.map((outcome) => (
                  <option key={outcome.id} value={outcome.id}>
                    {`${outcome.code}${outcome.status === 'ARCHIVED' ? ' (archived)' : ''}`}
                  </option>
                ))}
              </SelectField>
              <TextField
                label={`Topic, row ${index + 1}`}
                hint="As written in the question bank."
                value={row.topic}
                maxLength={100}
                onChange={(event) => changeRow(index, { topic: event.target.value })}
              />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" size="sm" disabled={index === 0} onClick={() => moveRow(index, -1)}>
                Move up<span className="sr-only">{` row ${index + 1}`}</span>
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={index === draft.rows.length - 1}
                onClick={() => moveRow(index, 1)}
              >
                Move down<span className="sr-only">{` row ${index + 1}`}</span>
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={draft.rows.length === 1}
                onClick={() => setDraft((current) => ({ ...current, rows: current.rows.filter((_, at) => at !== index) }))}
              >
                Remove<span className="sr-only">{` row ${index + 1}`}</span>
              </Button>
            </div>
          </li>
        ))}
      </ol>
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          disabled={draft.rows.length >= 30}
          onClick={() => setDraft((current) => ({ ...current, rows: [...current.rows, { ...EMPTY_ROW }] }))}
        >
          Add row
        </Button>
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : blueprint ? 'Save blueprint' : 'Create blueprint'}
        </Button>
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** A paper's title, duration, and instructions, for a new, built, or edited paper. */
export function PaperDetailsForm({
  initial,
  titleOptional = false,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial: ExamPaperDetailsInput
  titleOptional?: boolean
  submitLabel: string
  onSubmit: (input: ExamPaperDetailsInput) => Promise<void>
  onCancel: () => void
}) {
  const [title, setTitle] = useState(initial.title ?? '')
  const [duration, setDuration] = useState(initial.durationMinutes === null ? '' : String(initial.durationMinutes))
  const [instructions, setInstructions] = useState(initial.instructions ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const minutes = duration.trim() === '' ? null : Number(duration)
    if (!titleOptional && title.trim() === '') {
      setError('Enter a title.')
      return
    }
    if (minutes !== null && (!/^\d{1,4}$/.test(duration.trim()) || minutes < 1 || minutes > 1440)) {
      setError('Enter a duration from 1 to 1440 minutes, or leave it empty.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSubmit({ title: title.trim() || null, instructions: instructions.trim() || null, durationMinutes: minutes })
    } catch (refused) {
      setError(paperRefusal(refused, 'Couldn’t save the paper. Try again.'))
      setBusy(false)
    }
  }

  return (
    <form className="space-y-4" onSubmit={(event) => void submit(event)} noValidate>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Paper title"
          hint={titleOptional ? 'Leave empty to use the blueprint’s title.' : undefined}
          value={title}
          maxLength={200}
          onChange={(event) => setTitle(event.target.value)}
        />
        <TextField
          label="Duration in minutes"
          hint="Optional."
          inputMode="numeric"
          value={duration}
          onChange={(event) => setDuration(event.target.value)}
        />
      </div>
      <TextAreaField
        label="Instructions"
        hint="Plain text, printed above the questions."
        value={instructions}
        maxLength={5000}
        onChange={(event) => setInstructions(event.target.value)}
      />
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

type Open = { kind: 'new-blueprint' } | { kind: 'edit'; id: number } | { kind: 'build'; id: number } | { kind: 'archive'; id: number }

/**
 * The course's exam blueprints and papers: New blueprint (rows of section, filters, count, and marks), Edit, Build
 * paper (questions chosen at random from the reviewed bank), and Archive (confirmed); New paper for one built by hand,
 * and every paper with its status and size. Nothing is deleted.
 */
export function ExamPapersEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const blueprints = useApiQuery(queryKeys.lms.examBlueprints(lmsCourseId), (signal) => getExamBlueprints(lmsCourseId, signal))
  const papers = useApiQuery(queryKeys.lms.examPapers(lmsCourseId), (signal) => getExamPapers(lmsCourseId, signal))
  const outcomes = useApiQuery(queryKeys.lms.facultyOutcomes(lmsCourseId), (signal) => getFacultyOutcomes(lmsCourseId, signal))
  const [open, setOpen] = useState<Open | null>(null)
  const [addingPaper, setAddingPaper] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const outcomeList = outcomes.status === 'loaded' ? outcomes.data : []

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card
        title="Blueprints"
        description="A blueprint plans a paper: each row takes a number of reviewed bank questions of a kind, with the marks each carries."
        padding="none"
        action={
          open?.kind === 'new-blueprint' ? undefined : (
            <Button size="sm" onClick={() => setOpen({ kind: 'new-blueprint' })}>
              New blueprint
            </Button>
          )
        }
      >
        {open?.kind === 'new-blueprint' && (
          <div className="border-b border-line px-4 py-4 sm:px-5">
            <BlueprintForm lmsCourseId={lmsCourseId} blueprint={null} outcomes={outcomeList} onDone={() => setOpen(null)} />
          </div>
        )}
        {blueprints.status !== 'loaded' ? (
          <SectionState request={blueprints} what="blueprints" />
        ) : blueprints.data.length === 0 ? (
          <EmptyState title="No blueprints yet." description="Create a blueprint, then build papers from it." />
        ) : (
          <ol className="divide-y divide-line">
            {blueprints.data.map((blueprint) => (
              <li key={blueprint.id} className="space-y-3 px-4 py-4 sm:px-5">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-semibold text-ink">{blueprint.title}</h3>
                  {blueprint.status === 'ARCHIVED' && <Badge tone="neutral">Archived</Badge>}
                </div>
                <p className="text-sm text-ink-muted">{paperFacts({ ...blueprint, durationMinutes: null })}</p>
                <ol className="list-inside list-decimal space-y-1 text-sm text-ink">
                  {blueprint.rows.map((row) => (
                    <li key={row.position}>
                      {row.sectionTitle && <span className="font-medium">{row.sectionTitle}: </span>}
                      {blueprintRowText(row, outcomeList)}
                    </li>
                  ))}
                </ol>
                {blueprint.status === 'ACTIVE' && open?.kind === 'edit' && open.id === blueprint.id && (
                  <BlueprintForm lmsCourseId={lmsCourseId} blueprint={blueprint} outcomes={outcomeList} onDone={() => setOpen(null)} />
                )}
                {blueprint.status === 'ACTIVE' && open?.kind === 'build' && open.id === blueprint.id && (
                  <PaperDetailsForm
                    initial={{ title: null, instructions: null, durationMinutes: null }}
                    titleOptional
                    submitLabel="Build paper"
                    onCancel={() => setOpen(null)}
                    onSubmit={async (input) => {
                      const paper = await buildExamPaper(blueprint.id, input)
                      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
                      void navigate(`/teaching/${lmsCourseId}/papers/${paper.id}`)
                    }}
                  />
                )}
                {open?.kind === 'archive' && open.id === blueprint.id && (
                  <StatusConfirmation
                    question={`Archive “${blueprint.title}”?`}
                    detail="It is kept, but it can no longer change or build papers. Papers built from it stay as they are."
                    confirmLabel="Archive"
                    busyLabel="Archiving…"
                    describeRefusal={(status, message) =>
                      status === 403 ? PAPER_FORBIDDEN : message && status !== null && status < 500 ? message : 'Couldn’t archive. Try again.'
                    }
                    onCancel={() => setOpen(null)}
                    onConfirm={async () => {
                      await archiveExamBlueprint(blueprint.id)
                      setOpen(null)
                      setAnnouncement(`Archived ${blueprint.title}.`)
                      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
                    }}
                  />
                )}
                {blueprint.status === 'ACTIVE' && open === null && (
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" onClick={() => setOpen({ kind: 'build', id: blueprint.id })}>
                      Build paper<span className="sr-only">{` from ${blueprint.title}`}</span>
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setOpen({ kind: 'edit', id: blueprint.id })}>
                      Edit<span className="sr-only">{` ${blueprint.title}`}</span>
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setOpen({ kind: 'archive', id: blueprint.id })}>
                      Archive<span className="sr-only">{` ${blueprint.title}`}</span>
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>

      <Card
        title="Papers"
        padding="none"
        action={
          addingPaper ? undefined : (
            <Button size="sm" variant="secondary" onClick={() => setAddingPaper(true)}>
              New paper
            </Button>
          )
        }
      >
        {addingPaper && (
          <div className="border-b border-line px-4 py-4 sm:px-5">
            <PaperDetailsForm
              initial={{ title: null, instructions: null, durationMinutes: null }}
              submitLabel="Create paper"
              onCancel={() => setAddingPaper(false)}
              onSubmit={async (input) => {
                const paper = await addExamPaper(lmsCourseId, input)
                await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
                void navigate(`/teaching/${lmsCourseId}/papers/${paper.id}`)
              }}
            />
          </div>
        )}
        {papers.status !== 'loaded' ? (
          <SectionState request={papers} what="papers" />
        ) : papers.data.length === 0 ? (
          <EmptyState title="No papers yet." description="Build a paper from a blueprint, or create one and add questions by hand." />
        ) : (
          <ol className="divide-y divide-line">
            {papers.data.map((paper) => (
              <li key={paper.id} className="space-y-1.5 px-4 py-4 sm:px-5">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`/teaching/${lmsCourseId}/papers/${paper.id}`} className={`font-semibold ${textLinkClasses}`}>
                    {paper.title}
                  </Link>
                  <PaperStatusBadge status={paper.status} />
                </div>
                <p className="text-sm text-ink-muted">{paperFacts(paper)}</p>
              </li>
            ))}
          </ol>
        )}
      </Card>
    </div>
  )
}
