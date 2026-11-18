import type { ReactNode } from 'react'

export function CourseSectionHeader({
  course,
  title,
  description,
  breadcrumb,
}: {
  course?: { name: string; facts: string }
  title: string
  description?: ReactNode
  breadcrumb?: ReactNode
}) {
  return (
    <header className="mb-6">
      {course && (
        <>
          <p className="text-2xl font-bold break-words text-portal-navy sm:text-3xl">{course.name}</p>
          <p className="mt-1 break-words text-ink">{course.facts}</p>
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
