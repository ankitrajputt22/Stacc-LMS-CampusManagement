import type { TextFormat } from '../formatting/textFormat'
// These types mirror the backend's LMS answers in com.stacc.backend.lms.api.

/** One LMS course the signed-in student can currently use, from GET /api/v1/lms/my-courses. */
export type LmsCourseSummary = {
  /** Identifies the LMS learning space. */
  lmsCourseId: number
  courseCode: string
  courseName: string
  /** An exact decimal on the backend, such as 4.00 or 1.50. JSON carries it as a number. */
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  /** The section the offering is for, or null for the whole semester. */
  sectionCode: string | null
  /** Names of the faculty teaching it, primary first. */
  faculty: string[]
}

/** The states in which faculty can use an LMS course. Archived courses are never listed for them. */
export type LmsFacultyCourseStatus = 'DRAFT' | 'PUBLISHED'

/** One LMS course the signed-in student can currently open, from GET /api/v1/lms/courses/{lmsCourseId}. */
export type LmsCourseDetail = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  /** An exact decimal on the backend, such as 4.00 or 1.50. JSON carries it as a number. */
  credits: number
  /** The department that owns the course. It may differ from the program's department. */
  departmentCode: string
  departmentName: string
  programCode: string
  programName: string
  semesterNumber: number
  academicSessionCode: string
  /** The section, or null when the offering is for the whole semester. */
  sectionCode: string | null
  /** The names of the active faculty teaching the offering. */
  faculty: string[]
  homeView: LmsCourseHomeView
  /** Formatted text, or null. */
  welcomeMessage: string | null
}

/** Where a module or topic is in its life. Students only ever receive published content. */
export type LmsContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/** One published study material, as a student sees it. Where the file is stored is never sent. */
export type LmsCourseMaterial = {
  id: number
  title: string
  description: string | null
  originalFileName: string
  contentType: string
  sizeBytes: number
  /** When the file was uploaded. */
  uploadedAt: string
}

/** One published topic, as a student sees it. The content is plain text, never HTML. */
export type LmsCourseTopic = {
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  /** Its published study materials. Often empty. */
  materials: LmsCourseMaterial[]
  /** Whether the page counts towards its module's completion and the student's progress. */
  required: boolean
  /** Whether this student marked the page complete. */
  completed: boolean
}

/** One published module with its published topics, as a student sees it. */
export type LmsCourseModule = {
  id: number
  title: string
  description: string | null
  topics: LmsCourseTopic[]
  /** Whether students must complete the module before this one first. */
  requiresPrevious: boolean
  /** Locked for this student: its pages come without text or files. */
  locked: boolean
  /** Every required page of the module is completed by this student. */
  completed: boolean
  /** The module to complete first, when locked. */
  waitingFor: string | null
}

/** The published content of a course, from GET /api/v1/lms/courses/{lmsCourseId}/content. */
export type LmsCourseContent = {
  modules: LmsCourseModule[]
}

/** One topic as faculty manage it. */
export type LmsFacultyTopic = {
  id: number
  title: string
  content: string | null
  contentFormat: TextFormat
  position: number
  status: LmsContentStatus
  /** Whether students must complete the page. */
  required: boolean
}

/** One study material as faculty manage it. */
export type LmsFacultyMaterial = LmsCourseMaterial & {
  position: number
  status: LmsContentStatus
}

/** One topic with its study materials that are not archived. */
export type LmsFacultyTopicContent = LmsFacultyTopic & {
  materials: LmsFacultyMaterial[]
}

/** One module as faculty manage it, without its topics: the answer to a change. */
export type LmsFacultyModule = {
  id: number
  title: string
  description: string | null
  position: number
  status: LmsContentStatus
  /** Whether students must complete the previous module first. */
  requiresPrevious: boolean
}

/** One module with its topics that are not archived. */
export type LmsFacultyModuleContent = LmsFacultyModule & {
  topics: LmsFacultyTopicContent[]
}

