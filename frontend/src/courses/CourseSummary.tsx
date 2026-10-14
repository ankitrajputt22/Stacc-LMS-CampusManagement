import { Link } from 'react-router'
import type { LmsCourseSummary } from '../types/lms'
import { creditsText } from './credits'

type CourseSummaryProps = {
  course: LmsCourseSummary
}

/**
 * One course in the list, shown with exactly what the backend sent. The course name is a real
 * link to the course page, and its click area is stretched over the whole card, so screen
 * readers hear one clear link while the whole card can be clicked.
 */
export function CourseSummary({ course }: CourseSummaryProps) {
  return (
    <article className="group relative h-full rounded-panel border border-line bg-surface px-4 py-3.5 hover:border-slate-400 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5 sm:py-4">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {course.courseCode}
      </p>
      <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {/* The course route uses the LMS course ID, not the course or offering ID. */}
        <Link
          to={`/courses/${course.lmsCourseId}`}
          className="after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
        >
          {course.courseName}
        </Link>
      </h2>
      {/* Short spoken labels give each value its meaning; the dots are only for the eye. */}
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
    </article>
  )
}
