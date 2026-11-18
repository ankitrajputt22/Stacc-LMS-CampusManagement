import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  type BlueprintFillResult,
  type BlueprintRowShortfall,
  buildExamPaperSets,
  fillBlueprintShortfall,
  getBlueprintShortfall,
} from '../api/lmsExamPapersApi'
import { queryKeys } from '../api/queryKeys'
import { Alert, Button, SelectField, TextField, textLinkClasses } from '../ui'

function refusal(error: unknown, failed: string): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return 'You don’t have permission to do this for this course.'
  }
  const message = getApiError(error)?.message
  return message && status !== null && status !== 404 ? message : failed
}

export function PaperSetsForm({ blueprintId, onDone }: { blueprintId: number; onDone: (message: string) => void }) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [sets, setSets] = useState('2')
  const [distinct, setDistinct] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const made = await buildExamPaperSets(blueprintId, { title: title.trim() || null, sets: Number(sets), distinct })
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
      onDone(`Built ${made.length} sets as draft papers.`)
    } catch (failure) {
      setError(refusal(failure, 'Couldn’t build the sets. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form noValidate onSubmit={submit} className="grid gap-3 rounded-control border border-line p-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Title (optional)" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
        <SelectField label="Sets" value={sets} onChange={(event) => setSets(event.target.value)}>
          <option value="2">2 (A, B)</option>
          <option value="3">3 (A to C)</option>
          <option value="4">4 (A to D)</option>
        </SelectField>
      </div>
      <label className="inline-flex items-center gap-2 text-sm text-ink">
        <input type="checkbox" checked={distinct} onChange={(event) => setDistinct(event.target.checked)} />
        No question in two sets
      </label>
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>{busy ? 'Building…' : 'Build sets'}</Button>
        <Button type="button" variant="secondary" onClick={() => onDone('')} disabled={busy}>Cancel</Button>
      </div>
    </form>
  )
}

export function BlueprintShortfall({ lmsCourseId, blueprintId, onClose }: { lmsCourseId: number; blueprintId: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<BlueprintRowShortfall[] | null>(null)
  const [filled, setFilled] = useState<BlueprintFillResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function check() {
    setBusy(true)
    setError(null)
    try {
      setRows(await getBlueprintShortfall(blueprintId))
    } catch (failure) {
      setError(refusal(failure, 'Couldn’t check the bank. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  async function fill() {
    setBusy(true)
    setError(null)
    try {
      setFilled(await fillBlueprintShortfall(blueprintId))
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    } catch (failure) {
      setError(refusal(failure, 'AI couldn’t draft the questions. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  const missing = rows?.reduce((sum, row) => sum + row.missing, 0) ?? 0
  return (
    <div className="grid gap-3 rounded-control border border-line p-3 text-sm">
      {rows === null ? (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" onClick={() => void check()} disabled={busy}>{busy ? 'Checking…' : 'Check the bank'}</Button>
          <Button size="sm" variant="secondary" onClick={onClose}>Close</Button>
        </div>
      ) : (
        <>
          <ul className="space-y-1">
            {rows.map((row) => (
              <li key={row.position}>
                Row {row.position}
                {row.sectionTitle && ` (${row.sectionTitle})`}: needs {row.needed}, the bank has {row.available}
                {row.missing > 0 ? ` — ${row.missing} missing` : ' — enough'}
              </li>
            ))}
          </ul>
          {filled ? (
            <Alert tone="success">
              <p>
                AI drafted {filled.rows.reduce((sum, row) => sum + row.added, 0)} questions into the bank as Needs review.
                Review them, then build the paper.
              </p>
              {filled.note && <p>{filled.note}</p>}
              <Link to={`/teaching/${lmsCourseId}/questions`} className={textLinkClasses}>Review them in the question bank</Link>
            </Alert>
          ) : missing > 0 ? (
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={() => void fill()} disabled={busy}>
                {busy ? 'Drafting…' : 'Draft the missing questions with AI'}
              </Button>
              <Button size="sm" variant="secondary" onClick={onClose}>Close</Button>
            </div>
          ) : (
            <Button size="sm" variant="secondary" onClick={onClose}>Close</Button>
          )}
        </>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  )
}
