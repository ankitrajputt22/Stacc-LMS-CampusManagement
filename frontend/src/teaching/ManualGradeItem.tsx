import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { editGradeItem, enterGrade, getGradebook } from '../api/lmsGradebookApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import type { Gradebook, GradebookColumn } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, TextField } from '../ui'
import { GRADEBOOK_FORBIDDEN } from './GradebookEditor'

const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

function refusalText(failure: unknown, fallback: string): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return GRADEBOOK_FORBIDDEN
  }
  const message = getApiError(failure)?.message
  return message && status !== null && status < 500 ? message : fallback
}

/** One student's marks and feedback for the item, saved on its own. */
function StudentMarks({
  itemId,
  column,
  student,
  index,
  onSaved,
}: {
  itemId: number
  column: GradebookColumn
  student: Gradebook['students'][number]
  index: number
  onSaved: (message: string) => void
}) {
  const cell = student.cells[index]
  const [marks, setMarks] = useState(cell.marks === null ? '' : String(cell.marks))
  const [feedback, setFeedback] = useState(cell.feedback ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!MARKS_PATTERN.test(marks.trim()) || Number(marks) > column.maxMarks) {
      setError(`Enter marks from 0 to ${column.maxMarks}, with at most 2 decimal places.`)
      return
    }
    setError(null)
    setBusy(true)
    try {
      await enterGrade(itemId, student.courseEnrollmentId, Number(marks), feedback.trim() === '' ? null : feedback)
      onSaved(`Saved the marks of ${student.studentLoginId}.`)
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t save the marks. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="px-4 py-4 sm:px-5">
      <form className="grid gap-3 sm:grid-cols-[10rem_8rem_1fr_auto] sm:items-start" onSubmit={(event) => void save(event)} noValidate>
        <p className="pt-1 text-sm font-medium text-ink sm:pt-8">{student.studentLoginId}</p>
        <TextField label={`Marks for ${student.studentLoginId}`} inputMode="decimal" value={marks} error={error} onChange={(event) => setMarks(event.target.value)} />
        <TextField label={`Feedback for ${student.studentLoginId} (optional)`} value={feedback} maxLength={10_000} onChange={(event) => setFeedback(event.target.value)} />
        <Button type="submit" variant="secondary" className="sm:mt-6" disabled={busy}>
          {busy ? 'Saving…' : 'Save'}
          <span className="sr-only">{` marks for ${student.studentLoginId}`}</span>
        </Button>
      </form>
    </li>
  )
}

function ItemSettings({ itemId, column, onSaved }: { itemId: number; column: GradebookColumn; onSaved: (message: string) => void }) {
  const [title, setTitle] = useState(column.title)
  const [maxMarks, setMaxMarks] = useState(String(column.maxMarks))
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (title.trim() === '' || !MARKS_PATTERN.test(maxMarks.trim()) || Number(maxMarks) <= 0) {
      setError('Enter a title and maximum marks greater than zero, with at most 2 decimal places.')
      return
    }
    setError(null)
    try {
      await editGradeItem(itemId, { title: title.trim(), maxMarks: Number(maxMarks) })
      onSaved('Saved the item.')
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t save the item. Try again.'))
    }
  }

  return (
    <form className="grid gap-3 sm:grid-cols-[1fr_12rem_auto] sm:items-start" onSubmit={(event) => void save(event)} noValidate>
      <TextField label="Item title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
      <TextField label="Maximum marks" inputMode="decimal" value={maxMarks} onChange={(event) => setMaxMarks(event.target.value)} />
      <Button type="submit" variant="secondary" className="sm:mt-6">
        Save item
      </Button>
      {error && <Alert tone="error" className="sm:col-span-3">{error}</Alert>}
    </form>
  )
}

/** A manual gradebook item: its title and maximum, and each current student's marks and feedback. */
export function ManualGradeItem({ lmsCourseId, itemId }: { lmsCourseId: number; itemId: number }) {
  const queryClient = useQueryClient()
  const gradebook = useApiQuery(queryKeys.lms.gradebook(lmsCourseId), (signal) => getGradebook(lmsCourseId, signal))
  const [announcement, setAnnouncement] = useState('')

  async function saved(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  if (gradebook.status !== 'loaded') {
    return (
      <Card title="Grade item">
        <SectionState request={gradebook} what="the gradebook" />
      </Card>
    )
  }
  const index = gradebook.data.columns.findIndex((column) => column.type === 'MANUAL' && column.sourceId === itemId)
  if (index < 0) {
    return (
      <Card title="Grade item">
        <EmptyState title="Item unavailable" description="This item could not be found in the course’s gradebook." />
      </Card>
    )
  }
  const column = gradebook.data.columns[index]
  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card
        title={column.title}
        description={`Out of ${column.maxMarks}.`}
        action={
          <ButtonLink to={`/teaching/${lmsCourseId}/gradebook`} variant="secondary" size="sm">
            Back to the gradebook
          </ButtonLink>
        }
      >
        <div className="space-y-4">
          {column.published ? <Badge tone="success">Published</Badge> : <Badge tone="warning">Not published</Badge>}
          <ItemSettings key={`${column.title}-${column.maxMarks}`} itemId={itemId} column={column} onSaved={(message) => void saved(message)} />
        </div>
      </Card>
      <Card title="Marks" padding="none">
        {gradebook.data.students.length === 0 ? (
          <EmptyState title="No students are enrolled yet." />
        ) : (
          <ul className="divide-y divide-line">
            {gradebook.data.students.map((student) => (
              <StudentMarks
                key={`${student.courseEnrollmentId}-${student.cells[index].marks ?? ''}`}
                itemId={itemId}
                column={column}
                student={student}
                index={index}
                onSaved={(message) => void saved(message)}
              />
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
