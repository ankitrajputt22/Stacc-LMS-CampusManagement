import type { LmsCourseLifecycleStatus } from './lms'

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

export type FacultyDashboardCourse = {
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

export type AdminAcademicMasterSummary = {
  departmentCount: number
  programCount: number
  academicSessionCount: number
  semesterCount: number
  courseCount: number
}

export type AdminAcademicOperationsSummary = {
  courseOfferingCount: number
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
  courseOfferings: AdminDashboardCourseOffering[]
}
