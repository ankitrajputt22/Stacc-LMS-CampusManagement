import { Link } from 'react-router'
import { CourseFacts } from '../courses/CourseFacts'
import type { LmsFacultyCourseSummary } from '../types/lms'
import { StatusTag } from './StatusTag'

type TeachingCourseSummaryProps = {
  course: LmsFacultyCourseSummary
}

/**
 * One course the faculty member teaches, shown with exactly what the backend sent. The course name
 * is a real link to the course's content page, and its click area is stretched over the whole card,
 * so screen readers hear one clear link while the whole card can be clicked.
 */
export function TeachingCourseSummary({ course }: TeachingCourseSummaryProps) {
  return (
    <article className="group relative h-full rounded-panel border border-line bg-surface px-4 py-3.5 hover:border-slate-400 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5 sm:py-4">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {course.courseCode}
      </p>
      <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {/* The route uses the LMS course ID, never a course, offering, or teaching assignment ID. */}
        <Link
          to={`/teaching/${course.lmsCourseId}`}
          className="after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
        >
          {course.courseName}
        </Link>
      </h2>
      <CourseFacts course={course} />
      <p className="mt-2 text-xs text-ink-muted">
        <StatusTag status={course.lmsCourseStatus} label="LMS status" />
      </p>
    </article>
  )
}
