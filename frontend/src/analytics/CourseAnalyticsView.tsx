import { getCourseAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { Alert, Card } from '../ui'
import { ExportsPanel } from './ExportsPanel'
import { Percent } from './Percent'

const SMALL = 'Shown for groups of at least five students'

/** Class analytics as the backend works them out: no student is listed or ranked, and small groups show counts only. */
export function CourseAnalyticsView({ lmsCourseId }: { lmsCourseId: number }) {
  const analytics = useApiQuery(queryKeys.analytics.course(lmsCourseId), (signal) => getCourseAnalytics(lmsCourseId, signal))
  if (analytics.status !== 'loaded') {
    return <Card title="Course analytics"><SectionState request={analytics} what="analytics" /></Card>
  }
  const a = analytics.data
  return (
    <div className="space-y-6">
      {a.note && <Alert tone="info">{a.note} This course has {a.enrolled} enrolled, so only counts are shown.</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Card title="Enrolled"><p className="text-3xl font-bold text-portal-navy">{a.enrolled}</p></Card>
        <Card title="Attendance"><p className="text-3xl font-bold text-portal-navy"><Percent value={a.attendancePercent} hidden={SMALL} /></p></Card>
      </div>
      <Card title="Assignments" padding="none">
        {a.assignments.length === 0 ? <EmptyState title="No published assignments." description="They appear here once published." /> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line bg-surface-muted text-xs text-ink-muted">
                <tr><th scope="col" className="px-4 py-2">Assignment</th><th scope="col" className="px-4 py-2">Due</th><th scope="col" className="px-4 py-2">Handed in</th><th scope="col" className="px-4 py-2">Late</th><th scope="col" className="px-4 py-2">Average mark</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {a.assignments.map((line) => (
                  <tr key={line.assignmentId}>
                    <th scope="row" className="px-4 py-2 font-medium text-ink">{line.title}</th>
                    <td className="px-4 py-2">{formatDateTime(line.dueAt)}</td>
                    <td className="px-4 py-2">{line.submitted} (<Percent value={line.submittedPercent} hidden={SMALL} />)</td>
                    <td className="px-4 py-2">{line.late}</td>
                    <td className="px-4 py-2"><Percent value={line.averagePercent} hidden={SMALL} /> of {line.graded} graded</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {a.quizzes.map((quiz) => (
        <Card key={quiz.quizId} title={`Quiz: ${quiz.title}`} description={`${quiz.handedIn} handed in · average `} padding="none">
          <p className="px-4 pt-2 text-sm text-ink-muted sm:px-5">Average score <Percent value={quiz.averagePercent} hidden={SMALL} /></p>
          <ol className="divide-y divide-line text-sm">
            {quiz.questions.map((question) => (
              <li key={question.position} className="flex flex-wrap justify-between gap-2 px-4 py-2 sm:px-5">
                <span className="min-w-0 break-words text-ink">{question.position}. {question.question}</span>
                <span className="text-ink-muted">{question.answered} answered · <Percent value={question.correctPercent} hidden={SMALL} /> correct</span>
              </li>
            ))}
          </ol>
        </Card>
      ))}
      <Card title="Required pages" padding="none">
        {a.requiredPages.length === 0 ? <EmptyState title="No required pages." description="Pages marked required appear here." /> : (
          <ul className="divide-y divide-line text-sm">
            {a.requiredPages.map((page) => (
              <li key={page.pageId} className="flex justify-between px-4 py-2 sm:px-5">
                <span className="text-ink">{page.title}</span>
                <span className="text-ink-muted">{page.completed} completed · <Percent value={page.completedPercent} hidden={SMALL} /></span>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <ExportsPanel kind="COURSE_ANALYTICS" parameter={lmsCourseId} label="Export these analytics (Excel)" />
    </div>
  )
}
