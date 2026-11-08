import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addGradeItem, getGradebook, publishGradeColumn } from '../api/lmsGradebookApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { cellText, gradeTypeLabel, matchesStudent } from '../courses/gradeText'
import { SectionState } from '../courses/SectionState'
import type { Gradebook, GradebookColumn } from '../types/lms'
import { Alert, Badge, Button, Card, DataTable, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

export const GRADEBOOK_FORBIDDEN =
  'You don’t have access to this course’s gradebook. If your access changed recently, sign out and sign in again.'

const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

type Row = Gradebook['students'][number]

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

/** Where a column's marks are given: the assignment's submissions, the quiz's results, or the manual item's page. */
function columnPath(lmsCourseId: number, column: GradebookColumn): string {
  switch (column.type) {
    case 'ASSIGNMENT':
      return `/teaching/${lmsCourseId}/assignments/${column.sourceId}/submissions`
    case 'QUIZ':
      return `/teaching/${lmsCourseId}/quizzes/${column.sourceId}/results`
    default:
      return `/teaching/${lmsCourseId}/gradebook/items/${column.sourceId}`
  }
}

function AddItemForm({ lmsCourseId, onDone }: { lmsCourseId: number; onDone: (message: string) => void }) {
  const [title, setTitle] = useState('')
  const [maxMarks, setMaxMarks] = useState('')
  const [errors, setErrors] = useState<{ title?: string; maxMarks?: string; form?: string | null }>({})
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: typeof errors = {}
    if (title.trim() === '') {
      found.title = 'Enter a title.'
    }
    if (!MARKS_PATTERN.test(maxMarks.trim()) || Number(maxMarks) <= 0) {
      found.maxMarks = 'Enter a number greater than zero, with at most 2 decimal places, below 1,000,000.'
    }
    setErrors(found)
    if (Object.keys(found).length > 0) {
      return
    }
    setBusy(true)
    try {
      await addGradeItem(lmsCourseId, { title: title.trim(), maxMarks: Number(maxMarks) })
      setTitle('')
      setMaxMarks('')
      onDone('Added the item. Enter marks from its page, then publish it.')
    } catch (failure) {
      setErrors({ form: refusalText(failure, 'Couldn’t add the item. Try again.') })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="grid gap-3 sm:grid-cols-[1fr_12rem_auto] sm:items-start" onSubmit={(event) => void submit(event)} noValidate>
      <TextField label="Item title" value={title} maxLength={200} error={errors.title} onChange={(event) => setTitle(event.target.value)} />
      <TextField label="Maximum marks" inputMode="decimal" value={maxMarks} error={errors.maxMarks} onChange={(event) => setMaxMarks(event.target.value)} />
      <Button type="submit" className="sm:mt-6" disabled={busy}>
        {busy ? 'Adding…' : 'Add item'}
      </Button>
      {errors.form && <Alert tone="error" className="sm:col-span-3">{errors.form}</Alert>}
    </form>
  )
}

/**
 * The course gradebook as faculty see it: every current student (by college ID, searchable) against every published
 * assignment, graded quiz, and manual item, with a total of what is graded. Columns are published to students one at a
 * time, confirmed first; nothing unpublishes. Marks are given where they belong: assignment grading, quiz results, or a
 * manual item's own page.
 */
export function GradebookEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const queryClient = useQueryClient()
  const gradebook = useApiQuery(queryKeys.lms.gradebook(lmsCourseId), (signal) => getGradebook(lmsCourseId, signal))
  const [search, setSearch] = useState('')
  const [publishing, setPublishing] = useState<GradebookColumn | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  if (gradebook.status !== 'loaded') {
    return (
      <Card title="Gradebook">
        <SectionState request={gradebook} what="the gradebook" />
      </Card>
    )
  }
  const data = gradebook.data
  const rows = data.students.filter((student) => matchesStudent(student.studentLoginId, search))

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card title="Gradebook" description="Marks of the current students. A column shows on students’ Grades pages once it is published." padding="none">
        <form role="search" aria-label="Find a student" className="border-b border-line px-4 py-4 sm:px-5" onSubmit={(event) => event.preventDefault()}>
          <TextField label="Search by college ID" className="max-w-sm" value={search} onChange={(event) => setSearch(event.target.value)} />
        </form>
        {data.columns.length === 0 ? (
          <EmptyState title="Nothing to grade yet." description="Published assignments, graded quizzes, and manual items appear here." />
        ) : (
          <DataTable<Row>
            caption="Gradebook"
            rows={rows}
            rowKey={(row) => row.courseEnrollmentId}
            empty={<EmptyState title={data.students.length === 0 ? 'No students are enrolled yet.' : 'No student matches this search.'} />}
            columns={[
              { key: 'student', header: 'Student', primary: true, cell: (row) => row.studentLoginId },
              ...data.columns.map((column, index) => ({
                key: `${column.type}-${column.sourceId}`,
                header: `${column.title} (/${column.maxMarks})`,
                align: 'right' as const,
                cell: (row: Row) => cellText(row.cells[index]),
              })),
              {
                key: 'total',
                header: 'Total',
                align: 'right' as const,
                cell: (row: Row) => (Number(row.totalOutOf) === 0 ? '—' : `${row.totalMarks} / ${row.totalOutOf}`),
              },
            ]}
          />
        )}
      </Card>

      <Card title="Columns" padding="none">
        {publishing && (
          <div className="px-4 pt-4 sm:px-5">
            <StatusConfirmation
              question={`Publish the grades of “${publishing.title}”?`}
              detail="Students will see this column, and their own marks in it, on their Grades page. It cannot be unpublished."
              confirmLabel="Publish grades"
              busyLabel="Publishing…"
              describeRefusal={(status, apiMessage) =>
                status === 403 ? GRADEBOOK_FORBIDDEN : apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t publish. Try again.'
              }
              onCancel={() => setPublishing(null)}
              onConfirm={async () => {
                await publishGradeColumn(lmsCourseId, publishing.type, publishing.sourceId)
                setPublishing(null)
                await changed('Published the grades.')
              }}
            />
          </div>
        )}
        {data.columns.length === 0 ? (
          <EmptyState title="No columns yet." />
        ) : (
          <ul className="divide-y divide-line">
            {data.columns.map((column) => (
              <li key={`${column.type}-${column.sourceId}`} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <Link to={columnPath(lmsCourseId, column)} className={`font-medium ${textLinkClasses}`}>
                    {column.title}
                  </Link>
                  <Badge tone="brand">{gradeTypeLabel(column.type)}</Badge>
                  <span className="text-sm text-ink-muted">out of {column.maxMarks}</span>
                  {column.published ? (
                    <Badge tone="success">Published{column.publishedAt ? ` ${formatDateTime(column.publishedAt)}` : ''}</Badge>
                  ) : (
                    <Badge tone="warning">Not published</Badge>
                  )}
                </div>
                {!column.published && (
                  <Button variant="secondary" size="sm" onClick={() => setPublishing(column)}>
                    Publish grades<span className="sr-only">{`: ${column.title}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="Add a manual item" description="For marks given outside Stacc, such as a class test on paper.">
        <AddItemForm lmsCourseId={lmsCourseId} onDone={(message) => void changed(message)} />
      </Card>
    </div>
  )
}
