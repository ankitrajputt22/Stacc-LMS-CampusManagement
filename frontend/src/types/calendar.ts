// The shared calendar and the admin calendar events, exactly as the backend sends them. Official events are plain
// dates with no time zone; assignment deadlines are absolute moments in UTC.

export type CalendarEventCategory = 'ACADEMIC' | 'HOLIDAY' | 'EXAM' | 'REGISTRATION' | 'OTHER'
export type CalendarEventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'

/** An official event on someone's calendar. Drafts are never sent; a cancelled one was published first. */
export type AcademicCalendarEventSummary = {
  eventId: number
  title: string
  /** Plain text, or null when none was entered. */
  description: string | null
  category: CalendarEventCategory
  /** Calendar dates such as 2026-10-20. The end date is included. */
  startDate: string
  endDate: string
  status: Exclude<CalendarEventStatus, 'DRAFT'>
  academicSessionCode: string
  /** Null when the event applies across the whole session. */
  semesterNumber: number | null
  programCode: string | null
}

export type StudentAssignmentDeadline = {
  assignmentId: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
  /** An absolute moment, such as 2026-10-20T18:29:00Z. */
  dueAt: string
  maxMarks: number
}

export type FacultyAssignmentDeadline = StudentAssignmentDeadline & {
  status: 'DRAFT' | 'PUBLISHED'
}

export type MyCalendarResponse = {
  fromDate: string
  toDate: string
  timeZone: string
  academicEvents: AcademicCalendarEventSummary[]
  /** Empty unless the account holds STUDENT. */
  studentAssignmentDeadlines: StudentAssignmentDeadline[]
  /** Empty unless the account holds FACULTY. */
  facultyAssignmentDeadlines: FacultyAssignmentDeadline[]
}

/** An official event as admins manage it, in any status. */
export type AdminCalendarEvent = {
  eventId: number
  academicSessionId: number
  academicSessionCode: string
  semesterId: number | null
  semesterNumber: number | null
  programCode: string | null
  title: string
  description: string | null
  category: CalendarEventCategory
  startDate: string
  endDate: string
  status: CalendarEventStatus
  publishedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

/** What an admin sends to create or edit an event. There is never a status: drafts change only by publishing. */
export type CalendarEventRequest = {
  academicSessionId: number
  semesterId: number | null
  title: string
  description: string | null
  category: CalendarEventCategory
  startDate: string
  endDate: string
}

export type CalendarEventFilters = {
  from?: string
  to?: string
  academicSessionId?: number
  semesterId?: number
  status?: CalendarEventStatus
}
