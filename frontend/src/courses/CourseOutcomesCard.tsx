import { getCourseOutcomes } from '../api/lmsOutcomesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Card } from '../ui'

/** The course's active learning outcomes, by code, for students. Nothing is shown while there are none. */
export function CourseOutcomesCard({ lmsCourseId }: { lmsCourseId: number }) {
  const outcomes = useApiQuery(queryKeys.lms.outcomes(lmsCourseId), (signal) => getCourseOutcomes(lmsCourseId, signal))
  if (outcomes.status !== 'loaded' || outcomes.data.length === 0) {
    return null
  }
  return (
    <Card title="Course outcomes" description="What you should be able to do by the end of the course." className="mt-6">
      <ul className="space-y-1.5">
        {outcomes.data.map((outcome) => (
          <li key={outcome.id} className="text-sm break-words text-ink">
            <span className="font-semibold">{outcome.code}</span> {outcome.description}
          </li>
        ))}
      </ul>
    </Card>
  )
}
