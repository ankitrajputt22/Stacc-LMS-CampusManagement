import type { MyAcademicProfile, MyCourseRecord } from '../types/academicProfile'

type StudentPart = NonNullable<MyAcademicProfile['student']>

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

export function currentCourses(student: StudentPart): MyCourseRecord[] {
  return (currentSemester(student)?.courses ?? [])
    .filter((course) => course.status === 'ENROLLED' || course.status === 'COMPLETED')
    .sort((a, b) => a.courseCode.localeCompare(b.courseCode))
}

export function totalCredits(courses: readonly MyCourseRecord[]): string {
  const sum = courses.reduce((total, course) => total + Number(course.credits), 0)
  return formatCredits(sum)
}

export function formatCredits(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}
