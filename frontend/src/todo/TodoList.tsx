import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { dismissTodo, getTodo } from '../api/todoApi'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { TodoItem, TodoKind } from '../types/todo'
import { Alert, Badge, type BadgeTone, Button, Card } from '../ui'

const KIND: Record<TodoKind, { label: string; tone: BadgeTone }> = {
  ASSIGNMENT: { label: 'Assignment', tone: 'brand' },
  GRADING: { label: 'Grading', tone: 'warning' },
  ATTENDANCE: { label: 'Attendance', tone: 'info' },
  APPROVAL: { label: 'Approval', tone: 'neutral' },
  MENTORING: { label: 'Mentoring', tone: 'info' },
}

/** Whether a due time has passed. Display only; the backend decides what is listed. */
function pastDue(item: TodoItem): boolean {
  return item.dueAt !== null && item.kind === 'ASSIGNMENT' && new Date(item.dueAt).getTime() < Date.now()
}

/**
 * The To-Do list, soonest due first, with Dismiss for each item. `limit` shows only the first few (on the dashboard);
 * the items and their links are only what the backend sends, and each destination checks access again.
 */
export function TodoList({ limit, level = 2 }: { limit?: number; level?: 2 | 3 }) {
  const queryClient = useQueryClient()
  const todo = useApiQuery(queryKeys.me.todo(), (signal) => getTodo(signal))
  const [error, setError] = useState<string | null>(null)

  async function dismiss(item: TodoItem) {
    setError(null)
    try {
      await dismissTodo(item.key)
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.todo() })
    } catch (failure) {
      if (getHttpStatus(failure) !== 401) {
        setError('Couldn’t dismiss that item. Try again.')
      }
    }
  }

  const items = todo.status === 'loaded' ? (limit ? todo.data.slice(0, limit) : todo.data) : []
  return (
    <Card title="To-Do" level={level} padding="none" description={limit ? undefined : 'Worked out from your courses, teaching, and approvals.'}>
      {error && (
        <div className="px-4 pt-3 sm:px-5">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      {todo.status !== 'loaded' ? (
        <SectionState request={todo} what="your To-Do list" />
      ) : items.length === 0 ? (
        <EmptyState title="Nothing to do right now." description="New work and requests appear here as they arrive." />
      ) : (
        <ul className="divide-y divide-line">
          {items.map((item) => (
            <li key={item.key} className="flex flex-wrap items-start justify-between gap-3 px-4 py-3 sm:px-5">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={KIND[item.kind].tone}>{KIND[item.kind].label}</Badge>
                  {item.actionPath ? (
                    <Link to={item.actionPath} className="font-semibold break-words text-stacc-primary underline-offset-4 hover:underline">
                      {item.title}
                    </Link>
                  ) : (
                    <span className="font-semibold break-words text-ink">{item.title}</span>
                  )}
                  {pastDue(item) && <Badge tone="danger">Past due</Badge>}
                </div>
                <p className="mt-0.5 text-sm text-ink-muted">
                  {item.context}
                  {item.dueAt && (
                    <>
                      {' · '}
                      {item.kind === 'ATTENDANCE' ? 'Class ' : 'Due '}
                      <time dateTime={item.dueAt}>{formatDateTime(item.dueAt)}</time>
                    </>
                  )}
                </p>
              </div>
              <Button variant="secondary" size="sm" onClick={() => void dismiss(item)}>
                Dismiss<span className="sr-only">{` ${item.title}`}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
      {limit && todo.status === 'loaded' && todo.data.length > limit && (
        <p className="border-t border-line px-4 py-3 text-sm sm:px-5">
          <Link to="/todo" className="font-medium text-stacc-primary underline-offset-4 hover:underline">
            See all {todo.data.length} items
          </Link>
        </p>
      )}
    </Card>
  )
}
