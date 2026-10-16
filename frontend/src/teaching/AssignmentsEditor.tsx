import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { archiveAssignment, createAssignment, publishAssignment, updateAssignment } from '../api/lmsAssignmentsApi'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { formatDateTime, isPast } from '../courses/dates'
import { marksText } from '../courses/marks'
import { PastDueLabel } from '../courses/PastDueLabel'
import type { LmsAssignmentInput, LmsFacultyAssignment } from '../types/lms'
import { ArchiveConfirmation } from './ArchiveConfirmation'
import { AssignmentForm } from './AssignmentForm'
import { ASSIGNMENTS_SECTION_ID, asOutcome, srContext } from './editorState'
import { StatusTag } from './StatusTag'
import { type ChangeResult, useContentChanges, useFocusAfterChanges } from './useContentChanges'
import { useFacultyAssignments } from './useFacultyAssignments'

type AssignmentFormState = { type: 'add' } | { type: 'edit'; id: number }

type Changes = ReturnType<typeof useContentChanges>

// A token issued before the account got the assignment permission does not carry it until the next sign-in.
const ASSIGNMENTS_FORBIDDEN =
  'You don’t have permission to manage assignments. If your access changed recently, sign out and sign in again.'

type AssignmentItemProps = {
  assignment: LmsFacultyAssignment
  now: number
  isEditing: boolean
  isConfirming: boolean
  setOpenForm: (form: AssignmentFormState | null) => void
  setConfirming: (id: number | null) => void
  changes: Changes
}

function AssignmentItem({ assignment, now, isEditing, isConfirming, setOpenForm, setConfirming, changes }: AssignmentItemProps) {
  const key = `assignment-${assignment.id}`
  const name = `assignment “${assignment.title}”`

  if (isEditing) {
    return (
      <AssignmentForm
        heading={`Edit ${name}`}
        note={
          assignment.status === 'PUBLISHED'
            ? 'This assignment is published. Students see your changes, including a new deadline, as soon as you save.'
            : undefined
        }
        initial={assignment}
        submitLabel="Save assignment"
        submittingLabel="Saving…"
        onSubmit={async (input: LmsAssignmentInput) => {
          const result = await changes.run(() => updateAssignment(assignment.id, input), {
            done: 'Assignment saved.',
            focus: () => [`${key}-heading`],
            showError: false,
          })
          if (result?.ok) {
            setOpenForm(null)
          }
          return asOutcome(result)
        }}
        onCancel={() => {
          setOpenForm(null)
          changes.focusNext([`${key}-edit`])
        }}
      />
    )
  }

  return (
    <article aria-labelledby={`${key}-title`} className="px-4 py-3 sm:px-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h3
          id={`${key}-title`}
          tabIndex={-1}
          data-focus-key={`${key}-heading`}
          className="text-sm font-semibold break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          {assignment.title}
        </h3>
        <StatusTag status={assignment.status} label="Assignment status" />
        {isPast(assignment.dueAt, now) && <PastDueLabel />}
      </div>
      <p className="mt-0.5 text-sm text-ink-muted">
        Due <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {marksText(assignment.maxMarks)}
      </p>
      <p className="text-xs text-ink-muted">
        {assignment.publishedAt ? (
          <>
            Published <time dateTime={assignment.publishedAt}>{formatDateTime(assignment.publishedAt)}</time>
          </>
        ) : (
          'Not published yet. Students cannot see it.'
        )}
      </p>
      <details className="mt-1.5">
        <summary className="inline-flex min-h-11 cursor-pointer items-center rounded-control text-sm font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0">
          Instructions{srContext(`of ${name}`)}
        </summary>
        {/* Plain text: React shows it as text, never as HTML. Line breaks are kept. */}
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{assignment.instructions}</p>
      </details>

      {isConfirming ? (
        <ArchiveConfirmation
          what={name}
          onConfirm={() => {
            setConfirming(null)
            void changes.run(() => archiveAssignment(assignment.id), {
              done: `Archived ${name}.`,
              focus: () => ['add-assignment', 'assignments-heading'],
            })
          }}
          onCancel={() => {
            setConfirming(null)
            changes.focusNext([`${key}-archive`])
          }}
        />
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          <ActionButton focusKey={`${key}-edit`} onClick={() => setOpenForm({ type: 'edit', id: assignment.id })}>
            Edit{srContext(name)}
          </ActionButton>
          {assignment.status === 'DRAFT' && (
            <ActionButton
              onClick={() =>
                void changes.run(() => publishAssignment(assignment.id), {
                  done: `Published ${name}. Students can see it now.`,
                  focus: () => [`${key}-heading`],
                })
              }
            >
              Publish{srContext(name)}
            </ActionButton>
          )}
          <ActionButton focusKey={`${key}-archive`} onClick={() => setConfirming(assignment.id)}>
            Archive{srContext(name)}
          </ActionButton>
        </div>
      )}
    </article>
  )
}