/** A course a faculty member teaches, with its content, from GET /api/v1/lms/faculty/courses/{lmsCourseId}/content. */
export type LmsFacultyCourseContent = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  /** An exact decimal on the backend, such as 4.00 or 1.50. JSON carries it as a number. */
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  lmsCourseStatus: LmsFacultyCourseStatus
  modules: LmsFacultyModuleContent[]
  homeView: LmsCourseHomeView
  welcomeMessage: string | null
}

/** What a course's home shows students. */
export type LmsCourseHomeView = 'OVERVIEW' | 'MODULES' | 'SYLLABUS'

/** The text of a module, for adding or editing one. */
export type LmsModuleInput = {
  title: string
  description: string | null
}

/** A new study material: its text and the chosen file. Everything else is decided by the server. */
export type LmsMaterialUpload = {
  title: string
  description: string | null
  file: File
}

/** The text of a study material, for editing one. The file itself cannot be changed. */
export type LmsMaterialInput = {
  title: string
  description: string | null
}

/** The text of a topic, for adding or editing one. */
export type LmsTopicInput = {
  title: string
  content: string | null
  /** Left out, a new text is plain and an edited one keeps its format. */
  contentFormat?: TextFormat
}

/** One published announcement of a course, as a student sees it. The message is plain text. */
export type LmsCourseAnnouncement = {
  id: number
  title: string
  message: string
  messageFormat: TextFormat
  /** When it was first published, as an ISO 8601 time in UTC. */
  publishedAt: string
}

