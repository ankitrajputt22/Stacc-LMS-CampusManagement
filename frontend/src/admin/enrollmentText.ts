import type { AdminCourseOffering, AdminSemester, AdminSemesterEnrollment } from '../types/admin'

export const ENROLLMENT_DENIED = 'You don’t have access to academic enrollment administration.'

/** "Semester 3 — 2026-27", with the program when the list mixes programs. */
export function semesterLabel(semester: AdminSemester, withProgram = true): string {
  return `Semester ${semester.semesterNumber} — ${semester.academicSessionCode}${withProgram ? ` · ${semester.programCode}` : ''}`
}

/** "Semester 3 · 2026-27" for an offering or a semester enrollment. */
export function semesterContext(record: AdminCourseOffering | AdminSemesterEnrollment): string {
  return `Semester ${record.semesterNumber} · ${record.academicSessionCode}`
}

/** "CS301 — Data Structures" for a select. */
export function offeringLabel(offering: AdminCourseOffering): string {
  return `${offering.courseCode} — ${offering.courseName}${offering.status === 'CLOSED' ? ' (closed)' : ''}`
}

/** A refusal in safe words: the backend's own message for an expected refusal, never a raw error. */
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
