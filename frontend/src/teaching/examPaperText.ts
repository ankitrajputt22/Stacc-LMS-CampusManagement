import { getApiError, getHttpStatus } from '../api/apiError'
import { bloomLabel } from '../courses/bloomText'
import type { ExamBlueprintRow, ExamPaperQuestion, ExamPaperStatus, LmsOutcome } from '../types/lms'
import { difficultyLabel, questionTypeLabel } from './questionText'

const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

/** Marks as typed: greater than zero, with at most two decimal places. Null when they are not valid. */
export function parseMarks(typed: string): number | null {
  const value = typed.trim()
  return MARKS_PATTERN.test(value) && Number(value) > 0 ? Number(value) : null
}

/** "1 mark", "2.5 marks". */
export function marksText(marks: number): string {
  return `${Number(marks)} ${Number(marks) === 1 ? 'mark' : 'marks'}`
}

export function paperStatusLabel(status: ExamPaperStatus): string {
  return status === 'FINAL' ? 'Final' : status === 'ARCHIVED' ? 'Archived' : 'Draft'
}

/** "(a)", "(b)", … for a choice's place in its question. */
export function choiceLetter(index: number): string {
  return `(${String.fromCharCode(97 + index)})`
}

/** What a blueprint row asks for, in words: "2 questions × 5 marks: Single choice, Easy, CO2". */
export function blueprintRowText(row: ExamBlueprintRow, outcomes: LmsOutcome[]): string {
  const filters = [
    row.questionType && questionTypeLabel(row.questionType),
    row.difficulty && difficultyLabel(row.difficulty),
    row.bloomLevel && bloomLabel(row.bloomLevel),
    row.outcomeId !== null && (outcomes.find((outcome) => outcome.id === row.outcomeId)?.code ?? 'An outcome'),
    row.topic && `topic “${row.topic}”`,
  ].filter(Boolean)
  const count = `${row.questionCount} ${row.questionCount === 1 ? 'question' : 'questions'} × ${marksText(row.marksEach)}`
  return `${count}: ${filters.length === 0 ? 'any reviewed question' : filters.join(', ')}`
}

/** The answer key line of a question, as faculty read it. */
export function answerKeyText(question: ExamPaperQuestion): string {
  switch (question.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return question.options
        .map((option, index) => (option.correct ? `${choiceLetter(index)} ${option.text}` : null))
        .filter(Boolean)
        .join('; ')
    case 'TRUE_FALSE':
      return question.trueFalseAnswer ? 'True' : 'False'
    case 'SHORT_ANSWER':
      return `Accepted: ${question.options.map((option) => option.text).join('; ')}`
    default:
      return question.answerGuidance ? `Guidance: ${question.answerGuidance}` : 'No marking guidance given.'
  }
}

/** The questions grouped by section in order; a new group starts each time the section changes. */
export function sectionGroups(questions: ExamPaperQuestion[]): { section: string | null; questions: ExamPaperQuestion[] }[] {
  const groups: { section: string | null; questions: ExamPaperQuestion[] }[] = []
  for (const question of questions) {
    const last = groups.at(-1)
    if (last && last.section === question.sectionTitle) {
      last.questions.push(question)
    } else {
      groups.push({ section: question.sectionTitle, questions: [question] })
    }
  }
  return groups
}

export const PAPER_FORBIDDEN =
  'You don’t have permission to manage exam papers. If your access changed recently, sign out and sign in again.'

/** Safe words for a refused request: the backend's message for expected refusals, a general one otherwise. */
export function paperRefusal(error: unknown, failed: string): string {
  const status = getHttpStatus(error)
  const message = getApiError(error)?.message ?? null
  if (status === 403) {
    return PAPER_FORBIDDEN
  }
  return message && status !== null && status < 500 ? message : failed
}

/** A paper's size and length in words: "4 questions · 14 marks · 45 minutes". */
export function paperFacts(paper: { questionCount: number; totalMarks: number; durationMinutes: number | null }): string {
  return [
    `${paper.questionCount} ${paper.questionCount === 1 ? 'question' : 'questions'}`,
    marksText(paper.totalMarks),
    paper.durationMinutes === null ? null : `${paper.durationMinutes} minutes`,
  ]
    .filter(Boolean)
    .join(' · ')
}
