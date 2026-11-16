import type { MyAcademicProfile, MyCourseRecord } from '../types/academicProfile'

type StudentPart = NonNullable<MyAcademicProfile['student']>

/** The semester the student is enrolled in now: the one the backend names as current. */
export function currentSemester(student: StudentPart) {
  const current = student.current
  if (!current) {
    return null
  }
  return (
    student.semesters.find(
      (semester) =>
        semester.status === 'ENROLLED' &&
        semester.academicSessionCode === current.academicSessionCode &&
        semester.semesterNumber === current.semesterNumber,
    ) ?? null
  )
}

/** The courses the student is enrolled in now, by course code. Dropped and withdrawn ones are left out. */
export function currentCourses(student: StudentPart): MyCourseRecord[] {
  return (currentSemester(student)?.courses ?? [])
    .filter((course) => course.status === 'ENROLLED' || course.status === 'COMPLETED')
    .sort((a, b) => a.courseCode.localeCompare(b.courseCode))
}

/** The sum of the courses' credits, written without needless decimals ("16", "16.5"). */
export function totalCredits(courses: readonly MyCourseRecord[]): string {
  const sum = courses.reduce((total, course) => total + Number(course.credits), 0)
  return formatCredits(sum)
}

export function formatCredits(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}
