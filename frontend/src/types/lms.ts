import type { TextFormat } from '../formatting/textFormat'

export type LmsCourseSummary = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  sectionCode: string | null
  faculty: string[]
}

export type LmsFacultyCourseStatus = 'DRAFT' | 'PUBLISHED'

export type LmsCourseDetail = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  credits: number
  departmentCode: string
  departmentName: string
  programCode: string
  programName: string
  semesterNumber: number
  academicSessionCode: string
  sectionCode: string | null
  faculty: string[]
  homeView: LmsCourseHomeView
  welcomeMessage: string | null
}

export type LmsContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type LmsCourseMaterial = {
  id: number
  title: string
  description: string | null
  originalFileName: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export type LmsCourseTopic = {
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  materials: LmsCourseMaterial[]
  required: boolean
  completed: boolean
}

export type LmsCourseModule = {
  id: number
  title: string
  description: string | null
  topics: LmsCourseTopic[]
  requiresPrevious: boolean
  locked: boolean
  completed: boolean
  waitingFor: string | null
}

export type LmsCourseContent = {
  modules: LmsCourseModule[]
}

export type LmsFacultyTopic = {
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  position: number
  status: LmsContentStatus
  required: boolean
}

export type LmsFacultyMaterial = LmsCourseMaterial & {
  position: number
  status: LmsContentStatus
  excludedFromAi: boolean
}

export type LmsFacultyTopicContent = LmsFacultyTopic & {
  materials: LmsFacultyMaterial[]
}

export type LmsFacultyModule = {
  id: number
  title: string
  description: string | null
  position: number
  status: LmsContentStatus
  requiresPrevious: boolean
}

export type LmsFacultyModuleContent = LmsFacultyModule & {
  topics: LmsFacultyTopicContent[]
}

export type LmsFacultyCourseContent = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  lmsCourseStatus: LmsFacultyCourseStatus
  modules: LmsFacultyModuleContent[]
  homeView: LmsCourseHomeView
  welcomeMessage: string | null
}

export type LmsCourseHomeView = 'OVERVIEW' | 'MODULES' | 'SYLLABUS'

export type LmsModuleInput = {
  title: string
  description: string | null
}

export type LmsMaterialUpload = {
  title: string
  description: string | null
  file: File
}

export type LmsMaterialInput = {
  title: string
  description: string | null
}

export type LmsTopicInput = {
  title: string
  content: string | null
  contentFormat?: TextFormat
}

export type LmsAnnouncementFile = {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
}

export type LmsCourseAnnouncement = {
  id: number
  title: string
  message: string
  messageFormat: TextFormat
  publishedAt: string
  files?: LmsAnnouncementFile[]
}

export type LmsFacultyAnnouncement = {
  id: number
  title: string
  message: string
  messageFormat: TextFormat
  status: LmsContentStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  files?: LmsAnnouncementFile[]
}

export type LmsAnnouncementInput = {
  title: string
  message: string
  messageFormat?: TextFormat
}

export type LmsCourseAssignment = {
  id: number
  title: string
  dueAt: string
  maxMarks: number
}

export type LmsCourseAssignmentDetail = {
  id: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  instructions: string
  instructionsFormat: TextFormat
  dueAt: string
  maxMarks: number
  publishedAt: string
  bloomLevel: BloomLevel | null
  outcomes: LmsOutcome[]
  rubric: LmsRubric | null
  availableFrom: string | null
  availableUntil: string | null
  allowLateSubmissions: boolean
  maxAttempts: number | null
  submissionWindow: 'NOT_OPEN' | 'OPEN' | 'CLOSED'
}

export type LmsFacultyAssignment = {
  id: number
  title: string
  instructions: string
  instructionsFormat: TextFormat
  dueAt: string
  maxMarks: number
  status: LmsContentStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  bloomLevel: BloomLevel | null
  outcomes: LmsOutcome[]
  rubric: LmsRubric | null
  availableFrom: string | null
  availableUntil: string | null
  allowLateSubmissions: boolean
  maxAttempts: number | null
}

