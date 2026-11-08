import { formatDateTime, fromLocalInputValue, toLocalInputValue } from '../courses/dates'
import type { TextFormat } from '../formatting/textFormat'
import type { LmsQuiz, LmsQuizSettingsInput, LmsQuizSummary, QuizType } from '../types/lms'

export const QUIZ_TITLE_MAX = 200
export const QUIZ_INSTRUCTIONS_MAX = 20_000

export const QUIZ_TYPES: readonly { value: QuizType; label: string }[] = [
  { value: 'GRADED', label: 'Graded' },
  { value: 'PRACTICE', label: 'Practice' },
]

export function quizTypeLabel(type: QuizType): string {
  return type === 'PRACTICE' ? 'Practice' : 'Graded'
}

/** The quiz settings as the form holds them: numbers and times as typed, times in the browser's own time zone. */
export type QuizSettingsDraft = {
  title: string
  instructions: string
  instructionsFormat: TextFormat
  type: QuizType
  timeLimitMinutes: string
  availableFrom: string
  dueAt: string
  availableUntil: string
  maxAttempts: string
  shuffleQuestions: boolean
  shuffleOptions: boolean
  negativeMarkPercent: string
}

export type QuizSettingsField = keyof QuizSettingsDraft

export function emptySettings(): QuizSettingsDraft {
  return {
    title: '',
    instructions: '',
    instructionsFormat: 'FORMATTED',
    type: 'GRADED',
    timeLimitMinutes: '',
    availableFrom: '',
    dueAt: '',
    availableUntil: '',
    maxAttempts: '1',
    shuffleQuestions: false,
    shuffleOptions: false,
    negativeMarkPercent: '0',
  }
}

export function settingsOf(quiz: LmsQuiz): QuizSettingsDraft {
  return {
    title: quiz.title,
    instructions: quiz.instructions ?? '',
    instructionsFormat: quiz.instructionsFormat,
    type: quiz.type,
    timeLimitMinutes: quiz.timeLimitMinutes === null ? '' : String(quiz.timeLimitMinutes),
    availableFrom: quiz.availableFrom ? toLocalInputValue(quiz.availableFrom) : '',
    dueAt: quiz.dueAt ? toLocalInputValue(quiz.dueAt) : '',
    availableUntil: quiz.availableUntil ? toLocalInputValue(quiz.availableUntil) : '',
    maxAttempts: quiz.maxAttempts === null ? '' : String(quiz.maxAttempts),
    shuffleQuestions: quiz.shuffleQuestions,
    shuffleOptions: quiz.shuffleOptions,
    negativeMarkPercent: String(quiz.negativeMarkPercent),
  }
}

const WHOLE_NUMBER = /^\d+$/
const PERCENT = /^\d{1,3}(\.\d{1,2})?$/
const EARLIEST_YEAR = 2000
const LATEST_YEAR = 9999

type Checked = { errors: Partial<Record<QuizSettingsField, string>>; input: LmsQuizSettingsInput | null }

// An optional whole number from 1 to max; empty means no limit.
function wholeNumber(value: string, max: number, wrong: string, errors: Checked['errors'], field: QuizSettingsField) {
  const typed = value.trim()
  if (typed === '') {
    return null
  }
  if (!WHOLE_NUMBER.test(typed) || Number(typed) < 1 || Number(typed) > max) {
    errors[field] = wrong
    return null
  }
  return Number(typed)
}

function moment(value: string, name: string, errors: Checked['errors'], field: QuizSettingsField): Date | null {
  if (value === '') {
    return null
  }
  const time = fromLocalInputValue(value)
  if (time === null) {
    errors[field] = `Enter a complete ${name}.`
    return null
  } else if (time.getFullYear() < EARLIEST_YEAR || time.getFullYear() > LATEST_YEAR) {
    errors[field] = `Choose a ${name} between the years ${EARLIEST_YEAR} and ${LATEST_YEAR}.`
    return null
  }
  return time
}

/**
 * Checks the settings the way the backend does and turns them into what the API is sent: times as absolute moments,
 * empty optional values as no limit. The backend checks everything again.
 */
