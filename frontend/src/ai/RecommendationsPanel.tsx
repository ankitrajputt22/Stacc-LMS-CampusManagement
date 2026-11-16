import { useId } from 'react'
import { Link } from 'react-router'
import { getRecommendations } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { textLinkClasses } from '../ui'
import { hintClasses, panelClasses } from './aiStyles'

/**
 * What to do next (v0.15.6): up to six suggestions from fixed rules over the student's own mastery, flashcards, and the
 * course's assignments, each saying why. No AI is used; pages and assignments open through their own access checks.
 */
export function RecommendationsPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const recommendations = useApiQuery(queryKeys.lms.recommendations(lmsCourseId), (signal) => getRecommendations(lmsCourseId, signal))
  if (recommendations.status !== 'loaded' || recommendations.data.length === 0) {
    return null
  }
  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">What to do next</h2>
      <p className={hintClasses}>Suggestions from your own practice, flashcards, and deadlines, each with its reason.</p>
      <ul className="mt-3 space-y-2 text-sm">
        {recommendations.data.map((item) => (
          <li key={`${item.kind}-${item.targetId}`}>
            {item.targetId !== null && (item.kind === 'PRACTISE_WEAK' || item.kind === 'START_PAGE' || item.kind === 'REFRESH_PAGE') ? (
              <Link to={`/courses/${lmsCourseId}/pages/${item.targetId}`} className={textLinkClasses}>{item.title}</Link>
            ) : item.kind === 'ASSIGNMENT_SOON' && item.targetId !== null ? (
              <Link to={`/courses/${lmsCourseId}/assignments/${item.targetId}`} className={textLinkClasses}>{item.title}</Link>
            ) : (
              <span className="font-medium text-ink">{item.title}</span>
            )}
            <span className="block text-ink-muted">{item.reason}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
