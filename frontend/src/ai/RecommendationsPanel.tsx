import { useId } from 'react'
import { Link } from 'react-router'
import { getRecommendations } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { primaryLinkClasses } from '../components/actionStyles'
import { recommendationLink } from './recommendationLink'
import { hintClasses, panelClasses } from './aiStyles'

export function RecommendationsPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const recommendations = useApiQuery(queryKeys.lms.recommendations(lmsCourseId), (signal) => getRecommendations(lmsCourseId, signal))
  if (recommendations.status !== 'loaded' || recommendations.data.length === 0) {
    return null
  }
  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-2xl font-bold text-ink">What to do next</h2>
      <p className={hintClasses}>Suggestions from your own practice, flashcards, and deadlines, each with its reason.</p>
      <ul className="mt-3 divide-y divide-line">
        {recommendations.data.map((item) => {
          const to = recommendationLink(lmsCourseId, item)
          return (
            <li key={`${item.kind}-${item.targetId}`} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p aria-hidden={to ? true : undefined} className="text-lg font-bold break-words text-ink">{item.title}</p>
                <p className="text-[0.95rem] text-ink-muted">Reason: <span>{item.reason}</span></p>
              </div>
              {to && (
                <Link to={to} className={`${primaryLinkClasses} shrink-0 self-start sm:self-center`}>
                  {item.title}
                </Link>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
