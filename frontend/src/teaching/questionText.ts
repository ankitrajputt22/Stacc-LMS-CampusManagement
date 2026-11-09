import type { LmsQuestion, QuestionDifficulty, QuestionType } from '../types/lms'

/** The question types in the order they are offered, with the words shown for them. */
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

/** A short plain-text name for a question in button labels: its first words. */
export function questionName(question: { text: string }): string {
  const words = question.text.replace(/\s+/g, ' ').trim()
  return words.length > 60 ? `${words.slice(0, 57)}…` : words
}

/** The filters of the question bank page. Empty values mean "any". */
export type QuestionFilters = {
  search: string
  type: QuestionType | ''
  difficulty: QuestionDifficulty | ''
  topic: string
  status: 'ACTIVE' | 'ARCHIVED' | ''
}

export const NO_FILTERS: QuestionFilters = { search: '', type: '', difficulty: '', topic: '', status: 'ACTIVE' }

/** The questions matching every filter; the search looks in the question text, ignoring case. */
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

/** The distinct topics of the bank, alphabetically, for the topic filter. */
export function questionTopics(questions: LmsQuestion[]): string[] {
  return [...new Set(questions.map((question) => question.topic).filter((topic): topic is string => topic !== null))].sort(
    (a, b) => a.localeCompare(b),
  )
}
