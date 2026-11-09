import type { AiCitation } from '../types/ai'
import { citationKind } from './aiText'

/** The published course sources a result is based on, as plain text. */
export function AiCitations({ citations, headingLevel = 3 }: { citations: AiCitation[]; headingLevel?: 3 | 4 }) {
  if (citations.length === 0) {
    return null
  }
  const Heading = headingLevel === 3 ? 'h3' : 'h4'
  return (
    <div className="mt-3">
      <Heading className="text-sm font-semibold text-ink">Sources</Heading>
      <ul className="mt-1.5 space-y-1.5">
        {citations.map((citation) => (
          <li
            key={`${citation.sourceType}-${citation.sourceId}-${citation.pageNumber ?? ''}-${citation.slideNumber ?? ''}`}
            className="rounded-control border border-line bg-surface-muted px-3 py-2 text-sm leading-5"
          >
            <span className="block font-medium break-words text-ink">
              {citation.fileName ?? citation.title}
            </span>
            <span className="block break-words text-ink-muted">
              {citation.fileName !== null && <>{citation.title} · </>}
              {citationKind(citation)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
