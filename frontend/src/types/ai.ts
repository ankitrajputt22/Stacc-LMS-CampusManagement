// Shapes of the AI learning API (docs/ai-learning.md). Only what the backend sends: answers, summaries, and
// questions come with citations of published course sources, never storage keys, prompts, or scores.

export type AiSourceType = 'TOPIC' | 'STUDY_MATERIAL'

/** A published course source an AI result is based on. */
export type AiCitation = {
  sourceType: AiSourceType
  sourceId: number
  title: string
  /** The study material's file name, or null for a topic. */
  fileName: string | null
  pageNumber: number | null
  slideNumber: number | null
}

/** A grounded answer, or insufficientContext with no answer and no citations. */
export type AiAnswer = {
  answer: string | null
  insufficientContext: boolean
  citations: AiCitation[]
}

export type AiTopicSummary = {
  topicId: number
  topicTitle: string
  summary: string | null
  keyPoints: string[]
  insufficientContext: boolean
  citations: AiCitation[]
}

export type AiPracticeQuestion = {
  question: string
  suggestedAnswer: string
  citations: AiCitation[]
}

export type AiPracticeSet = {
  questions: AiPracticeQuestion[]
  insufficientContext: boolean
}

export type AiKnowledgeIndexStatus = 'NOT_INDEXED' | 'BUILDING' | 'READY' | 'FAILED'

/** A course's AI knowledge as faculty see it. */
export type AiKnowledgeStatus = {
  enabled: boolean
  status: AiKnowledgeIndexStatus
  /** Published content changed since the last successful refresh. */
  stale: boolean
  indexedAt: string | null
  publishedSourceCount: number
  failureMessage: string | null
}

export type AiKnowledgeSync = {
  status: AiKnowledgeIndexStatus
  indexedSourceCount: number
  skippedSourceCount: number
  indexedAt: string
}

export type AiDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED'
export type AiQuestionType = 'MCQ' | 'SHORT_ANSWER' | 'LONG_ANSWER' | 'MIXED'

export type AiAssessmentDraftInput = {
  questionCount: number
  difficulty: AiDifficulty
  questionType: AiQuestionType
  additionalInstruction: string | null
}

export type AiAssessmentQuestion = {
  questionType: Exclude<AiQuestionType, 'MIXED'>
  prompt: string
  /** Only for multiple-choice questions. */
  options: string[]
  suggestedAnswer: string
  suggestedMarks: number
  citations: AiCitation[]
}

/** A draft for the teacher to review. It is not saved and creates no assignment. */
export type AiAssessmentDraft = {
  titleSuggestion: string | null
  instructionsSuggestion: string | null
  questions: AiAssessmentQuestion[]
  insufficientContext: boolean
}

/**
 * What "Use in assignment form" hands to the normal assignment form through navigation state. The form only shows
 * it; nothing is created until the teacher submits the form.
 */
export type AssignmentDraftPrefill = {
  title: string
  instructions: string
  maxMarks: number | null
}
