export type CalendarEventCategory = 'ACADEMIC' | 'HOLIDAY' | 'EXAM' | 'REGISTRATION' | 'OTHER'
export type CalendarEventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'

export type AcademicCalendarEventSummary = {
  eventId: number
  title: string
  description: string | null
  category: CalendarEventCategory
  startDate: string
  endDate: string
  status: Exclude<CalendarEventStatus, 'DRAFT'>
  academicSessionCode: string
  semesterNumber: number | null
  programCode: string | null
}

export type StudentAssignmentDeadline = {
  assignmentId: number
  lmsCourseId: number
  courseCode: string
  courseName: string
  title: string
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
  studentAssignmentDeadlines: StudentAssignmentDeadline[]
  facultyAssignmentDeadlines: FacultyAssignmentDeadline[]
}

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

export type PersonalCalendarEvent = {
  eventId: number
  title: string
  notes: string | null
  startDate: string
  endDate: string
}

export type PersonalCalendarEventRequest = {
  title: string
  notes: string | null
  startDate: string
  endDate: string
}

export type CalendarFeedStatus = { active: boolean; createdAt: string | null }

export type NewCalendarFeed = { feedPath: string; createdAt: string }
