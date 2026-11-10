export type TodoKind = 'ASSIGNMENT' | 'GRADING' | 'ATTENDANCE' | 'APPROVAL' | 'MENTORING'

/** One To-Do item as the API sends it, worked out live. */
export type TodoItem = {
  key: string
  kind: TodoKind
  title: string
  context: string
  dueAt: string | null
  actionPath: string | null
}
