import type { EnrollmentStatus } from './admin'

export type TaughtOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  academicSessionCode: string
  programCode: string
  semesterNumber: number
  sectionCode: string | null
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
