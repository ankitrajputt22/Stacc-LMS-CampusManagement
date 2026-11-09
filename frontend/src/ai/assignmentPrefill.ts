import { ASSIGNMENT_INSTRUCTIONS_MAX, TITLE_MAX } from '../teaching/editorState'
import type { AssignmentDraftPrefill } from '../types/ai'

/** A question as the teacher edited it on the AI Tools page. */
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

/** The total of the questions' marks, or null when any is missing or not a valid amount. */
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

/**
 * The fields the normal assignment form is filled with: the title, the instructions with the numbered questions (and
 * their options and marks), and the total marks. Suggested answers stay on the AI Tools page: assignment instructions
 * become visible to students once the assignment is published.
 */
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

/** Whether navigation state really is a prefill from this page, so nothing else can fill the form. */
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
