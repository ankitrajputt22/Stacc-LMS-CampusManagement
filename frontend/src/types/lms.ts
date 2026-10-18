// These types mirror the backend's LMS answers in com.stacc.backend.lms.api.

/** One LMS course the signed-in student can currently use, from GET /api/lms/my-courses. */
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
}

/** The states in which faculty can use an LMS course. Archived courses are never listed for them. */
export type LmsFacultyCourseStatus = 'DRAFT' | 'PUBLISHED'

/** One LMS course the signed-in student can currently open, from GET /api/lms/courses/{lmsCourseId}. */
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
}

/** One published topic, as a student sees it. The content is plain text, never HTML. */
export type LmsCourseTopic = {
  id: number
  title: string
  content: string | null
  /** Its published study materials. Often empty. */
  materials: LmsCourseMaterial[]
}

/** One published module with its published topics, as a student sees it. */
export type LmsCourseModule = {
  id: number
  title: string
  description: string | null
  topics: LmsCourseTopic[]
}

/** The published content of a course, from GET /api/lms/courses/{lmsCourseId}/content. */
export type LmsCourseContent = {
  modules: LmsCourseModule[]
}

/** One topic as faculty manage it. */
export type LmsFacultyTopic = {
  id: number
  title: string
  content: string | null
  position: number
  status: LmsContentStatus
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
}

/** One module with its topics that are not archived. */
export type LmsFacultyModuleContent = LmsFacultyModule & {
  topics: LmsFacultyTopicContent[]
}

/** A course a faculty member teaches, with its content, from GET /api/lms/faculty/courses/{lmsCourseId}/content. */
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
}

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
}

/** One published announcement of a course, as a student sees it. The message is plain text. */
export type LmsCourseAnnouncement = {
  id: number
  title: string
  message: string
  /** When it was first published, as an ISO 8601 time in UTC. */
  publishedAt: string
}

/** One announcement as faculty manage it. Archived announcements are not sent. */
export type LmsFacultyAnnouncement = {
  id: number
  title: string
  message: string
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
  /** The deadline, as an ISO 8601 time in UTC. */
  dueAt: string
  maxMarks: number
  /** When it was first published, as an ISO 8601 time in UTC. */
  publishedAt: string
}

/** One assignment as faculty manage it. Archived assignments are not sent. */
export type LmsFacultyAssignment = {
  id: number
  title: string
  instructions: string
  dueAt: string
  maxMarks: number
  status: LmsContentStatus
  /** When it was first published, or null for a draft. Edits never change it. */
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

/** One assignment in a faculty member's list across their courses, from GET /api/lms/faculty/my-assignments. */
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
}

/** An LMS course after a management change. */
export type LmsCourseManagement = {
  lmsCourseId: number
  courseOfferingId: number
  lmsCourseStatus: LmsCourseLifecycleStatus
  /** How many current students have an active membership now; null when the change did not synchronize them. */
  synchronizedCount: number | null
}