export type LmsFacultyAssignmentOverview = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  assignmentId: number
  title: string
  dueAt: string
  maxMarks: number
  status: LmsContentStatus
}

export type LmsAssignmentInput = {
  title: string
  instructions: string
  instructionsFormat?: TextFormat
  dueAt: string
  maxMarks: number
}

export type LmsSubmissionAttachment = {
  attachmentId: number
  originalFileName: string
  contentType: string
  sizeBytes: number
}

export type LmsSubmissionAttempt = {
  attemptId: number
  attemptNumber: number
  textContent: string | null
  submittedAt: string
  late: boolean
  attachments: LmsSubmissionAttachment[]
  grade: LmsSubmissionGrade | null
}

export type LmsSubmissionHistory = {
  attempts: LmsSubmissionAttempt[]
}

export type LmsSubmissionGrade = {
  marksAwarded: number
  maxMarksSnapshot: number
  feedback: string | null
  gradedAt: string
  rubric: { criterion: string; points: number; maxPoints: number; comment: string | null }[]
}

export type LmsFacultySubmissionSummary = {
  submissionId: number
  studentLoginId: string
  latestAttemptNumber: number
  attemptCount: number
  latestSubmittedAt: string
  latestLate: boolean
  latestGraded: boolean
  latestMarksAwarded: number | null
  latestMaxMarksSnapshot: number | null
}

export type LmsFacultySubmissionAttempt = {
  attemptId: number
  attemptNumber: number
  textContent: string | null
  submittedAt: string
  dueAtSnapshot: string
  late: boolean
  attachments: LmsSubmissionAttachment[]
  grade: LmsSubmissionGrade | null
}

export type LmsFacultySubmissionDetail = {
  assignmentId: number
  title: string
  maxMarks: number
  dueAt: string
  studentLoginId: string
  submissionId: number
  attempts: LmsFacultySubmissionAttempt[]
  rubric: LmsRubric | null
}

export type LmsGradeInput = {
  marksAwarded: number
  feedback: string | null
}

export type LmsCourseLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type LmsManagedCourse = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  lmsCourseId: number | null
  lmsCourseStatus: LmsCourseLifecycleStatus | null
  sectionCode: string | null
  teachingRole: 'PRIMARY' | 'CO_FACULTY'
  enrolledCount: number
}

export type LmsCourseManagement = {
  lmsCourseId: number
  courseOfferingId: number
  lmsCourseStatus: LmsCourseLifecycleStatus
  currentStudentCount: number | null
}

export type LmsPageLink = {
  id: number
  title: string
  moduleTitle: string
}

export type LmsCoursePage = {
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  moduleId: number
  moduleTitle: string
  materials: LmsCourseMaterial[]
  previous: LmsPageLink | null
  next: LmsPageLink | null
  required: boolean
  completed: boolean
  locked: boolean
  waitingFor: string | null
}

export type LmsCourseProgress = {
  requiredPages: number
  completedRequiredPages: number
  next: LmsPageLink | null
}

export type LmsFacultyPage = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  lmsCourseStatus: 'DRAFT' | 'PUBLISHED'
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  status: LmsContentStatus
  moduleId: number
  moduleTitle: string
  moduleStatus: LmsContentStatus
  materials: LmsFacultyMaterial[]
  previous: LmsPageLink | null
  next: LmsPageLink | null
}

export type LmsCourseFile = {
  id: number
  title: string
  description: string | null
  originalFileName: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
  pageId: number
  pageTitle: string
  moduleTitle: string
}

export type LmsFacultyCourseFile = Omit<LmsCourseFile, 'pageId' | 'pageTitle' | 'moduleTitle'> & {
  status: LmsContentStatus
  pageId: number
  pageTitle: string
  pageStatus: LmsContentStatus
  moduleTitle: string
  moduleStatus: LmsContentStatus
  excludedFromAi: boolean
}