/**
 * The assignments of a course in the faculty page: drafts and published ones, earliest deadline first.
 * Each change goes to the backend, which checks access and the assignment permission again, and the list
 * then reloads. Archived assignments are kept but not listed. Nothing here is about students' work.
 */
export function AssignmentsEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const assignments = useFacultyAssignments(lmsCourseId)
  const changes = useContentChanges(assignments.refresh, ASSIGNMENTS_FORBIDDEN)
  const containerRef = useRef<HTMLElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const { hash } = useLocation()
  const [openForm, setOpenForm] = useState<AssignmentFormState | null>(null)
  const [confirming, setConfirming] = useState<number | null>(null)
  const [now] = useState(() => Date.now())
  const list = assignments.status === 'loaded' ? assignments.data : null
  const arrivedHere = useRef(false)

  useFocusAfterChanges(containerRef, list, changes.pendingFocus, changes.focusHandled)

  // Coming from the assignments overview (#assignments), go to this section once it has loaded.
  useEffect(() => {
    if (list !== null && hash === `#${ASSIGNMENTS_SECTION_ID}` && !arrivedHere.current) {
      arrivedHere.current = true
      headingRef.current?.focus()
    }
  }, [list, hash])

  async function add(input: LmsAssignmentInput) {
    const result: ChangeResult<LmsFacultyAssignment> | null = await changes.run(
      () => createAssignment(lmsCourseId, input),
      {
        done: 'Assignment added as a draft.',
        focus: (created) => [`assignment-${created.id}-heading`],
        showError: false,
      },
    )
    if (result?.ok) {
      setOpenForm(null)
    }
    return asOutcome(result)
  }

  return (
    <section
      ref={containerRef}
      id={ASSIGNMENTS_SECTION_ID}
      aria-labelledby="assignments-heading"
      aria-busy={changes.busy}
      className="mt-8 scroll-mt-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h2
            ref={headingRef}
            id="assignments-heading"
            tabIndex={-1}
            data-focus-key="assignments-heading"
            className="text-base font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            Assignments
          </h2>
          {changes.busy && <span className="text-sm text-ink-muted">Saving…</span>}
        </div>
        {list !== null && openForm?.type !== 'add' && (
          <div className="w-full sm:w-auto">
            <Button variant="secondary" data-focus-key="add-assignment" onClick={() => setOpenForm({ type: 'add' })}>
              Add assignment
            </Button>
          </div>
        )}
      </div>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        Work for students, with a deadline and maximum marks. Students see published assignments only.
      </p>

      <p role="status" className="sr-only">
        {changes.announcement}
      </p>
      <div role="alert">
        {changes.error && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {changes.error}
          </p>
        )}
      </div>

      <div className="mt-3 space-y-3">
        {assignments.status === 'loading' && (
          <p role="status" className="text-sm text-ink-muted">
            Loading assignments…
          </p>
        )}

        {assignments.status === 'failed' && (
          <div role="alert" className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-ink">Couldn’t load assignments.</p>
            <ActionButton onClick={assignments.retry}>Retry</ActionButton>
          </div>
        )}

        {(assignments.status === 'notFound' || assignments.status === 'denied') && (
          <p className="text-sm text-ink-muted">Assignments are not available for this course.</p>
        )}

        {openForm?.type === 'add' && (
          <AssignmentForm
            heading="New assignment"
            note="It is saved as a draft. Students see it once you publish it."
            submitLabel="Create assignment"
            submittingLabel="Creating…"
            onSubmit={add}
            onCancel={() => {
              setOpenForm(null)
              changes.focusNext(['add-assignment'])
            }}
          />
        )}

        {list !== null && list.length === 0 && openForm?.type !== 'add' && (
          <p className="text-sm text-ink-muted">
            No assignments yet. Create an assignment to set work for students, with a deadline and maximum marks.
          </p>
        )}

        {list !== null && list.length > 0 && (
          <ol aria-label="Assignments" className="divide-y divide-line rounded-panel border border-line bg-surface">
            {list.map((assignment) => (
              <li key={assignment.id}>
                <AssignmentItem
                  assignment={assignment}
                  now={now}
                  isEditing={openForm?.type === 'edit' && openForm.id === assignment.id}
                  isConfirming={confirming === assignment.id}
                  setOpenForm={setOpenForm}
                  setConfirming={setConfirming}
                  changes={changes}
                />
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}
