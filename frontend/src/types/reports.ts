// Reports exactly as the backend sends them (FacultyReports and AdminReports in com.stacc.backend.report). Every value
// is a count from the source data; nothing is averaged, ranked, or scored.

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
  /** Course enrollments in ENROLLED status. */
  activeCourseEnrollmentCount: number
  /** Draft and published assignments; null when there is no LMS course faculty can open. */
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
    /** False when the offering has no draft or published LMS course; the counts are then 0. */
    available: boolean
    lmsCourseId: number | null
    lmsCourseStatus: LmsCourseStatus | null
    draftAssignmentCount: number
    publishedAssignmentCount: number
    /** One per student and published assignment with at least one attempt. */
    submissionCount: number
    /** Submissions whose latest attempt has no grade. */
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
  /** Each section is null when the admin lacks that module's own permission. */
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

/** The filters as the page holds them; an empty one is left out of the request. */
export type ReportFilterQuery = { academicSessionId?: number; programId?: number; semesterId?: number }
