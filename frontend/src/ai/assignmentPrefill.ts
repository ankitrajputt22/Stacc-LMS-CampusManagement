import { ASSIGNMENT_INSTRUCTIONS_MAX, TITLE_MAX } from '../teaching/editorState'
import type { AssignmentDraftPrefill } from '../types/ai'

export type EditableQuestion = {
  questionType: 'MCQ' | 'SHORT_ANSWER' | 'LONG_ANSWER'
  prompt: string
  options: string[]
  suggestedAnswer: string
  marks: string
}

const MARKS = /^\d{1,6}(\.\d{1,2})?$/
const LETTERS = 'abcdefghij'

function marksText(marks: number): string {
  return Number.isInteger(marks) ? String(marks) : marks.toFixed(2)
}

export function totalMarks(questions: EditableQuestion[]): number | null {
  let total = 0
  for (const question of questions) {
    const value = question.marks.trim()
    if (!MARKS.test(value) || Number(value) <= 0) {
      return null
    }
    total += Number(value)
  }
  total = Math.round(total * 100) / 100
  return total > 0 && total < 1_000_000 ? total : null
}

export function assignmentPrefill(title: string, instructions: string, questions: EditableQuestion[]): AssignmentDraftPrefill {
  const lines: string[] = []
  if (instructions.trim()) {
    lines.push(instructions.trim(), '')
  }
  lines.push('Questions')
  questions.forEach((question, index) => {
    const value = question.marks.trim()
    const marks = MARKS.test(value) ? ` (${marksText(Number(value))} ${Number(value) === 1 ? 'mark' : 'marks'})` : ''
    lines.push(`${index + 1}. ${question.prompt.trim()}${marks}`)
    if (question.questionType === 'MCQ') {
      question.options
        .filter((option) => option.trim())
        .forEach((option, optionIndex) => lines.push(`   ${LETTERS[optionIndex] ?? '-'}) ${option.trim()}`))
    }
  })
  return {
    title: title.trim().slice(0, TITLE_MAX),
    instructions: lines.join('\n').slice(0, ASSIGNMENT_INSTRUCTIONS_MAX),
    maxMarks: totalMarks(questions),
  }
}

export function isAssignmentDraftPrefill(value: unknown): value is AssignmentDraftPrefill {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.title === 'string' &&
    typeof candidate.instructions === 'string' &&
    (candidate.maxMarks === null || typeof candidate.maxMarks === 'number')
  )
}
