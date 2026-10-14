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
