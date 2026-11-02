// Faculty class lists as /api/v1/faculty/me/course-offerings sends them. The faculty member is the one in the token.

import type { EnrollmentStatus } from './admin'

export type TaughtOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  academicSessionCode: string
  programCode: string
  semesterNumber: number
  /** The section it is offered to, or null for the whole semester. */
  sectionCode: string | null
  /** Null means no limit. */
  capacity: number | null
  enrolledCount: number
}

export type ClassStudent = {
  courseEnrollmentId: number
  loginId: string
  name: string | null
  rollNumber: string | null
  programCode: string
  semesterNumber: number
  sectionCode: string | null
  status: EnrollmentStatus
  registeredAt: string
}

export type ClassList = { offering: TaughtOffering; students: ClassStudent[] }
