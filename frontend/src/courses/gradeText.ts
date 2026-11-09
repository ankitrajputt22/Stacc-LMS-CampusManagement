import type { GradebookCell, GradeItemType } from '../types/lms'

export function gradeTypeLabel(type: GradeItemType): string {
  switch (type) {
    case 'ASSIGNMENT':
      return 'Assignment'
    case 'QUIZ':
      return 'Quiz'
    default:
      return 'Other'
  }
}

/** A cell's marks, or why there are none yet. */
export function cellText(cell: Pick<GradebookCell, 'marks' | 'status'>): string {
  switch (cell.status) {
    case 'GRADED':
      return String(cell.marks)
    case 'NEEDS_GRADING':
      return 'Needs grading'
    default:
      return '—'
  }
}

/** Case-insensitive match on the college ID, for the gradebook's student search. */
export function matchesStudent(loginId: string, search: string): boolean {
  const words = search.trim().toLowerCase()
  return words === '' || loginId.toLowerCase().includes(words)
}
