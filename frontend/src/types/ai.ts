export type AiSourceType = 'TOPIC' | 'STUDY_MATERIAL' | 'TEACHER_DOCUMENT' | 'NOTE'

export type AiCitation = {
  sourceType: AiSourceType
  sourceId: number
  title: string
  fileName: string | null
  pageNumber: number | null
  slideNumber: number | null
  pageId?: number | null
}

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

export type AiTopicMastery = {
  topicId: number
  title: string
  level: 'NOT_STARTED' | 'LEARNING' | 'PRACTISING' | 'MASTERED'
  mastery: number
  confidence: number
  evidence: number
  lastPractisedAt: string | null
}

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
export type AiFlashcardSet = { id: number; title: string; cards: AiFlashcard[]; today: string }
export type ReviewRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'

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

export type AiAnswerStyle = 'NORMAL' | 'SIMPLER' | 'EXAMPLE'

export type AiFollowUpTurn = { question: string; answer: string }

export type AiSavedAnswer = { id: number; question: string; answer: string; citations: AiCitation[]; savedAt: string }

export type AiAskMode = 'COURSE' | 'COURSE_AND_NOTES' | 'NOTES_ONLY'

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
  chosenTopicId?: number | null
  reason?: string | null
}

export type AiStudyPlanDay = {
  date: string
  busy: boolean
  budget: number
  planned: number
  sessions: { kind: 'ASSIGNMENT' | 'PRACTISE_WEAK' | 'START_PAGE' | 'REFRESH_PAGE' | 'FLASHCARDS'; targetId: number | null; title: string; minutes: number }[]
}

export type AiCourseStudy = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  pagesByLevel: Record<'NOT_STARTED' | 'LEARNING' | 'PRACTISING' | 'MASTERED', number>
  flashcardsDue: number
  nextStep: AiRecommendation | null
  assistantOn: boolean
}

export type AiRecommendation = {
  kind: 'PRACTISE_WEAK' | 'START_PAGE' | 'REVIEW_FLASHCARDS' | 'ASSIGNMENT_SOON' | 'REFRESH_PAGE'
  targetId: number | null
  title: string
  reason: string
}

export type AiKnowledgeIndexStatus = 'NOT_INDEXED' | 'BUILDING' | 'READY' | 'FAILED'

export type AiKnowledgeStatus = {
  enabled: boolean
  status: AiKnowledgeIndexStatus
  stale: boolean
  indexedAt: string | null
  publishedSourceCount: number
  failureMessage: string | null
  job: AiJob | null
  teacherDocumentCount: number
  teacherDocumentsCurrent: boolean
  studentAiEnabled: boolean
}

export type AiQuestionBankDraft = {
  added: number
  droppedForKeyMismatch: number
  droppedAsRepeats: number
  leftOut: number
  insufficientContext: boolean
  questionIds: number[]
}

export type AiQuizDraft = {
  quizId: number
  fromBank: number
  fromAi: number
  missing: number
  aiNote: string | null
}

export type AiModelAnswer = {
  answer: string | null
  keyPoints: string[]
  citations: AiCitation[]
  insufficientContext: boolean
}

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

export type AiStudentNote = {
  id: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export type AiTeacherDocument = {
  id: number
  title: string
  content: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

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
  options: string[]
  suggestedAnswer: string
  suggestedMarks: number
  citations: AiCitation[]
}

export type AiAssessmentDraft = {
  titleSuggestion: string | null
  instructionsSuggestion: string | null
  questions: AiAssessmentQuestion[]
  insufficientContext: boolean
}

export type AssignmentDraftPrefill = {
  title: string
  instructions: string
  maxMarks: number | null
}
