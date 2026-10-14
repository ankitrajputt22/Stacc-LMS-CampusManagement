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