export type LmsFacultyCourseFiles = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  lmsCourseStatus: 'DRAFT' | 'PUBLISHED'
  files: LmsFacultyCourseFile[]
}

export type LmsCourseSyllabus = {
  content: string | null
  publishedAt: string | null
}

export type LmsFacultySyllabus = {
  draftContent: string | null
  draftUpdatedAt: string | null
  publishedContent: string | null
  firstPublishedAt: string | null
  lastPublishedAt: string | null
  unpublishedChanges: boolean
}

export type BloomLevel = 'REMEMBER' | 'UNDERSTAND' | 'APPLY' | 'ANALYZE' | 'EVALUATE' | 'CREATE'

export type LmsOutcome = {
  id: number
  code: string
  description: string
  status: 'ACTIVE' | 'ARCHIVED'
}

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'DESCRIPTIVE'

export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD'

export type LmsQuestionOption = {
  text: string
  correct: boolean
}

export type LmsQuestion = {
  id: number
  type: QuestionType
  text: string
  textFormat: TextFormat
  explanation: string | null
  options: LmsQuestionOption[]
  trueFalseAnswer: boolean | null
  answerGuidance: string | null
  topic: string | null
  difficulty: QuestionDifficulty
  marks: number
  bloomLevel: BloomLevel | null
  outcome: LmsOutcome | null
  reviewStatus: 'NEEDS_REVIEW' | 'REVIEWED'
  practiceAllowed: boolean
  status: 'ACTIVE' | 'ARCHIVED'
  inPublishedQuiz: boolean
  updatedAt: string
  aiGenerated?: boolean
  sources?: LmsQuestionSource[]
  aiChecks?: {
    duplicateOfQuestionId: number | null
    inPreviousPaper: boolean
    outOfSyllabus: boolean
    suggestedDifficulty: QuestionDifficulty | null
    suggestedBloomLevel: BloomLevel | null
  } | null
}

export type LmsQuestionSource = {
  sourceType: 'TOPIC' | 'STUDY_MATERIAL' | 'TEACHER_DOCUMENT'
  sourceId: number
  title: string
  fileName: string | null
  pageNumber: number | null
  slideNumber: number | null
  pageId: number | null
}

export type LmsQuestionInput = {
  type: QuestionType
  text: string
  textFormat: TextFormat
  explanation: string | null
  options: LmsQuestionOption[]
  trueFalseAnswer: boolean | null
  answerGuidance: string | null
  topic: string | null
  difficulty: QuestionDifficulty
  marks: number
  bloomLevel: BloomLevel | null
  outcomeId: number | null
}

export type QuizType = 'GRADED' | 'PRACTICE'

export type LmsQuizSummary = {
  id: number
  title: string
  type: QuizType
  status: LmsContentStatus
  questionCount: number
  totalMarks: number
  timeLimitMinutes: number | null
  availableFrom: string | null
  dueAt: string | null
  availableUntil: string | null
  maxAttempts: number | null
  publishedAt: string | null
}

export type LmsQuizQuestion = {
  id: number
  position: number
  marks: number
  questionId: number
  type: QuestionType
  text: string
  textFormat: TextFormat
  topic: string | null
  difficulty: QuestionDifficulty
  bankMarks: number
  reviewStatus: 'NEEDS_REVIEW' | 'REVIEWED'
  practiceAllowed: boolean
  status: 'ACTIVE' | 'ARCHIVED'
}

export type LmsQuizSettingsInput = {
  title: string
  instructions: string | null
  instructionsFormat: TextFormat
  type: QuizType
  timeLimitMinutes: number | null
  availableFrom: string | null
  dueAt: string | null
  availableUntil: string | null
  maxAttempts: number | null
  shuffleQuestions: boolean
  shuffleOptions: boolean
  negativeMarkPercent: number
  resultRelease: QuizResultRelease
  showAnswers: boolean
}

export type QuizResultRelease = 'AFTER_MARKING' | 'AFTER_DUE_DATE'

