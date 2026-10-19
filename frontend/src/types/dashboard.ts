import type { LmsCourseLifecycleStatus } from './lms'

// The student dashboard as GET /api/dashboard/student sends it: short previews with their full counts. Every value
// comes from the feature that owns it; nothing is derived here.

export type DashboardCourse = {
  lmsCourseId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

export type DashboardAssignment = {
  assignmentId: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  /** An absolute moment, such as 2026-10-30T18:29:00Z. */
  dueAt: string
  maxMarks: number
}

export type DashboardAnnouncement = {
  announcementId: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  publishedAt: string
}

export type DashboardAttendanceCourse = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  totalSessions: number
  presentCount: number
  absentCount: number
  /** As the attendance page sends it; null only when nothing was finalized. */
  attendancePercentage: number | null
}

export type DashboardResult = {
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  marksObtained: number
  maxMarks: number
  /** Null when no grade was entered. */
  gradeCode: string | null
  publishedAt: string
}

export type StudentDashboard = {
  courseCount: number
  courses: DashboardCourse[]
  upcomingAssignmentCount: number
  upcomingAssignments: DashboardAssignment[]
  recentAnnouncements: DashboardAnnouncement[]
  attendanceCourseCount: number
  attendance: DashboardAttendanceCourse[]
  resultCount: number
  recentResults: DashboardResult[]
}

// The faculty dashboard as GET /api/dashboard/faculty sends it.

export type FacultyDashboardCourse = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  /** Both null when the offering has no LMS course yet. */
  lmsCourseId: number | null
  lmsCourseStatus: LmsCourseLifecycleStatus | null
}

export type FacultyDashboardAssignment = {
  assignmentId: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  dueAt: string
  maxMarks: number
  status: 'DRAFT' | 'PUBLISHED'
}

/** A submission whose latest attempt has no grade yet. */
export type FacultyDashboardPendingSubmission = {
  submissionId: number
  assignmentId: number
  lmsCourseId: number
  studentLoginId: string
  courseCode: string
  courseName: string
  assignmentTitle: string
  latestAttemptId: number
  latestAttemptNumber: number
  latestSubmittedAt: string
  latestLate: boolean
}

export type FacultyDashboardOpenSession = {
  attendanceSessionId: number
  courseOfferingId: number
  courseCode: string
  courseName: string
  heldAt: string
  topic: string | null
  totalRecords: number
  unmarkedCount: number
}

export type FacultyDashboard = {
  teachingCourseCount: number
  teachingCourses: FacultyDashboardCourse[]
  upcomingAssignmentCount: number
  upcomingAssignments: FacultyDashboardAssignment[]
  needsGradingCount: number
  needsGrading: FacultyDashboardPendingSubmission[]
  openAttendanceCount: number
  openAttendanceSessions: FacultyDashboardOpenSession[]
}

// The admin dashboard as GET /api/dashboard/admin sends it: exact counts and a short offering preview.

/** Every record of each master, whatever its status. */
export type AdminAcademicMasterSummary = {
  departmentCount: number
  programCount: number
  academicSessionCount: number
  semesterCount: number
  courseCount: number
}

export type AdminAcademicOperationsSummary = {
  courseOfferingCount: number
  /** ENROLLED only; completed and withdrawn enrollments are not counted. */
  activeSemesterEnrollmentCount: number
  activeCourseEnrollmentCount: number
}

export type AdminDashboardCourseOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

export type AdminDashboard = {
  academicMasters: AdminAcademicMasterSummary
  academicOperations: AdminAcademicOperationsSummary
  /** The first offerings in the admin list's order. */
  courseOfferings: AdminDashboardCourseOffering[]
}
