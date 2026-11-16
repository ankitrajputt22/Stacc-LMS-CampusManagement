import type { ReactNode } from 'react'
import type { LmsCourseDetail } from '../types/lms'
import { courseFactsLine } from './courseFactsLine'

/**
 * The top of a student course section: the course and its facts (once loaded), an optional breadcrumb, then the
 * section's own title with the teal bar, which is the page's one h1.
 */
export function CourseSectionHeader({
  course,
  title,
  description,
  breadcrumb,
}: {
  course?: LmsCourseDetail
  title: string
  description?: ReactNode
  breadcrumb?: ReactNode
}) {
  return (
    <header className="mb-6">
      {course && (
        <>
          <p className="text-2xl font-bold break-words text-portal-navy sm:text-3xl">{`${course.courseCode} ${course.courseName}`}</p>
          <p className="mt-1 break-words text-ink">{courseFactsLine(course)}</p>
        </>
      )}
      {breadcrumb && <div className="mt-4">{breadcrumb}</div>}
      <h1 className="mt-4 flex items-center gap-3 text-3xl font-bold tracking-tight break-words text-ink">
        <span aria-hidden="true" className="h-9 w-1.5 shrink-0 rounded bg-teal-600" />
        {title}
      </h1>
      {description && <p className="mt-2 text-[0.95rem] text-ink-muted">{description}</p>}
    </header>
  )
}
