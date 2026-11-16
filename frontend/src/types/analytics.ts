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

// --- Department analytics (DepartmentAnalyticsService): counts for the current session; attendance only for groups of
// at least five (null otherwise). No student is named.

export type DepartmentProgramLine = { code: string; name: string; students: number }
export type DepartmentOfferingLine = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  programCode: string
  semesterNumber: number
  enrolled: number
  attendancePercent: number | null
  publishedResults: number
}
export type DepartmentAnalytics = {
  departmentId: number
  code: string
  name: string
  programs: DepartmentProgramLine[]
  offerings: DepartmentOfferingLine[]
  note: string
}

// --- Area analytics (AreaAnalyticsService): a section is null when the account lacks that area's permission.

export type AreaAnalytics = {
  hostel: { bedsInService: number; bedsUnderMaintenance: number; occupiedBeds: number; occupancyPercent: number | null } | null
  helpdesk: { ticketsByStatus: Record<string, number>; ratedTickets: number; averageRating: number | null } | null
  engagement: { activeGroups: number; activeMemberships: number; pastEvents: number; comingEvents: number; pastRegistrations: number; checkedIn: number; attendancePercent: number | null } | null
  placement: { openOpportunities: number; applicationsByStatus: Record<string, number>; offersByStatus: Record<string, number> } | null
  library: { activeTitles: number; copiesInService: number; openLoans: number; overdueLoans: number; activeHolds: number } | null
  ai: { courseIndexesByStatus: Record<string, number> } | null
  note: string
}
