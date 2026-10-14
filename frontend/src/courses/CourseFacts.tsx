import { creditsText } from './credits'

type CourseFactsProps = {
  course: {
    programCode: string
    semesterNumber: number
    academicSessionCode: string
    credits: number
  }
}

/**
 * The program, semester, session, and credits lines of a course card, exactly as the backend sent
 * them. Short spoken labels give each value its meaning; the dots are only for the eye.
 */
export function CourseFacts({ course }: CourseFactsProps) {
  return (
    <>
      <p className="mt-2 text-sm leading-6 break-words text-ink-muted">
        <span className="sr-only">Program </span>
        {course.programCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Semester {course.semesterNumber}
      </p>
      <p className="text-sm leading-6 break-words text-ink-muted">
        <span className="sr-only">Academic session </span>
        {course.academicSessionCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {creditsText(course.credits)}
      </p>
    </>
  )
}