export type LmsQuiz = Omit<LmsQuizSettingsInput, 'instructions'> & {
  id: number
  lmsCourseId: number
  instructions: string | null
  status: LmsContentStatus
  publishedAt: string | null
  totalMarks: number
  questions: LmsQuizQuestion[]
}

export type QuizAvailability = 'NOT_OPEN' | 'OPEN' | 'CLOSED'

export type QuizAttemptState = 'IN_PROGRESS' | 'TIME_UP' | 'SUBMITTED' | 'EXPIRED'

export type QuizAttemptSummary = {
  id: number
  attemptNumber: number
  state: QuizAttemptState
  startedAt: string
  deadlineAt: string | null
  submittedAt: string | null
  late: boolean
  resultStatus: QuizResultStatus
  score: number | null
}

export type QuizResultStatus = 'NOT_HANDED_IN' | 'WAITING_FOR_MARKING' | 'WAITING_FOR_RELEASE' | 'RELEASED'

export type QuizAnswerOutcome = 'CORRECT' | 'INCORRECT' | 'UNANSWERED' | 'NEEDS_MARKING' | 'MARKED'

export type QuizResult = {
  score: number
  totalMarks: number
  correct: number
  incorrect: number
  unanswered: number
  questions: {
    quizQuestionId: number
    outcome: QuizAnswerOutcome
    marksAwarded: number
    feedback: string | null
    correctOptions: number[] | null
    acceptedAnswers: string[] | null
    trueFalseAnswer: boolean | null
    explanation: string | null
  }[]
}

export type StudentQuiz = {
  id: number
  title: string
  instructions: string | null
  instructionsFormat: TextFormat
  type: QuizType
  timeLimitMinutes: number | null
  availableFrom: string | null
  dueAt: string | null
  availableUntil: string | null
  maxAttempts: number | null
  negativeMarkPercent: number
  questionCount: number
  totalMarks: number
  availability: QuizAvailability
  attemptsLeft: number | null
  openAttemptId: number | null
  startable: boolean
  attempts: QuizAttemptSummary[]
}

export type QuizAnswer = {
  quizQuestionId: number
  selectedOptions: number[]
  trueFalseAnswer: boolean | null
  text: string | null
  answeredAt: string
}

export type QuizAnswerInput = { selectedOptions: number[]; trueFalseAnswer: boolean | null; text: string | null }

export type QuizAttemptQuestion = {
  quizQuestionId: number
  number: number
  type: QuestionType
  text: string
  textFormat: TextFormat
  marks: number
  options: { position: number; text: string }[]
  answer: QuizAnswer | null
}

export type QuizAttempt = {
  id: number
  quizId: number
  lmsCourseId: number
  quizTitle: string
  quizType: QuizType
  attemptNumber: number
  state: QuizAttemptState
  startedAt: string
  deadlineAt: string | null
  submittedAt: string | null
  late: boolean
  serverTime: string
  totalMarks: number
  resultStatus: QuizResultStatus
  result: QuizResult | null
  questions: QuizAttemptQuestion[]
}

export type QuizResults = {
  quizId: number
  title: string
  totalMarks: number
  attempts: {
    attemptId: number
    studentLoginId: string
    attemptNumber: number
    state: QuizAttemptState
    startedAt: string
    submittedAt: string | null
    late: boolean
    automaticMarks: number
    teacherMarks: number
    score: number | null
    needsMarking: number
  }[]
}

export type QuizAttemptReview = {
  attemptId: number
  quizId: number
  lmsCourseId: number
  quizTitle: string
  quizPublished: boolean
  studentLoginId: string
  attemptNumber: number
  state: QuizAttemptState
  startedAt: string
  deadlineAt: string | null
  submittedAt: string | null
  late: boolean
  negativeMarkPercent: number
  totalMarks: number
  automaticMarks: number
  teacherMarks: number
  score: number | null
  questions: {
    quizQuestionId: number
    number: number
    type: QuestionType
    text: string
    textFormat: TextFormat
    marks: number
    options: LmsQuestionOption[]
    trueFalseAnswer: boolean | null
    answerGuidance: string | null
    explanation: string | null
    answer: QuizAnswer | null
    outcome: QuizAnswerOutcome
    automaticMarks: number | null
    mark: { marks: number; feedback: string | null; markedAt: string } | null
  }[]
}

