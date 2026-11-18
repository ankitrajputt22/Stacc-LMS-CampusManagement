export type CourseResultStatus = 'DRAFT' | 'PUBLISHED'

export type CourseEnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN' | 'DROPPED'

export type ResultCourseOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

export type FacultyResult = {
  resultId: number
  marksObtained: number
  maxMarks: number
  gradeCode: string | null
  remarks: string | null
  status: CourseResultStatus
  publishedAt: string | null
  updatedAt: string
  internalMarks: number | null
  internalMax: number | null
  externalMarks: number | null
  externalMax: number | null
}

export type CourseResultRosterStudent = {
  courseEnrollmentId: number
  studentLoginId: string
  courseEnrollmentStatus: CourseEnrollmentStatus
  result: FacultyResult | null
}

export type CourseResultRoster = ResultCourseOffering & {
  students: CourseResultRosterStudent[]
}

export type CourseResultInput = {
  marksObtained: number
  maxMarks: number
  gradeCode: string | null
  remarks: string | null
  internalMarks?: number | null
  internalMax?: number | null
  externalMarks?: number | null
  externalMax?: number | null
}

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

export type StudentResults = {
  results: StudentResult[]
}
