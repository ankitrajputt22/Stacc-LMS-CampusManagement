import { Link } from 'react-router'
import { FormattedText } from '../formatting/FormattedText'
import type { TextFormat } from '../formatting/textFormat'
import type { LmsCourseMaterial, LmsPageLink } from '../types/lms'
import { StudyMaterialList } from './StudyMaterialList'
import { Card } from '../ui'
import { useMaterialDownload } from './useMaterialDownload'

type PageBodyProps = {
  content: string | null
  contentFormat: TextFormat
  materials: LmsCourseMaterial[]
  previous: LmsPageLink | null
  next: LmsPageLink | null
  /** Where a neighbouring page opens: the student page or the faculty preview. */
  pathTo: (pageId: number) => string
  /** Only the links to the pages before and after, such as below a locked page or after the completion box. */
  neighboursOnly?: boolean
}

const neighbourClasses =
  'flex min-h-11 flex-col justify-center rounded-control border border-line bg-surface px-4 py-2 text-sm hover:border-line-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

/**
 * The body of a course page, the same for students and for the faculty preview: its text as its format says, its
 * study materials, and links to the pages before and after it in course order. Only what the API sent.
 */
export function PageBody({ content, contentFormat, materials, previous, next, pathTo, neighboursOnly = false }: PageBodyProps) {
  const downloads = useMaterialDownload()

  return (
    <>
      {!neighboursOnly && (
        <Card>
          {content ? (
            <FormattedText text={content} format={contentFormat} className="text-base leading-7 text-ink" headingLevel={2} />
          ) : (
            <p className="text-sm text-ink-muted">This page has no text.</p>
          )}
        </Card>
      )}

      {!neighboursOnly && materials.length > 0 && (
        <Card title="Study materials" className="mt-6">
          <StudyMaterialList materials={materials} downloads={downloads} />
        </Card>
      )}

      {(previous || next) && (
        <nav aria-label="Pages of this course" className="mt-8 grid gap-3 sm:grid-cols-2">
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
