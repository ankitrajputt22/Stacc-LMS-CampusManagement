import type { CourseType } from './admin'

export type RegistrationEnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN' | 'DROPPED'

export type EligibleOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  courseType: CourseType
  sectionCode: string | null
  faculty: string[]
  capacity: number | null
  availableSeats: number | null
  canRegister: boolean
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
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  dropDeadline: string | null
  registrationOpen: boolean
  dropOpen: boolean
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
