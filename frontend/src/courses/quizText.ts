import type {
  QuizAnswerInput,
  QuizAnswerOutcome,
  QuizAttemptQuestion,
  QuizAttemptState,
  QuizAttemptSummary,
  QuizResultStatus,
  StudentQuiz,
} from '../types/lms'
import { formatDateTime } from './dates'

export function attemptStateLabel(state: QuizAttemptState): string {
  switch (state) {
    case 'IN_PROGRESS':
      return 'In progress'
    case 'TIME_UP':
      return 'Time is up'
    case 'SUBMITTED':
      return 'Submitted'
    default:
      return 'Closed at the deadline'
  }
}

/** Whether the student may start the quiz now, in words, exactly as the backend decided it. */
export function quizStatusText(quiz: StudentQuiz): string {
  if (quiz.openAttemptId !== null) {
    return 'You have an attempt in progress.'
  }
  if (quiz.availability === 'NOT_OPEN') {
    return quiz.availableFrom ? `Opens ${formatDateTime(quiz.availableFrom)}.` : 'Not open yet.'
  }
  if (quiz.availability === 'CLOSED') {
    return 'This quiz has closed.'
  }
  if (quiz.attemptsLeft === 0) {
    return 'You have used every attempt.'
  }
  return 'Open.'
}

/** "1 of 2 attempts used", or "1 attempt used" when attempts are unlimited. */
export function attemptsText(quiz: Pick<StudentQuiz, 'maxAttempts' | 'attempts'>): string {
  const used = quiz.attempts.length
  return quiz.maxAttempts === null
    ? `${used} ${used === 1 ? 'attempt' : 'attempts'} used (unlimited)`
    : `${used} of ${quiz.maxAttempts} ${quiz.maxAttempts === 1 ? 'attempt' : 'attempts'} used`
}

/** Seconds as a clock, such as 12:05 or 1:02:09. Never below zero. */
export function clockText(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = String(seconds % 60).padStart(2, '0')
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${rest}` : `${minutes}:${rest}`
}

/** The answer as the form holds it. */
export type AnswerDraft = { selected: number[]; trueFalse: boolean | null; text: string }

export function draftOfQuestion(question: QuizAttemptQuestion): AnswerDraft {
  return {
    selected: question.answer?.selectedOptions ?? [],
    trueFalse: question.answer?.trueFalseAnswer ?? null,
    text: question.answer?.text ?? '',
  }
}

/** What the API is sent: only the part the question's type takes. */
export function inputOfDraft(question: Pick<QuizAttemptQuestion, 'type'>, draft: AnswerDraft): QuizAnswerInput {
  switch (question.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return { selectedOptions: [...draft.selected].sort((a, b) => a - b), trueFalseAnswer: null, text: null }
    case 'TRUE_FALSE':
      return { selectedOptions: [], trueFalseAnswer: draft.trueFalse, text: null }
    default:
      return { selectedOptions: [], trueFalseAnswer: null, text: draft.text.trim() === '' ? null : draft.text }
  }
}

export function isAnswered(question: Pick<QuizAttemptQuestion, 'type'>, draft: AnswerDraft): boolean {
  const input = inputOfDraft(question, draft)
  return input.selectedOptions.length > 0 || input.trueFalseAnswer !== null || input.text !== null
}

export function outcomeLabel(outcome: QuizAnswerOutcome): string {
  switch (outcome) {
    case 'CORRECT':
      return 'Correct'
    case 'INCORRECT':
      return 'Incorrect'
    case 'UNANSWERED':
      return 'Not answered'
    case 'NEEDS_MARKING':
      return 'Waiting for marking'
    default:
      return 'Marked'
  }
}

/** Why a handed-in attempt shows no result yet, in words; null once it is shown or before hand-in. */
export function resultWaitText(status: QuizResultStatus): string | null {
  switch (status) {
    case 'WAITING_FOR_MARKING':
      return 'Your result appears once your teacher has marked the written answers.'
    case 'WAITING_FOR_RELEASE':
      return 'Results for this quiz are shown after its due date.'
    default:
      return null
  }
}

/** An attempt's result in the attempts list: the score, or why there is none yet. */
export function attemptResultText(attempt: Pick<QuizAttemptSummary, 'resultStatus' | 'score'>, totalMarks: number): string {
  switch (attempt.resultStatus) {
    case 'RELEASED':
      return `${attempt.score} / ${totalMarks}`
    case 'WAITING_FOR_MARKING':
      return 'Waiting for marking'
    case 'WAITING_FOR_RELEASE':
      return 'After the due date'
    default:
      return '—'
  }
}
