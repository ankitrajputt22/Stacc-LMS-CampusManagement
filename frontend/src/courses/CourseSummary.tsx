import { Link } from 'react-router'
import { courseColor } from '../dashboard/courseColors'
import type { LmsCourseSummary } from '../types/lms'
import { CourseFacts } from './CourseFacts'

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
    <article
      style={{ borderTopColor: courseColor(course.lmsCourseId) }}
      className="group relative h-full rounded-panel border border-t-4 border-line bg-surface px-4 py-3.5 hover:border-slate-400 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary sm:px-5 sm:py-4"
    >
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {course.courseCode}
      </p>
      <h3 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {/* The course route uses the LMS course ID, not the course or offering ID. */}
        <Link
          to={`/courses/${course.lmsCourseId}`}
          className="after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:text-stacc-primary group-hover:underline group-hover:underline-offset-4"
        >
          {course.courseName}
        </Link>
      </h3>
      <CourseFacts course={course} />
      {course.faculty.length > 0 && (
        <p className="text-sm leading-6 break-words text-ink-muted">
          <span className="sr-only">Taught by </span>
          {course.faculty.join(', ')}
        </p>
      )}
    </article>
  )
}
