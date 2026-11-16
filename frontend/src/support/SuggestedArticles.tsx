import { getHelpArticles } from '../api/helpdeskApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/**
 * Help articles for a ticket being written: the category's published articles, those sharing words with the subject
 * first. Loaded when the category changes and when the subject field is left, never per keystroke. Shows nothing when
 * there are none, so the form never waits on it.
 */
export function SuggestedArticles({ categoryId, subject, className = '' }: { categoryId: number | null; subject: string; className?: string }) {
  const articles = useApiQuery(categoryId === null ? null : queryKeys.helpdesk.helpArticles(categoryId, subject), (signal) =>
    getHelpArticles(categoryId ?? 0, subject, signal),
  )
  if (articles.status !== 'loaded' || articles.data.length === 0) {
    return null
  }
  return (
    <section aria-labelledby="help-articles-heading" className={`rounded-control border border-line bg-surface-muted px-3 py-3 ${className}`}>
      <h3 id="help-articles-heading" className="text-sm font-semibold text-ink">
        These articles might solve it
      </h3>
      <ul className="mt-2 space-y-1.5">
        {articles.data.map((article) => (
          <li key={article.articleId}>
            <details>
              <summary className="cursor-pointer text-sm font-medium text-stacc-primary">{article.title}</summary>
              <p className="mt-1.5 text-sm leading-6 whitespace-pre-line text-ink">{article.body}</p>
            </details>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-ink-muted">If none of them helps, carry on and create the ticket.</p>
    </section>
  )
}
