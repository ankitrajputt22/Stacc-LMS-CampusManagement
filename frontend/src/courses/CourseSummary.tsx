import type { LmsCourseSummary } from '../types/lms'

const creditsFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

function creditsText(credits: number): string {
  return `${creditsFormat.format(credits)} ${credits === 1 ? 'credit' : 'credits'}`
}

type CourseSummaryProps = {
  course: LmsCourseSummary
}

/**
 * One course, shown with exactly what the backend sent. It is information only: there is no
 * course page to open yet, so nothing here is a link or a button.
 */
export function CourseSummary({ course }: CourseSummaryProps) {
  return (
    <article className="h-full rounded-panel border border-line bg-surface px-4 py-3.5 sm:px-5 sm:py-4">
      <p className="text-xs font-semibold tracking-[0.04em] break-words text-ink-muted">
        {course.courseCode}
      </p>
      <h2 className="mt-0.5 text-base leading-6 font-semibold break-words text-ink">
        {course.courseName}
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
