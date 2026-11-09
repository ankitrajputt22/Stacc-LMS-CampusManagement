import { useState } from 'react'
import { Link } from 'react-router'
import type { ApiRequestState } from '../api/useApiRequest'
import { EmptyState } from '../components/states/EmptyState'
import type { LmsCourseAssignment } from '../types/lms'
import { formatDateTime, isPast } from './dates'
import { marksText } from './marks'
import { PastDueLabel } from './PastDueLabel'
import { SectionState } from './SectionState'
import { Card } from '../ui'

type CourseAssignmentsProps = {
  /** On the section's own page, whose title already names it: a plain card without its own heading. */
  standalone?: boolean
  lmsCourseId: number
  assignments: ApiRequestState<LmsCourseAssignment[]> & { retry: () => void }
}

/** The deadline and marks of an assignment, as one line of text. */
export function AssignmentFacts({ dueAt, maxMarks, now }: { dueAt: string; maxMarks: number; now: number }) {
  return (
    <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ink-muted">
      <span>
        Due <time dateTime={dueAt}>{formatDateTime(dueAt)}</time>
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {marksText(maxMarks)}
      </span>
      {isPast(dueAt, now) && <PastDueLabel />}
    </p>
  )
}

/**
 * The course's published assignments, earliest deadline first, past-due ones included. Each row opens its own page.
 * This part loads on its own: if it fails, the rest of the course page stays as it is.
 */
export function CourseAssignments({ lmsCourseId, assignments, standalone = false }: CourseAssignmentsProps) {
  // One moment for the whole list, so every row agrees on what has passed.
  const [now] = useState(() => Date.now())

  return (
    <Card title={standalone ? undefined : 'Assignments'} padding="none" className={standalone ? '' : 'mt-6'}>
      {assignments.status !== 'loaded' ? (
        <SectionState request={assignments} what="assignments" />
      ) : assignments.data.length === 0 ? (
        <EmptyState title="No assignments published yet." />
      ) : (
        <ul className="divide-y divide-line">
          {assignments.data.map((assignment) => (
            <li
              key={assignment.id}
              className="group relative flex items-start justify-between gap-4 px-4 py-3 last:rounded-b-panel hover:bg-surface-muted has-[a:focus-visible]:outline-2 has-[a:focus-visible]:-outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5"
            >
              <div className="min-w-0">
                <h3 className="text-sm font-semibold break-words text-ink">
                  {/* The title is the one link; its click area covers the whole row. */}
                  <Link
                    to={`/courses/${lmsCourseId}/assignments/${assignment.id}`}
                    className="after:absolute after:inset-0 focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
                  >
                    {assignment.title}
                  </Link>
                </h3>
                <AssignmentFacts dueAt={assignment.dueAt} maxMarks={assignment.maxMarks} now={now} />
              </div>
              <span aria-hidden="true" className="shrink-0 pt-0.5 text-sm font-medium text-stacc-primary">
                View
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
