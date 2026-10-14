import { CourseFacts } from '../courses/CourseFacts'
import type { LmsFacultyCourseStatus, LmsFacultyCourseSummary } from '../types/lms'

const STATUS_LABELS: Record<LmsFacultyCourseStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
}

type TeachingCourseSummaryProps = {
  course: LmsFacultyCourseSummary
}

/**
 * One course the faculty member teaches, shown with exactly what the backend sent. It is
 * information only: there is no faculty course page yet, so nothing here is a link or a button.
 */
export function TeachingCourseSummary({ course }: TeachingCourseSummaryProps) {
  return (
    <article className="h-full rounded-panel border border-line bg-surface px-4 py-3.5 sm:px-5 sm:py-4">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {course.courseCode}
      </p>
      <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {course.courseName}
      </h2>
      <CourseFacts course={course} />
      {/* The status is a word, never only a colour. */}
      <p className="mt-2 text-xs text-ink-muted">
        <span className="sr-only">LMS status: </span>
        <span className="inline-block rounded-sm border border-line-strong px-1.5 py-0.5 font-medium text-ink">
          {STATUS_LABELS[course.lmsCourseStatus] ?? course.lmsCourseStatus}
        </span>
      </p>
    </article>
  )
}
