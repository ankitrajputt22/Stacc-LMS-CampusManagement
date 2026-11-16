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

/** A practice answer marked by AI (v0.15.4): practice only, never an official mark. */
export type AiEvaluation = {
  marks: number | null
  maxMarks: number
  verdict: 'CORRECT' | 'PARTLY_CORRECT' | 'INCORRECT' | null
  feedback: string | null
  followUpQuestion: string | null
  citations: AiCitation[]
  insufficientContext: boolean
}
export type AiPracticeMode = 'PRACTICE' | 'MOCK_EXAM' | 'VIVA'

/** One published page's mastery for the signed-in student (v0.15.5), worked out from their own evidence. */
export type AiTopicMastery = {
  topicId: number
  title: string
  level: 'NOT_STARTED' | 'LEARNING' | 'PRACTISING' | 'MASTERED'
  mastery: number
  confidence: number
  evidence: number
  lastPractisedAt: string | null
}

/** Flashcards (v0.15.3). */
export type AiFlashcardSetSummary = { id: number; title: string; cards: number; dueToday: number }
export type AiFlashcard = {
  id: number
  position: number
  front: string
  back: string
  source: AiCitation
  dueOn: string
  intervalDays: number
  repetitions: number
}
export type AiFlashcardSet = { id: number; title: string; cards: AiFlashcard[] }
export type ReviewRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'

/** What a summary covers and how (v0.15.2). */
export type AiSummaryScope = 'TOPIC' | 'MODULE' | 'DOCUMENT'
export type AiSummaryStyle = 'BRIEF' | 'DETAILED' | 'REVISION_NOTES'

export type AiSummary = {
  scope: AiSummaryScope
  id: number
  title: string | null
  style: AiSummaryStyle
  summary: string | null
  keyPoints: string[]
  insufficientContext: boolean
  citations: AiCitation[]
}

/** How to answer (v0.15.1). */
export type AiAnswerStyle = 'NORMAL' | 'SIMPLER' | 'EXAMPLE'

/** An earlier question and answer on the open page, sent with a follow-up; nothing is stored (v0.15.1). */
export type AiFollowUpTurn = { question: string; answer: string }

/** An AI answer the student chose to save (v0.15.1). */
export type AiSavedAnswer = { id: number; question: string; answer: string; citations: AiCitation[]; savedAt: string }

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
  /** With adaptive practice (v0.15.6), the page chosen and why. */
  chosenTopicId?: number | null
  reason?: string | null
}

/** A suggestion of what to do next, from fixed rules, with its reason (v0.15.6). */
export type AiRecommendation = {
  kind: 'PRACTISE_WEAK' | 'START_PAGE' | 'REVIEW_FLASHCARDS' | 'ASSIGNMENT_SOON' | 'REFRESH_PAGE'
  targetId: number | null
  title: string
  reason: string
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

/** A suggested model answer to a bank question (v0.14.5); nothing is saved. */
export type AiModelAnswer = {
  answer: string | null
  keyPoints: string[]
  citations: AiCitation[]
  insufficientContext: boolean
}

/** A suggested rubric for an assignment (v0.14.5): points add up to the assignment's maximum marks. */
export type AiRubricSuggestion = {
  title: string
  maxMarks: number
  criteria: {
    title: string
    description: string | null
    maxPoints: number
    levels: { title: string; description: string | null; points: number }[]
  }[]
  citations: AiCitation[]
  insufficientContext: boolean
}

/** One of the signed-in student's private notes in a course (v0.13.6). */
export type AiStudentNote = {
  id: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

/** A teacher-only document (v0.13.5): plain text only Teacher AI read. Students never see it. */
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