export type GradeItemType = 'ASSIGNMENT' | 'QUIZ' | 'MANUAL'

export type GradeCellStatus = 'GRADED' | 'NEEDS_GRADING' | 'NO_MARKS'

export type GradebookColumn = {
  type: GradeItemType
  sourceId: number
  title: string
  maxMarks: number
  dueAt: string | null
  published: boolean
  publishedAt: string | null
}

export type GradebookCell = { marks: number | null; status: GradeCellStatus; feedback: string | null }

export type Gradebook = {
  columns: GradebookColumn[]
  students: {
    courseEnrollmentId: number
    studentLoginId: string
    cells: GradebookCell[]
    totalMarks: number
    totalOutOf: number
  }[]
}

export type StudentGrades = {
  items: {
    type: GradeItemType
    title: string
    dueAt: string | null
    marks: number | null
    maxMarks: number
    status: GradeCellStatus
    feedback: string | null
  }[]
  totalMarks: number
  totalOutOf: number
}

export type LmsRubric = {
  id: number
  title: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  totalPoints: number
  inUse: boolean
  criteria: {
    id: number
    title: string
    description: string | null
    maxPoints: number
    levels: { title: string; description: string | null; points: number }[]
  }[]
}

export type LmsRubricInput = {
  title: string
  description: string | null
  criteria: {
    title: string
    description: string | null
    maxPoints: number
    levels: { title: string; description: string | null; points: number }[]
  }[]
}


export type ExamBlueprintRow = {
  position: number
  sectionTitle: string | null
  questionType: QuestionType | null
  difficulty: QuestionDifficulty | null
  bloomLevel: BloomLevel | null
  outcomeId: number | null
  topic: string | null
  questionCount: number
  marksEach: number
}

export type ExamBlueprintRowInput = Omit<ExamBlueprintRow, 'position'>

export type ExamBlueprint = {
  id: number
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  rows: ExamBlueprintRow[]
  questionCount: number
  totalMarks: number
  updatedAt: string
}

export type ExamPaperStatus = 'DRAFT' | 'FINAL' | 'ARCHIVED'

export type ExamPaperSummary = {
  id: number
  title: string
  status: ExamPaperStatus
  blueprintId: number | null
  durationMinutes: number | null
  questionCount: number
  totalMarks: number
  finalizedAt: string | null
  updatedAt: string
}

export type ExamPaperQuestion = {
  id: number
  questionId: number
  position: number
  sectionTitle: string | null
  marks: number
  type: QuestionType
  text: string
  textFormat: TextFormat
  explanation: string | null
  options: LmsQuestionOption[]
  trueFalseAnswer: boolean | null
  answerGuidance: string | null
  topic: string | null
  difficulty: QuestionDifficulty
  bloomLevel: BloomLevel | null
  outcomeCode: string | null
  reviewStatus: 'NEEDS_REVIEW' | 'REVIEWED'
  status: 'ACTIVE' | 'ARCHIVED'
}

export type ExamPaper = {
  id: number
  title: string
  instructions: string | null
  durationMinutes: number | null
  status: ExamPaperStatus
  blueprintId: number | null
  courseCode: string
  courseName: string
  totalMarks: number
  finalizedAt: string | null
  updatedAt: string
  questions: ExamPaperQuestion[]
}

export type ExamPaperDetailsInput = {
  title: string | null
  instructions: string | null
  durationMinutes: number | null
}

export type ExamPaperQuestionInput = {
  questionId: number
  sectionTitle: string | null
  marks: number
}
