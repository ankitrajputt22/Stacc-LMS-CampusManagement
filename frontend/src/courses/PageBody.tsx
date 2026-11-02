import { Link } from 'react-router'
import { FormattedText } from '../formatting/FormattedText'
import type { TextFormat } from '../formatting/textFormat'
import type { LmsCourseMaterial, LmsPageLink } from '../types/lms'
import { StudyMaterialList } from './StudyMaterialList'
import { useMaterialDownload } from './useMaterialDownload'

type PageBodyProps = {
  content: string | null
  contentFormat: TextFormat
  materials: LmsCourseMaterial[]
  previous: LmsPageLink | null
  next: LmsPageLink | null
  /** Where a neighbouring page opens: the student page or the faculty preview. */
  pathTo: (pageId: number) => string
}

const neighbourClasses =
  'flex min-h-11 flex-col justify-center rounded-control border border-line bg-surface px-4 py-2 text-sm hover:border-line-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

/**
 * The body of a course page, the same for students and for the faculty preview: its text as its format says, its
 * study materials, and links to the pages before and after it in course order. Only what the API sent.
 */
export function PageBody({ content, contentFormat, materials, previous, next, pathTo }: PageBodyProps) {
  const downloads = useMaterialDownload()

  return (
    <>
      <div className="mt-6 max-w-3xl rounded-panel border border-line bg-surface px-4 py-4 sm:px-6">
        {content ? (
          <FormattedText text={content} format={contentFormat} className="text-base leading-7 text-ink" headingLevel={2} />
        ) : (
          <p className="text-sm text-ink-muted">This page has no text.</p>
        )}
      </div>

      {materials.length > 0 && (
        <section aria-labelledby="page-materials-heading" className="mt-6 max-w-3xl">
          <h2 id="page-materials-heading" className="text-base font-semibold text-ink">
            Study materials
          </h2>
          <StudyMaterialList materials={materials} downloads={downloads} />
        </section>
      )}

      {(previous || next) && (
        <nav aria-label="Pages of this course" className="mt-8 grid max-w-3xl gap-3 sm:grid-cols-2">
          {previous ? (
            <Link to={pathTo(previous.id)} className={neighbourClasses}>
              <span className="text-xs text-ink-muted">Previous: {previous.moduleTitle}</span>
              <span className="font-medium break-words text-stacc-primary">{previous.title}</span>
            </Link>
          ) : (
            <span aria-hidden="true" />
          )}
          {next && (
            <Link to={pathTo(next.id)} className={`${neighbourClasses} sm:text-right`}>
              <span className="text-xs text-ink-muted">Next: {next.moduleTitle}</span>
              <span className="font-medium break-words text-stacc-primary">{next.title}</span>
            </Link>
          )}
        </nav>
      )}
    </>
  )
}