/** One announcement as faculty manage it. Archived announcements are not sent. */
export type LmsFacultyAnnouncement = {
  id: number
  title: string
  message: string
  messageFormat: TextFormat
  status: LmsContentStatus
  /** When it was first published, or null for a draft. Corrections never change it. */
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

/** The title and message of an announcement, for adding or editing one. */
export type LmsAnnouncementInput = {
  title: string
  message: string
  /** Left out, a new text is plain and an edited one keeps its format. */
  messageFormat?: TextFormat
}

/** One published assignment in a student's course list. Past-due assignments are included. */
export type LmsCourseAssignment = {
  id: number
  title: string
  /** The deadline, as an ISO 8601 time in UTC. */
  dueAt: string
  /** An exact decimal on the backend, such as 20.00 or 12.50. JSON carries it as a number. */
  maxMarks: number
}

/** One published assignment as a student opens it. The instructions are plain text. */
export type LmsCourseAssignmentDetail = {
  id: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  instructions: string
  instructionsFormat: TextFormat
  /** The deadline, as an ISO 8601 time in UTC. */
  dueAt: string
  maxMarks: number
  /** When it was first published, as an ISO 8601 time in UTC. */
  publishedAt: string
  bloomLevel: BloomLevel | null
  outcomes: LmsOutcome[]
}

/** One assignment as faculty manage it. Archived assignments are not sent. */
export type LmsFacultyAssignment = {
  id: number
  title: string
  instructions: string
  instructionsFormat: TextFormat
  dueAt: string
  maxMarks: number
  status: LmsContentStatus
  /** When it was first published, or null for a draft. Edits never change it. */
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  /** The Bloom level it works at, or null. */
  bloomLevel: BloomLevel | null
  /** The course outcomes it assesses, by code. */
  outcomes: LmsOutcome[]
}

/** One assignment in a faculty member's list across their courses, from GET /api/v1/lms/faculty/my-assignments. */
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

/** The definition of an assignment, for adding or editing one. */
export type LmsAssignmentInput = {
  title: string
  instructions: string
  /** Left out, a new text is plain and an edited one keeps its format. */
  instructionsFormat?: TextFormat
  /** An absolute moment as an ISO 8601 time with a zone, such as 2026-10-20T18:29:00.000Z. */
  dueAt: string
  maxMarks: number
}

/** One file of a submission attempt. Where it is stored is never sent. */
export type LmsSubmissionAttachment = {
  attachmentId: number
  originalFileName: string
  contentType: string
  sizeBytes: number
}

/** One of the signed-in student's own attempts. It never changes once it is made. */
export type LmsSubmissionAttempt = {
  attemptId: number
  attemptNumber: number
  /** Plain text with line breaks, or null when only files were sent. */
  textContent: string | null
  /** When the server accepted it, as an ISO 8601 time in UTC. */
  submittedAt: string
  /** Decided by the server against the deadline that applied at the time. Never recomputed here. */
  late: boolean
  attachments: LmsSubmissionAttachment[]
  /** This attempt's own grade, or null while it is not graded. A newer attempt starts without one. */
  grade: LmsSubmissionGrade | null
}

/** The signed-in student's attempts for one assignment, newest first. Empty before the first. */
export type LmsSubmissionHistory = {
  attempts: LmsSubmissionAttempt[]
}

/** The grade of one attempt. Who graded it is not sent. */
export type LmsSubmissionGrade = {
  /** An exact decimal on the backend, such as 18.00 or 12.50. JSON carries it as a number. */
  marksAwarded: number
  /** The assignment's maximum when the attempt was first graded. It never changes afterwards. */
  maxMarksSnapshot: number
  /** Plain text with line breaks, or null. */
  feedback: string | null
  /** When the current marks and feedback were saved, as an ISO 8601 time in UTC. */
  gradedAt: string
}

/** One student's submission for an assignment, summed up by its latest attempt, for faculty. */
export type LmsFacultySubmissionSummary = {
  submissionId: number
  /** The student's college ID. There is no name to show. */
  studentLoginId: string
  latestAttemptNumber: number
  attemptCount: number
  latestSubmittedAt: string
  latestLate: boolean
  /** Whether the latest attempt is graded. An older attempt's grade does not count. */
  latestGraded: boolean
  latestMarksAwarded: number | null
  latestMaxMarksSnapshot: number | null
}

/** One attempt as faculty review it. */
export type LmsFacultySubmissionAttempt = {
  attemptId: number
  attemptNumber: number
  textContent: string | null
  submittedAt: string
  /** The deadline that applied when the attempt was made. */
  dueAtSnapshot: string
  late: boolean
  attachments: LmsSubmissionAttachment[]
  grade: LmsSubmissionGrade | null
}

/** One student's submission with every attempt, newest first, for faculty review. */
export type LmsFacultySubmissionDetail = {
  assignmentId: number
  title: string
  /** The assignment's maximum now. A first grade is out of this. */
  maxMarks: number
  dueAt: string
  studentLoginId: string
  submissionId: number
  attempts: LmsFacultySubmissionAttempt[]
}

/** The marks and feedback for one attempt. */
export type LmsGradeInput = {
  marksAwarded: number
  feedback: string | null
}

/** An LMS course's whole lifecycle, as faculty management sees it. Archived is final. */
export type LmsCourseLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/**
 * One course offering the faculty member officially teaches, with its LMS course if it has one. An offering without
 * one has null for both LMS fields; that is not a stored status.
 */
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
  /** The section the offering is for, or null for the whole semester. */
  sectionCode: string | null
  teachingRole: 'PRIMARY' | 'CO_FACULTY'
  /** Students enrolled in the offering now. */
  enrolledCount: number
}

/** An LMS course after a management change. */
export type LmsCourseManagement = {
  lmsCourseId: number
  courseOfferingId: number
  lmsCourseStatus: LmsCourseLifecycleStatus
  /** How many students are enrolled in the offering now; null for archiving. */
  currentStudentCount: number | null
}

/** The page before or after another, in course order. */
export type LmsPageLink = {
  id: number
  title: string
  moduleTitle: string
}

/** One published page (a topic of a module), as a student opens it at /courses/:lmsCourseId/pages/:pageId. */
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
  /** The page's module is locked for this student: then there is no text and there are no files. */
  locked: boolean
  waitingFor: string | null
}

