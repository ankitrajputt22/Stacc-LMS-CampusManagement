export type TodoKind = 'ASSIGNMENT' | 'GRADING' | 'ATTENDANCE' | 'APPROVAL' | 'MENTORING' | 'STUDY'

export type TodoItem = {
  key: string
  kind: TodoKind
  title: string
  context: string
  dueAt: string | null
  actionPath: string | null
}
