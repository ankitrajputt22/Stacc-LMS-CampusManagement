/** Percentages come from the backend; null means none to show (nothing yet, or a group under five). */
export type StudentAnalytics = {
  overallAttendancePercent: number | null
  attendance: { courseCode: string; sessions: number; present: number; presentPercent: number | null }[]
  courses: {
    lmsCourseId: number
    courseCode: string
    courseName: string
    assignmentsDue: number
    submitted: number
    late: number
    averageMarkPercent: number | null
  }[]
  assignmentAveragePercent: number | null
  resultAveragePercent: number | null
  publishedResults: number
}

export type CourseAnalytics = {
  lmsCourseId: number
  enrolled: number
  smallGroup: boolean
  note: string | null
  attendancePercent: number | null
  sessions: { sessionId: number; present: number; total: number; presentPercent: number | null }[]
  assignments: {
    assignmentId: number
    title: string
    dueAt: string
    submitted: number
    submittedPercent: number | null
    late: number
    graded: number
    averagePercent: number | null
  }[]
  quizzes: {
    quizId: number
    title: string
    handedIn: number
    averagePercent: number | null
    questions: { position: number; question: string; answered: number; correctPercent: number | null }[]
  }[]
  requiredPages: { pageId: number; title: string; completed: number; completedPercent: number | null }[]
}

export type ExportKind = 'ADMIN_ENROLLMENTS' | 'COURSE_ANALYTICS'

export type ReportExport = {
  exportId: number
  kind: ExportKind
  parameter: number | null
  status: 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED' | 'EXPIRED'
  fileName: string | null
  sizeBytes: number | null
  failure: string | null
  createdAt: string
  expiresAt: string
}
