import type { AdminCourseOffering, AdminSemester, AdminSemesterEnrollment } from '../types/admin'

export const ENROLLMENT_DENIED = 'You don’t have access to academic enrollment administration.'

export function semesterLabel(semester: AdminSemester, withProgram = true): string {
  return `Semester ${semester.semesterNumber} — ${semester.academicSessionCode}${withProgram ? ` · ${semester.programCode}` : ''}`
}

export function semesterContext(record: AdminCourseOffering | AdminSemesterEnrollment): string {
  return `Semester ${record.semesterNumber} · ${record.academicSessionCode}`
}

export function offeringLabel(offering: AdminCourseOffering): string {
  return `${offering.courseCode} — ${offering.courseName}${offering.sectionCode ? ` (section ${offering.sectionCode})` : ''}${
    offering.status === 'CLOSED' ? ' (closed)' : ''
  }`
}

export function refusalText(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
    case 503:
      return apiMessage ?? 'Couldn’t save. Try again.'
    case 403:
      return `${ENROLLMENT_DENIED} If your access changed recently, sign out and sign in again.`
    default:
      return 'Couldn’t save. Check your connection and try again.'
  }
}
