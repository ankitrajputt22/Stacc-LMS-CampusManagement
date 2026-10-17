// Official course results are academic data of a course enrollment. They never involve an LMS course, and nothing
// here is calculated from assignments or attendance.

export type CourseResultStatus = 'DRAFT' | 'PUBLISHED'

export type CourseEnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN'

/** A course offering the faculty member teaches, from GET /api/results/faculty/my-courses. */
export type ResultCourseOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

/** One official result as faculty manage it. Who recorded it is not sent. */
export type FacultyResult = {
  resultId: number
  /** Exact decimals on the backend, such as 82.00 or 12.50. JSON carries them as numbers. */
  marksObtained: number
  maxMarks: number
  /** The official grade code as entered, or null. It is never calculated. */
  gradeCode: string | null
  /** Plain text, or null. */
  remarks: string | null
  status: CourseResultStatus
  /** When it was first published, or null for a draft. Corrections never change it. */
  publishedAt: string | null
  updatedAt: string
}

/** One enrollment of the offering with its result, or null when none has been saved. */
export type CourseResultRosterStudent = {
  /** The enrollment row being managed. The backend checks it against the offering on every change. */
  courseEnrollmentId: number
  /** The student's college ID. There is no name to show. */
  studentLoginId: string
  courseEnrollmentStatus: CourseEnrollmentStatus
  result: FacultyResult | null
}

/** A course offering with every enrollment and its result, in student ID order. */
export type CourseResultRoster = ResultCourseOffering & {
  students: CourseResultRosterStudent[]
}

/** The official result to save. */
export type CourseResultInput = {
  marksObtained: number
  maxMarks: number
  gradeCode: string | null
  remarks: string | null
}

/** One of the signed-in student's own published results. No IDs are sent. */
export type StudentResult = {
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  marksObtained: number
  maxMarks: number
  gradeCode: string | null
  remarks: string | null
  publishedAt: string
}

/** The latest academic session first, then the highest semester number, then course code. */
export type StudentResults = {
  results: StudentResult[]
}
