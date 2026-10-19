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
