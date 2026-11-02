// Course registration as GET /api/v1/students/me/course-registration sends it. The student is the one in the access
// token; nothing here names a student.

import type { CourseType } from './admin'

export type RegistrationEnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN' | 'DROPPED'

export type EligibleOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  courseType: CourseType
  /** The section it is offered to, or null for the whole semester. */
  sectionCode: string | null
  /** Names of the faculty teaching it, primary first. */
  faculty: string[]
  /** Null means no limit. */
  capacity: number | null
  availableSeats: number | null
  canRegister: boolean
  /** Why it cannot be taken now, in plain words, or null. */
  reason: string | null
}

export type RegisteredCourse = {
  courseEnrollmentId: number
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  sectionCode: string | null
  faculty: string[]
  status: RegistrationEnrollmentStatus
  registeredAt: string
  droppedAt: string | null
  canDrop: boolean
}

export type RegistrationTerm = {
  academicSessionCode: string
  programCode: string
  programName: string
  semesterNumber: number
  sectionCode: string | null
  /** UTC moments, or null when no period is set. */
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  dropDeadline: string | null
  registrationOpen: boolean
  dropOpen: boolean
  /** The program's credit limit per semester, or null for no limit. */
  maxCredits: number | null
  registeredCredits: number
  offerings: EligibleOffering[]
  registered: RegisteredCourse[]
}

export type RegistrationOverview = {
  profileActive: boolean
  terms: RegistrationTerm[]
}

export type RegistrationResult = {
  registered: RegisteredCourse[]
  totalCourses: number
  totalCredits: number
}
