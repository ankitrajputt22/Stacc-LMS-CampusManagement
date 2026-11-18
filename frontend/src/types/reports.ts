export type LmsCourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type FacultyReportCourseSummary = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  lmsCourseId: number | null
  lmsCourseStatus: LmsCourseStatus | null
  activeCourseEnrollmentCount: number
  assignmentCount: number | null
  finalizedAttendanceSessionCount: number
  publishedResultCount: number
}

export type FacultyCourseReport = {
  course: {
    courseOfferingId: number
    courseCode: string
    courseName: string
    credits: number
    semesterNumber: number
    programCode: string
    programName: string
    academicSessionCode: string
    courseOfferingStatus: 'PLANNED' | 'ACTIVE' | 'CLOSED'
  }
  enrollment: { enrolledCount: number; completedCount: number; withdrawnCount: number }
  assignments: {
    available: boolean
    lmsCourseId: number | null
    lmsCourseStatus: LmsCourseStatus | null
    draftAssignmentCount: number
    publishedAssignmentCount: number
    submissionCount: number
    needsGradingCount: number
  }
  attendance: {
    openAttendanceSessionCount: number
    finalizedAttendanceSessionCount: number
    presentRecordCount: number
    absentRecordCount: number
  }
  results: { draftResultCount: number; publishedResultCount: number }
}

export type AdminReportFilters = { academicSessionId: number | null; programId: number | null; semesterId: number | null }

export type EnrollmentStatusCounts = { enrolledCount: number; completedCount: number; withdrawnCount: number }

export type AdminReportOverview = {
  filters: AdminReportFilters
  academic: { departmentCount: number; programCount: number; courseCount: number; courseOfferingCount: number }
  enrollment: { semesterEnrollments: EnrollmentStatusCounts; courseEnrollments: EnrollmentStatusCounts }
  operations: {
    helpdesk: { openTicketCount: number; inProgressTicketCount: number; resolvedTicketCount: number; closedTicketCount: number } | null
    hostel: { activeAllocationCount: number; vacatedAllocationCount: number } | null
    campusServices: {
      submittedCount: number
      approvedCount: number
      rejectedCount: number
      cancelledCount: number
      completedCount: number
    } | null
  }
}

export type AdminCourseEnrollmentReportRow = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  programCode: string
  semesterNumber: number
  academicSessionCode: string
  enrolledCount: number
  completedCount: number
  withdrawnCount: number
}

export type AdminCourseEnrollmentReportPage = {
  items: AdminCourseEnrollmentReportRow[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ReportFilterQuery = { academicSessionId?: number; programId?: number; semesterId?: number }
