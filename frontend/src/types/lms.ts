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

/** One published topic, as a student sees it. The content is plain text, never HTML. */
export type LmsCourseTopic = {
  id: number
  title: string
  content: string | null
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
  topics: LmsFacultyTopic[]
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

/** The text of a topic, for adding or editing one. */
export type LmsTopicInput = {
  title: string
  content: string | null
}
