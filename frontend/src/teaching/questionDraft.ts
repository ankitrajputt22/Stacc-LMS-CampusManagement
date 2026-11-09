import type { TextFormat } from '../formatting/textFormat'
import type { BloomLevel, LmsQuestion, LmsQuestionInput, QuestionDifficulty, QuestionType } from '../types/lms'

export const MAX_OPTIONS = 10

/** What the question form holds while it is being written. Numbers stay text until the form is sent. */
export type QuestionDraft = {
  type: QuestionType
  text: string
  textFormat: TextFormat
  explanation: string
  options: { text: string; correct: boolean }[]
  trueFalseAnswer: 'true' | 'false' | ''
  answerGuidance: string
  topic: string
  difficulty: QuestionDifficulty
  marks: string
  bloomLevel: BloomLevel | ''
  outcomeId: string
}

export function emptyDraft(): QuestionDraft {
  return {
    type: 'SINGLE_CHOICE',
    text: '',
    textFormat: 'FORMATTED',
    explanation: '',
    options: [
      { text: '', correct: true },
      { text: '', correct: false },
    ],
    trueFalseAnswer: '',
    answerGuidance: '',
    topic: '',
    difficulty: 'MEDIUM',
    marks: '1',
    bloomLevel: '',
    outcomeId: '',
  }
}

export function draftOf(question: LmsQuestion): QuestionDraft {
  return {
    type: question.type,
    text: question.text,
    textFormat: question.textFormat,
    explanation: question.explanation ?? '',
    options: question.options.map((option) => ({ ...option })),
    trueFalseAnswer: question.trueFalseAnswer === null ? '' : question.trueFalseAnswer ? 'true' : 'false',
    answerGuidance: question.answerGuidance ?? '',
    topic: question.topic ?? '',
    difficulty: question.difficulty,
    marks: String(question.marks),
    bloomLevel: question.bloomLevel ?? '',
    outcomeId: question.outcome ? String(question.outcome.id) : '',
  }
}

export function usesOptions(type: QuestionType): boolean {
  return type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE' || type === 'SHORT_ANSWER'
}

/** The draft with another type: options fit the new type (short answers are all correct; one correct for single choice). */
export function changeType(draft: QuestionDraft, type: QuestionType): QuestionDraft {
  let options = draft.options.length >= 2 || type === 'SHORT_ANSWER' ? draft.options : emptyDraft().options
  if (type === 'SHORT_ANSWER') {
    options = (options.length === 0 ? [{ text: '', correct: true }] : options).map((option) => ({ ...option, correct: true }))
  } else if (type === 'SINGLE_CHOICE') {
    const first = Math.max(0, options.findIndex((option) => option.correct))
    options = options.map((option, index) => ({ ...option, correct: index === first }))
  }
  return { ...draft, type, options }
}

/**
 * Checks what the browser can check before sending, so obvious slips are caught early. The backend still decides.
 * Answers field errors by name: text, options, trueFalseAnswer, marks.
 */
export function checkDraft(draft: QuestionDraft): Record<string, string> {
  const errors: Record<string, string> = {}
  if (draft.text.trim() === '') {
    errors.text = 'Write the question.'
  }
  const marks = Number(draft.marks)
  if (draft.marks.trim() === '' || !Number.isFinite(marks) || marks <= 0) {
    errors.marks = 'Enter marks greater than zero.'
  } else if (!/^\d{1,6}(\.\d{1,2})?$/.test(draft.marks.trim())) {
    errors.marks = 'Use at most two decimal places, below 1,000,000.'
  }
  if (usesOptions(draft.type)) {
    const options = draft.options
    const correct = options.filter((option) => option.correct).length
    if (options.some((option) => option.text.trim() === '')) {
      errors.options = draft.type === 'SHORT_ANSWER' ? 'Fill in every accepted answer or remove it.' : 'Fill in every option or remove it.'
    } else if (draft.type !== 'SHORT_ANSWER' && options.length < 2) {
      errors.options = 'Give at least two options.'
    } else if (draft.type === 'SHORT_ANSWER' && options.length < 1) {
      errors.options = 'Give at least one accepted answer.'
    } else if (draft.type === 'SINGLE_CHOICE' && correct !== 1) {
      errors.options = 'Mark exactly one correct option.'
    } else if (draft.type === 'MULTIPLE_CHOICE' && correct < 1) {
      errors.options = 'Mark at least one correct option.'
    } else if (new Set(options.map((option) => option.text.trim().toLocaleLowerCase())).size !== options.length) {
      errors.options = 'Options must differ from each other.'
    }
  }
  if (draft.type === 'TRUE_FALSE' && draft.trueFalseAnswer === '') {
    errors.trueFalseAnswer = 'Choose the correct answer.'
  }
  return errors
}

/** What is sent: only the parts the question's type takes. */
export function inputOf(draft: QuestionDraft): LmsQuestionInput {
  const optional = (text: string) => (text.trim() === '' ? null : text)
  return {
    type: draft.type,
    text: draft.text,
    textFormat: draft.textFormat,
    explanation: optional(draft.explanation),
    options: usesOptions(draft.type) ? draft.options.map((option) => ({ text: option.text, correct: option.correct })) : [],
    trueFalseAnswer: draft.type === 'TRUE_FALSE' ? draft.trueFalseAnswer === 'true' : null,
    answerGuidance: draft.type === 'DESCRIPTIVE' ? optional(draft.answerGuidance) : null,
    topic: optional(draft.topic),
    difficulty: draft.difficulty,
    marks: Number(draft.marks),
    bloomLevel: draft.bloomLevel === '' ? null : draft.bloomLevel,
    outcomeId: draft.outcomeId === '' ? null : Number(draft.outcomeId),
  }
}