export function checkSettings(draft: QuizSettingsDraft): Checked {
  const errors: Checked['errors'] = {}
  const title = draft.title.trim()
  if (title === '') {
    errors.title = 'Enter a title.'
  } else if (title.length > QUIZ_TITLE_MAX) {
    errors.title = `Use at most ${QUIZ_TITLE_MAX} characters.`
  }
  if (draft.instructions.trim().length > QUIZ_INSTRUCTIONS_MAX) {
    errors.instructions = `Use at most ${QUIZ_INSTRUCTIONS_MAX.toLocaleString('en')} characters.`
  }
  const timeLimit = wholeNumber(draft.timeLimitMinutes, 600, 'Enter whole minutes from 1 to 600, or leave it empty.', errors, 'timeLimitMinutes')
  const attempts = wholeNumber(draft.maxAttempts, 100, 'Enter a whole number from 1 to 100, or leave it empty.', errors, 'maxAttempts')
  const from = moment(draft.availableFrom, 'opening time', errors, 'availableFrom')
  const due = moment(draft.dueAt, 'due date', errors, 'dueAt')
  const until = moment(draft.availableUntil, 'closing time', errors, 'availableUntil')
  if (from && until && from.getTime() >= until.getTime()) {
    errors.availableUntil = 'The closing time must be after the opening time.'
  }
  if (due && from && due.getTime() < from.getTime()) {
    errors.dueAt = 'The due date cannot be before the opening time.'
  } else if (due && until && due.getTime() > until.getTime()) {
    errors.dueAt = 'The due date cannot be after the closing time.'
  }
  const negative = draft.negativeMarkPercent.trim() === '' ? '0' : draft.negativeMarkPercent.trim()
  if (!PERCENT.test(negative) || Number(negative) > 100) {
    errors.negativeMarkPercent = 'Enter a percentage from 0 to 100, with at most 2 decimal places.'
  }
  if (Object.keys(errors).length > 0) {
    return { errors, input: null }
  }
  return {
    errors,
    input: {
      title,
      instructions: draft.instructions.trim() === '' ? null : draft.instructions,
      instructionsFormat: draft.instructionsFormat,
      type: draft.type,
      timeLimitMinutes: timeLimit,
      availableFrom: from ? from.toISOString() : null,
      dueAt: due ? due.toISOString() : null,
      availableUntil: until ? until.toISOString() : null,
      maxAttempts: attempts,
      shuffleQuestions: draft.shuffleQuestions,
      shuffleOptions: draft.shuffleOptions,
      negativeMarkPercent: Number(negative),
    },
  }
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/** The quiz's size and rules in a few words, such as "5 questions · 10 marks · 20 minutes · 1 attempt". */
export function quizFacts(quiz: Pick<LmsQuizSummary, 'questionCount' | 'totalMarks' | 'timeLimitMinutes' | 'maxAttempts'>): string {
  return [
    plural(quiz.questionCount, 'question', 'questions'),
    plural(quiz.totalMarks, 'mark', 'marks'),
    quiz.timeLimitMinutes === null ? 'No time limit' : plural(quiz.timeLimitMinutes, 'minute', 'minutes'),
    quiz.maxAttempts === null ? 'Unlimited attempts' : plural(quiz.maxAttempts, 'attempt', 'attempts'),
  ].join(' · ')
}

/** When the quiz opens, is due, and closes, in the browser's own time zone; nothing when none is set. */
export function quizWindow(quiz: Pick<LmsQuizSummary, 'availableFrom' | 'dueAt' | 'availableUntil'>): string | null {
  const parts = [
    quiz.availableFrom && `Opens ${formatDateTime(quiz.availableFrom)}`,
    quiz.dueAt && `Due ${formatDateTime(quiz.dueAt)}`,
    quiz.availableUntil && `Closes ${formatDateTime(quiz.availableUntil)}`,
  ].filter((part): part is string => Boolean(part))
  return parts.length === 0 ? null : parts.join(' · ')
}
