import { Link } from 'react-router'
import type { AiCitation } from '../types/ai'
import { Badge } from '../ui'
import { textLinkClasses } from '../ui/styles'
import { citationKind } from './aiText'

/**
 * The sources a result is based on, as cards (v0.13.9): what kind of source, its title and file, the page or slide, and
 * a link to the course page it is on when the caller gives one. Plain text only.
 */
export function AiCitations({ citations, headingLevel = 3, pageLink }: {
  citations: AiCitation[]
  headingLevel?: 3 | 4
  pageLink?: (pageId: number) => string
}) {
  if (citations.length === 0) {
    return null
  }
  const Heading = headingLevel === 3 ? 'h3' : 'h4'
  return (
    <div className="mt-3">
      <Heading className="text-sm font-semibold text-ink">Sources</Heading>
      <ul className="mt-1.5 grid gap-2 sm:grid-cols-2">
        {citations.map((citation) => (
          <li
            key={`${citation.sourceType}-${citation.sourceId}-${citation.pageNumber ?? ''}-${citation.slideNumber ?? ''}`}
            className="rounded-control border border-line bg-surface-muted px-3 py-2 text-sm leading-5"
          >
            <Badge tone={citation.sourceType === 'NOTE' ? 'info' : 'neutral'}>{citationKind(citation)}</Badge>
            <span className="mt-1 block font-medium break-words text-ink">{citation.fileName ?? citation.title}</span>
            {citation.fileName !== null && <span className="block break-words text-ink-muted">{citation.title}</span>}
            {pageLink && citation.pageId != null && (
              <Link to={pageLink(citation.pageId)} className={`mt-1 inline-block ${textLinkClasses}`}>
                Open page<span className="sr-only"> for {citation.fileName ?? citation.title}</span>
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
