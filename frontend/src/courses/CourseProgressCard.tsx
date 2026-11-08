import { Link } from 'react-router'
import { getCourseProgress } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Card } from '../ui'
import { textLinkClasses } from '../ui/styles'

/**
 * The student's own progress in the course: required pages completed, and Continue to the next one. Shown only when
 * the course has required pages; worked out by the backend every time, never stored. It is the student's own view, not
 * a grade.
 */
export function CourseProgressCard({ lmsCourseId }: { lmsCourseId: number }) {
  const progress = useApiQuery(queryKeys.lms.progress(lmsCourseId), (signal) => getCourseProgress(lmsCourseId, signal))
  if (progress.status !== 'loaded' || progress.data.requiredPages === 0) {
    return null
  }
  const { requiredPages, completedRequiredPages, next } = progress.data
  const percent = Math.round((completedRequiredPages / requiredPages) * 100)
  return (
    <Card title="Your progress" className="mt-6">
      <p className="text-sm text-ink">
        {completedRequiredPages} of {requiredPages} required {requiredPages === 1 ? 'page' : 'pages'} completed
      </p>
      <div
        role="progressbar"
        aria-label="Required pages completed"
        aria-valuemin={0}
        aria-valuemax={requiredPages}
        aria-valuenow={completedRequiredPages}
        aria-valuetext={`${percent}%`}
        className="mt-2 h-2 overflow-hidden rounded-full bg-surface-muted"
      >
        <div className="h-full rounded-full bg-stacc-primary" style={{ width: `${percent}%` }} />
      </div>
      {next ? (
        <p className="mt-2 text-sm text-ink">
          Continue:{' '}
          <Link to={`/courses/${lmsCourseId}/pages/${next.id}`} className={textLinkClasses}>
            {next.title}
          </Link>{' '}
          <span className="text-ink-muted">({next.moduleTitle})</span>
        </p>
      ) : (
        completedRequiredPages === requiredPages && <p className="mt-2 text-sm text-ink-muted">Every required page is completed.</p>
      )}
    </Card>
  )
}
