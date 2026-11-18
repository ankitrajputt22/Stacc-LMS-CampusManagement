import { creditsText } from './credits'

type CourseFactsProps = {
  course: {
    programCode: string
    semesterNumber: number
    academicSessionCode: string
    credits: number
    sectionCode?: string | null
  }
}

export function CourseFacts({ course }: CourseFactsProps) {
  return (
    <>
      <p className="mt-2 text-sm leading-6 break-words text-ink-muted">
        <span className="sr-only">Program </span>
        {course.programCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Semester {course.semesterNumber}
        {course.sectionCode !== undefined && (
          <>
            <span aria-hidden="true"> · </span>
            <span className="sr-only">, </span>
            {course.sectionCode === null ? 'Whole semester' : `Section ${course.sectionCode}`}
          </>
        )}
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
