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
  standalone?: boolean
  lmsCourseId: number
  assignments: ApiRequestState<LmsCourseAssignment[]> & { retry: () => void }
}

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

export function CourseAssignments({ lmsCourseId, assignments, standalone = false }: CourseAssignmentsProps) {
  const [now] = useState(() => Date.now())

  return (
    <Card title={standalone ? undefined : 'Assignments'} icon={standalone ? undefined : 'assignments'} padding="none" className={standalone ? '' : 'mt-6'}>
      {assignments.status !== 'loaded' ? (
        <SectionState request={assignments} what="assignments" />
      ) : assignments.data.length === 0 ? (
        <EmptyState title="No assignments published yet." />
      ) : (
        <>
          {standalone && (
            <div aria-hidden="true" className="hidden grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_8rem_11rem] gap-4 rounded-t-panel bg-surface-muted px-5 py-3 text-[0.95rem] font-semibold text-ink md:grid">
              <span>Title</span>
              <span>Due</span>
              <span>Maximum</span>
              <span>Action</span>
            </div>
          )}
          <ul className="divide-y divide-line">
            {assignments.data.map((assignment) => (
              <li
                key={assignment.id}
                className={`group relative px-4 py-3 last:rounded-b-panel hover:bg-surface-muted has-[a:focus-visible]:outline-2 has-[a:focus-visible]:-outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5 ${
                  standalone ? 'md:grid md:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_8rem_11rem] md:items-center md:gap-4' : 'flex items-start justify-between gap-4'
                }`}
              >
                <div className="min-w-0">
                  <h3 className={`font-semibold break-words text-ink ${standalone ? 'text-[0.95rem]' : 'text-sm'}`}>
                    <Link
                      to={`/courses/${lmsCourseId}/assignments/${assignment.id}`}
                      className="after:absolute after:inset-0 focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
                    >
                      {assignment.title}
                    </Link>
                  </h3>
                  {standalone ? (
                    <span className="md:hidden"><AssignmentFacts dueAt={assignment.dueAt} maxMarks={assignment.maxMarks} now={now} /></span>
                  ) : (
                    <AssignmentFacts dueAt={assignment.dueAt} maxMarks={assignment.maxMarks} now={now} />
                  )}
                </div>
                {standalone ? (
                  <>
                    <span className="hidden items-center gap-2 text-[0.95rem] text-ink md:flex">
                      <time dateTime={assignment.dueAt}>{formatDateTime(assignment.dueAt)}</time>
                      {isPast(assignment.dueAt, now) && <PastDueLabel />}
                    </span>
                    <span className="hidden text-[0.95rem] text-ink md:block">{marksText(assignment.maxMarks)}</span>
                    <span aria-hidden="true" className="mt-2 inline-flex h-9 items-center gap-1 rounded-control bg-portal-cta px-3 text-sm font-medium text-white group-hover:bg-portal-cta-hover md:mt-0 md:justify-self-start">
                      Open assignment ›
                    </span>
                  </>
                ) : (
                  <span aria-hidden="true" className="shrink-0 pt-0.5 text-sm font-medium text-stacc-primary">
                    View
                  </span>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </Card>
  )
}