/** A student's own progress in a course, from GET /api/v1/lms/courses/{lmsCourseId}/progress. Nothing is stored. */
export type LmsCourseProgress = {
  requiredPages: number
  completedRequiredPages: number
  /** The next required page to complete in an open module, or null when none is left. */
  next: LmsPageLink | null
}

/** One page of a course the faculty member teaches, drafts included, for previewing it as students will see it. */
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

/** One published file of a course in its Files list: a study material with the page and module it is on. */
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

/** One file of a course a faculty member teaches, drafts included, with its page's and module's statuses. */
export type LmsFacultyCourseFile = Omit<LmsCourseFile, 'pageId' | 'pageTitle' | 'moduleTitle'> & {
  status: LmsContentStatus
  pageId: number
  pageTitle: string
  pageStatus: LmsContentStatus
  moduleTitle: string
  moduleStatus: LmsContentStatus
}

/** The Files list of a course a faculty member teaches. */
export type LmsFacultyCourseFiles = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  lmsCourseStatus: 'DRAFT' | 'PUBLISHED'
  files: LmsFacultyCourseFile[]
}

/** The published syllabus of a course, as a student reads it. Both are null when none is published yet. */
export type LmsCourseSyllabus = {
  content: string | null
  publishedAt: string | null
}

/** A course's syllabus as faculty manage it. Everything is null when none is saved yet. */
export type LmsFacultySyllabus = {
  draftContent: string | null
  draftUpdatedAt: string | null
  publishedContent: string | null
  firstPublishedAt: string | null
  lastPublishedAt: string | null
  unpublishedChanges: boolean
}

/** The six levels of the revised Bloom's taxonomy, lowest first. */
export type BloomLevel = 'REMEMBER' | 'UNDERSTAND' | 'APPLY' | 'ANALYZE' | 'EVALUATE' | 'CREATE'

/** One course learning outcome, such as CO2. Plain text. */
export type LmsOutcome = {
  id: number
  code: string
  description: string
  status: 'ACTIVE' | 'ARCHIVED'
}

/** The kinds of question in a course's question bank. */
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'DESCRIPTIVE'

export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD'

/** A choice with whether it is correct, or an accepted short answer (always correct). Plain text. */
export type LmsQuestionOption = {
  text: string
  correct: boolean
}

/** One question of the bank with its answers. Only faculty who teach the course receive it. */
export type LmsQuestion = {
  id: number
  type: QuestionType
  text: string
  /** Covers the question text and the explanation. */
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
  /** A published or archived quiz uses the question, so it can no longer be edited. */
  inPublishedQuiz: boolean
  updatedAt: string
}

/** What a faculty member writes when adding or editing a question. */
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

/** One quiz in the faculty list of a course. */
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

/** One question of a quiz: its place and marks in the quiz, and what the bank says about it. */
export type LmsQuizQuestion = {
  /** The ID of this question in this quiz, used to re-mark, move, or remove it. */
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

/** The settings faculty choose for a quiz; empty optional values mean no limit. */
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
}

/** A quiz with its settings and questions, for the faculty who teach the course. */
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

/** IN_PROGRESS, TIME_UP (still open but past its deadline: no more answers), SUBMITTED, or EXPIRED. */
export type QuizAttemptState = 'IN_PROGRESS' | 'TIME_UP' | 'SUBMITTED' | 'EXPIRED'

export type QuizAttemptSummary = {
  id: number
  attemptNumber: number
  state: QuizAttemptState
  startedAt: string
  deadlineAt: string | null
  submittedAt: string | null
  late: boolean
}

/** A published quiz as the signed-in student sees it, with their own attempts. Never its questions. */
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
  /** Choices of a single or multiple choice question, in this attempt's order. */
  options: { position: number; text: string }[]
  answer: QuizAnswer | null
}

/** One of the student's own attempts, with its questions and the answers saved so far. */
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
  /** The server's time when the answer was made, so the timer can allow for the device's clock. */
  serverTime: string
  totalMarks: number
  questions: QuizAttemptQuestion[]
}

