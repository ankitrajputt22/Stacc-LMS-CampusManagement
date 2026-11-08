import { getCourseSyllabus } from '../api/lmsSyllabusApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { FormattedText } from '../formatting/FormattedText'
import { Card } from '../ui'
import { formatDateTime } from './dates'

/** The course's published syllabus for the signed-in student, or that none is published yet. Drafts never appear. */
export function SyllabusBody({ lmsCourseId, headingLevel = 2 }: { lmsCourseId: number; headingLevel?: 2 | 3 }) {
  const syllabus = useApiQuery(queryKeys.lms.syllabus(lmsCourseId), (signal) => getCourseSyllabus(lmsCourseId, signal))

  if (syllabus.status !== 'loaded') {
    return (
      <StatePanel>
        {syllabus.status === 'loading' && <LoadingState label="Loading syllabus…" />}
        {syllabus.status === 'failed' && (
          <ErrorState title="Couldn’t load the syllabus" message="Check your connection and try again." onRetry={syllabus.retry} />
        )}
        {(syllabus.status === 'denied' || syllabus.status === 'notFound') && (
          <EmptyState title="The syllabus is not available to your account." />
        )}
      </StatePanel>
    )
  }
  if (syllabus.data.content === null) {
    return (
      <StatePanel>
        <EmptyState title="No syllabus published yet." description="It appears here once your teachers publish it." />
      </StatePanel>
    )
  }
  return (
    <Card>
      <FormattedText text={syllabus.data.content} format="FORMATTED" className="text-base leading-7 text-ink" headingLevel={headingLevel} />
      {syllabus.data.publishedAt && (
        <p className="mt-6 border-t border-line pt-3 text-xs text-ink-muted">
          Published <time dateTime={syllabus.data.publishedAt}>{formatDateTime(syllabus.data.publishedAt)}</time>
        </p>
      )}
    </Card>
  )
}
