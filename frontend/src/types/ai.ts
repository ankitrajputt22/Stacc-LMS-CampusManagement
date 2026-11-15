// Shapes of the AI learning API (docs/ai-learning.md). Only what the backend sends: answers, summaries, and
// questions come with citations of published course sources, never storage keys, prompts, or scores.

export type AiSourceType = 'TOPIC' | 'STUDY_MATERIAL' | 'TEACHER_DOCUMENT' | 'NOTE'

/** A published course source an AI result is based on. */
export type AiCitation = {
  sourceType: AiSourceType
  sourceId: number
  title: string
  /** The study material's file name, or null for a topic. */
  fileName: string | null
  pageNumber: number | null
  slideNumber: number | null
  /** The course page the source is on, for a link (v0.13.9); null for notes and teacher documents. */
  pageId?: number | null
}

/** What a question searches (v0.13.9). */
export type AiAskMode = 'COURSE' | 'COURSE_AND_NOTES' | 'NOTES_ONLY'

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
  /** The latest background refresh job, or null when none was ever queued. */
  job: AiJob | null
  /** Active teacher-only documents (v0.13.5). */
  teacherDocumentCount: number
  /** False until a refresh includes the latest teacher document changes. */
  teacherDocumentsCurrent: boolean
  /** Whether students may use the AI Study Assistant in this course (v0.13.7). */
  studentAiEnabled: boolean
}

/** What a question bank draft added (v0.14.1); the questions are in the bank, each needing review. */
export type AiQuestionBankDraft = {
  added: number
  droppedForKeyMismatch: number
  droppedAsRepeats: number
  leftOut: number
  insufficientContext: boolean
  questionIds: number[]
}

/** The draft quiz built from the bank, with AI only for gaps (v0.14.3). */
export type AiQuizDraft = {
  quizId: number
  fromBank: number
  fromAi: number
  missing: number
  aiNote: string | null
}

/** One of the signed-in student's private notes in a course (v0.13.6). */
export type AiStudentNote = {
  id: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

/** A teacher-only document (v0.13.5): plain text only AI Tools read. Students never see it. */
export type AiTeacherDocument = {
  id: number
  title: string
  content: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

/** A background refresh job (v0.13.1); results and errors are short safe texts from the backend. */
export type AiJob = {
  jobId: number
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  resultSummary: string | null
  errorMessage: string | null
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
