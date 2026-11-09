import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { gradeWithRubric } from '../api/lmsRubricsApi'
import type { LmsFacultySubmissionAttempt, LmsRubric, LmsSubmissionGrade } from '../types/lms'
import { Alert, Button, TextAreaField, TextField } from '../ui'

const POINTS = /^\d{1,6}(\.\d{1,2})?$/

type Score = { points: string; comment: string }

/**
 * Grades one attempt with the assignment's rubric: points per criterion (a level's button fills them in), an optional
 * comment each, and overall feedback. The total is the sum and becomes the marks. An earlier rubric grade fills the form.
 */
export function RubricGradeForm({
  attempt,
  rubric,
  onSaved,
}: {
  attempt: LmsFacultySubmissionAttempt
  rubric: LmsRubric
  onSaved: (grade: LmsSubmissionGrade) => void
}) {
  const previous = attempt.grade?.rubric ?? []
  const [scores, setScores] = useState<Record<number, Score>>(() =>
    Object.fromEntries(
      rubric.criteria.map((criterion) => {
        const earlier = previous.find((score) => score.criterion === criterion.title)
        return [criterion.id, { points: earlier ? String(earlier.points) : '', comment: earlier?.comment ?? '' }]
      }),
    ),
  )
  const [feedback, setFeedback] = useState(attempt.grade?.feedback ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const total = rubric.criteria.reduce((sum, criterion) => {
    const typed = scores[criterion.id].points.trim()
    return sum + (POINTS.test(typed) ? Number(typed) : 0)
  }, 0)

  function update(criterionId: number, change: Partial<Score>) {
    setScores((current) => ({ ...current, [criterionId]: { ...current[criterionId], ...change } }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const wrong = rubric.criteria.find((criterion) => {
      const typed = scores[criterion.id].points.trim()
      return !POINTS.test(typed) || Number(typed) > Number(criterion.maxPoints)
    })
    if (wrong) {
      setError(`${wrong.title}: enter points from 0 to ${wrong.maxPoints}, with at most 2 decimal places.`)
      return
    }
    setError(null)
    setBusy(true)
    try {
      const grade = await gradeWithRubric(
        attempt.attemptId,
        rubric.criteria.map((criterion) => ({
          criterionId: criterion.id,
          points: Number(scores[criterion.id].points),
          comment: scores[criterion.id].comment.trim() === '' ? null : scores[criterion.id].comment,
        })),
        feedback.trim() === '' ? null : feedback,
      )
      onSaved(grade)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const message = getApiError(failure)?.message
        setError(
          status === 403
            ? 'You don’t have permission to grade this assignment.'
            : message && status !== null && status < 500
              ? message
              : 'Couldn’t save the grade. Check your connection and try again.',
        )
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="mt-4 space-y-4" onSubmit={(event) => void submit(event)} noValidate>
      <p className="text-sm font-medium text-ink">{`Grade attempt ${attempt.attemptNumber} with the rubric “${rubric.title}”`}</p>
      {rubric.criteria.map((criterion) => (
        <fieldset key={criterion.id} className="space-y-2 rounded-control border border-line p-3">
          <legend className="px-1 text-sm font-medium text-ink">{`${criterion.title} (out of ${criterion.maxPoints})`}</legend>
          {criterion.description && <p className="text-sm text-ink-muted">{criterion.description}</p>}
          {criterion.levels.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {criterion.levels.map((level) => (
                <Button
                  key={`${level.title}-${level.points}`}
                  variant="secondary"
                  size="sm"
                  aria-pressed={scores[criterion.id].points.trim() !== '' && Number(scores[criterion.id].points) === Number(level.points)}
                  onClick={() => update(criterion.id, { points: String(level.points) })}
                >
                  {`${level.title} (${level.points})`}
                </Button>
              ))}
            </div>
          )}
          <div className="grid gap-3 sm:grid-cols-[10rem_1fr]">
            <TextField
              label={`Points for ${criterion.title}`}
              inputMode="decimal"
              value={scores[criterion.id].points}
              onChange={(event) => update(criterion.id, { points: event.target.value })}
            />
            <TextField
              label={`Comment on ${criterion.title} (optional)`}
              value={scores[criterion.id].comment}
              maxLength={2000}
              onChange={(event) => update(criterion.id, { comment: event.target.value })}
            />
          </div>
        </fieldset>
      ))}
      <p className="text-sm font-semibold text-ink" aria-live="polite">
        {`Total: ${total} / ${rubric.totalPoints}`}
      </p>
      <TextAreaField label="Overall feedback (optional)" rows={3} value={feedback} maxLength={10_000} onChange={(event) => setFeedback(event.target.value)} />
      {error && <Alert tone="error">{error}</Alert>}
      <Button type="submit" disabled={busy}>
        {busy ? 'Saving…' : attempt.grade ? 'Update grade' : 'Save grade'}
      </Button>
    </form>
  )
}
