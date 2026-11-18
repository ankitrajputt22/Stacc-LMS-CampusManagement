import type { LmsQuestion, QuestionDifficulty, QuestionType } from '../types/lms'

export const QUESTION_TYPES: { value: QuestionType; label: string }[] = [
  { value: 'SINGLE_CHOICE', label: 'Single choice' },
  { value: 'MULTIPLE_CHOICE', label: 'Multiple choice' },
  { value: 'TRUE_FALSE', label: 'True or false' },
  { value: 'SHORT_ANSWER', label: 'Short answer' },
  { value: 'DESCRIPTIVE', label: 'Descriptive' },
]

export const DIFFICULTIES: { value: QuestionDifficulty; label: string }[] = [
  { value: 'EASY', label: 'Easy' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HARD', label: 'Hard' },
]

export function questionTypeLabel(type: QuestionType): string {
  return QUESTION_TYPES.find((choice) => choice.value === type)?.label ?? type
}

export function difficultyLabel(difficulty: QuestionDifficulty): string {
  return DIFFICULTIES.find((choice) => choice.value === difficulty)?.label ?? difficulty
}

export function questionName(question: { text: string }): string {
  const words = question.text.replace(/\s+/g, ' ').trim()
  return words.length > 60 ? `${words.slice(0, 57)}…` : words
}

export type QuestionFilters = {
  search: string
  type: QuestionType | ''
  difficulty: QuestionDifficulty | ''
  topic: string
  status: 'ACTIVE' | 'ARCHIVED' | ''
}

export const NO_FILTERS: QuestionFilters = { search: '', type: '', difficulty: '', topic: '', status: 'ACTIVE' }

export function filterQuestions(questions: LmsQuestion[], filters: QuestionFilters): LmsQuestion[] {
  const search = filters.search.trim().toLocaleLowerCase()
  return questions.filter(
    (question) =>
      (search === '' || question.text.toLocaleLowerCase().includes(search)) &&
      (filters.type === '' || question.type === filters.type) &&
      (filters.difficulty === '' || question.difficulty === filters.difficulty) &&
      (filters.topic === '' || question.topic === filters.topic) &&
      (filters.status === '' || question.status === filters.status),
  )
}

export function questionTopics(questions: LmsQuestion[]): string[] {
  return [...new Set(questions.map((question) => question.topic).filter((topic): topic is string => topic !== null))].sort(
    (a, b) => a.localeCompare(b),
  )
}
