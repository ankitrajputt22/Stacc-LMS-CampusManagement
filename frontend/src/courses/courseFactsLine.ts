import type { LmsCourseDetail } from '../types/lms'
import { creditsText } from './credits'

const listFormat = new Intl.ListFormat('en', { style: 'long', type: 'conjunction' })

/** A course's official facts in one line: program, semester, section, session, credits, and who teaches it. */
export function courseFactsLine(course: LmsCourseDetail): string {
  return [
    course.programCode,
    `Semester ${course.semesterNumber}`,
    course.sectionCode ? `Section ${course.sectionCode}` : null,
    course.academicSessionCode,
    creditsText(course.credits),
    course.faculty.length > 0 ? listFormat.format(course.faculty) : null,
  ].filter(Boolean).join(' · ')
}

/** The course's code and name with its facts line, for the top of a course section. */
export function courseBanner(course: LmsCourseDetail): { name: string; facts: string } {
  return { name: `${course.courseCode} ${course.courseName}`, facts: courseFactsLine(course) }
}
