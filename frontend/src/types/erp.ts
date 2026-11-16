// The ERP section's records as the API sends them (v0.16). Every figure is worked out by the backend.

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type ClassKind = 'LECTURE' | 'TUTORIAL' | 'LAB'

/** One weekly class; times are "HH:mm:ss" in the college's time zone. */
export type TimetableSlot = {
  slotId: number
  courseOfferingId: number
  courseCode: string
  courseName: string
  sectionCode: string | null
  day: DayOfWeek
  startsAt: string
  endsAt: string
  room: string
  kind: ClassKind
}

/** A part is null when the account does not hold that role. */
export type MyTimetable = { studying: TimetableSlot[] | null; teaching: TimetableSlot[] | null }

export type CourseType = 'CORE' | 'ELECTIVE' | 'LAB' | 'PROJECT' | 'AUDIT'

export type DegreeProgress = {
  loginId: string
  rollNumber: string | null
  programCode: string
  programName: string
  departmentName: string
  admissionSessionCode: string
  current: { academicSessionCode: string; semesterNumber: number; sectionCode: string | null } | null
  expectedGraduationYear: number | null
  requiredCredits: number
  earnedCredits: number
  /** Null when the program has no requirements recorded. */
  percent: number | null
  groups: {
    courseType: CourseType
    requiredCredits: number
    earnedCredits: number
    percent: number
    status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETE'
    courses: { courseCode: string; courseName: string; credits: number; grade: string | null }[]
  }[]
}

export type CourseGrade = {
  courseCode: string
  courseName: string
  courseType: CourseType
  credits: number
  internalMarks: number | null
  internalMax: number | null
  externalMarks: number | null
  externalMax: number | null
  marksObtained: number
  maxMarks: number
  grade: string | null
  /** Null when the grade is not on the college's scale. */
  gradePoints: number | null
  passed: boolean
}

export type SemesterStanding = {
  academicSessionCode: string
  programCode: string
  semesterNumber: number
  creditsGraded: number
  creditsEarned: number
  sgpa: number | null
  courses: CourseGrade[]
}

/** Semesters latest first; the CGPA is null until some graded credit exists. */
export type Standing = { semesters: SemesterStanding[]; cgpa: number | null; creditsEarned: number }
