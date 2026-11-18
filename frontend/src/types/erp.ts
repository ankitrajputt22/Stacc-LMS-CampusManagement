export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type ClassKind = 'LECTURE' | 'TUTORIAL' | 'LAB'

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
  courseResultId: number
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
  gradePoints: number | null
  passed: boolean
}

export type SemesterStanding = {
  academicSessionCode: string
  programCode: string
  semesterNumber: number
  creditsGraded: number
  creditsEarned: number
  weightedPoints: number
  sgpa: number | null
  cgpaAfter: number | null
  courses: CourseGrade[]
}

export type Standing = {
  semesters: SemesterStanding[]
  cgpa: number | null
  creditsEarned: number
  creditsGraded: number
  weightedPoints: number
}

export type GradeScaleEntry = { code: string; points: number; minPercent: number; passing: boolean }

export type MyExam = {
  slotId: number
  sessionName: string
  courseCode: string
  courseName: string
  teachers: string[]
  examDate: string
  startsAt: string
  endsAt: string
  hall: string
  seatNumber: number | null
}

export type ExamSlot = {
  slotId: number
  courseOfferingId: number
  courseCode: string
  courseName: string
  sectionCode: string | null
  examDate: string
  startsAt: string
  endsAt: string
  hall: string
  hallSeats: number
  seatsGiven: number
  studentsEnrolled: number
}

export type ExamSession = {
  examSessionId: number
  semesterId: number
  name: string
  status: 'DRAFT' | 'PUBLISHED'
  exams: ExamSlot[]
}

export type RequestKind = 'MEDICAL_LEAVE' | 'ATTENDANCE_CORRECTION' | 'REVALUATION'
export type RequestStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type StudentRequest = {
  requestId: number
  reference: string
  kind: RequestKind
  status: RequestStatus
  requesterLoginId: string
  courseCode: string | null
  courseName: string | null
  classHeldAt: string | null
  fromDate: string | null
  toDate: string | null
  marksObtained: number | null
  maxMarks: number | null
  reason: string
  outcome: string | null
  decisionComment: string | null
  createdAt: string
  decidedAt: string | null
}
