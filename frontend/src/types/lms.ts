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

/** One LMS course the signed-in faculty member teaches, from GET /api/lms/faculty/my-courses. */
export type LmsFacultyCourseSummary = {
  /** Identifies the LMS learning space. */
  lmsCourseId: number
  courseCode: string
  courseName: string
  /** An exact decimal on the backend, such as 4.00 or 1.50. JSON carries it as a number. */
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  /** DRAFT while it is being prepared and students cannot use it yet; PUBLISHED once they can. */
  lmsCourseStatus: LmsFacultyCourseStatus
}

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
