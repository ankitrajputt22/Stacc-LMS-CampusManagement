import { type FormEvent, useState } from 'react'
import { setSubmissionRules } from '../api/lmsAssignmentsApi'
import { fromLocalInputValue, localTimeZoneName, toLocalInputValue } from '../courses/dates'
import { submissionRulesText } from '../courses/submissionRules'
import type { LmsFacultyAssignment } from '../types/lms'
import { Alert, Button, TextField } from '../ui'
import type { useContentChanges } from './useContentChanges'

type Changes = ReturnType<typeof useContentChanges>

/**
 * When an assignment takes work: an opening time, whether late work is accepted and until when, and an attempt limit.
 * Times are entered in the browser's own time zone. The backend checks them against the due date again.
 */
export function AssignmentSubmissionRules({ assignment, name, changes }: { assignment: LmsFacultyAssignment; name: string; changes: Changes }) {
  const [open, setOpen] = useState(false)
  const [opens, setOpens] = useState(assignment.availableFrom ? toLocalInputValue(assignment.availableFrom) : '')
  const [closes, setCloses] = useState(assignment.availableUntil ? toLocalInputValue(assignment.availableUntil) : '')
  const [allowLate, setAllowLate] = useState(assignment.allowLateSubmissions)
  const [attempts, setAttempts] = useState(assignment.maxAttempts === null ? '' : String(assignment.maxAttempts))
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const from = opens === '' ? null : fromLocalInputValue(opens)
    const until = closes === '' || !allowLate ? null : fromLocalInputValue(closes)
    const due = new Date(assignment.dueAt)
    if ((opens !== '' && from === null) || (closes !== '' && allowLate && until === null)) {
      setError('Enter complete dates and times, or leave them empty.')
      return
    }
    if (from && from > due) {
      setError('The opening time cannot be after the due date.')
      return
    }
    if (until && until < due) {
      setError('The closing time cannot be before the due date.')
      return
    }
    if (attempts.trim() !== '' && (!/^\d+$/.test(attempts.trim()) || Number(attempts) < 1 || Number(attempts) > 100)) {
      setError('Enter a whole number of attempts from 1 to 100, or leave it empty for unlimited.')
      return
    }
    setError(null)
    const result = await changes.run(
      () =>
        setSubmissionRules(assignment.id, {
          availableFrom: from ? from.toISOString() : null,
          availableUntil: until ? until.toISOString() : null,
          allowLateSubmissions: allowLate,
          maxAttempts: attempts.trim() === '' ? null : Number(attempts),
        }),
      { done: `Saved the submission rules of ${name}.`, showError: false },
    )
    if (result?.ok) {
      setOpen(false)
    } else if (result && result.message) {
      setError(result.message)
    }
  }

  return (
    <div className="mt-1.5">
      <p className="text-sm text-ink-muted">{submissionRulesText(assignment)}</p>
      {open ? (
        <form className="mt-2 space-y-3 rounded-control border border-line p-3" onSubmit={(event) => void save(event)} noValidate>
          <p className="text-sm text-ink-muted">{`Times are in your time zone (${localTimeZoneName()}).`}</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Opens (optional)" type="datetime-local" value={opens} onChange={(event) => setOpens(event.target.value)} />
            <TextField label="Attempts allowed (optional)" inputMode="numeric" value={attempts} hint="Empty means unlimited." onChange={(event) => setAttempts(event.target.value)} />
          </div>
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={allowLate} onChange={(event) => setAllowLate(event.target.checked)} />
            Accept late work (marked late)
          </label>
          {allowLate && (
            <TextField
              label="Late work accepted until (optional)"
              type="datetime-local"
              className="sm:max-w-xs"
              value={closes}
              hint="Empty means no final closing time."
              onChange={(event) => setCloses(event.target.value)}
            />
          )}
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="submit" size="sm" disabled={changes.busy}>
              Save rules
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <Button size="sm" variant="secondary" className="mt-1" onClick={() => setOpen(true)}>
          Submission rules<span className="sr-only">{` of ${name}`}</span>
        </Button>
      )}
    </div>
  )
}
