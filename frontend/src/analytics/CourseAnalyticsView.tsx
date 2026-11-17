import { getCourseAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { Alert, Card, DataTable } from '../ui'
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
        <section aria-label="Enrolled" className="rounded-panel border border-line bg-surface px-5 py-4">
          <h2 className="text-lg font-bold text-ink">Enrolled</h2>
          <p className="mt-1 text-4xl font-bold text-ink">{a.enrolled}</p>
        </section>
        <section aria-label="Attendance" className="rounded-panel border border-line bg-surface px-5 py-4">
          <h2 className="text-lg font-bold text-ink">Attendance</h2>
          <p className="mt-1 text-4xl font-bold text-ink"><Percent value={a.attendancePercent} hidden={SMALL} /></p>
        </section>
      </div>
      <Card title="Assignments">
        {a.assignments.length === 0 ? <EmptyState title="No published assignments." description="They appear here once published." /> : (
          <DataTable
            caption="Assignments"
            rows={a.assignments}
            rowKey={(line) => line.assignmentId}
            columns={[
              { key: 'title', header: 'Assignment', primary: true, cell: (line) => <span className="font-bold">{line.title}</span> },
              { key: 'due', header: 'Due', cell: (line) => formatDateTime(line.dueAt) },
              { key: 'handed', header: 'Handed in', cell: (line) => <>{line.submitted} (<Percent value={line.submittedPercent} hidden={SMALL} />)</> },
              { key: 'late', header: 'Late', cell: (line) => line.late },
              { key: 'average', header: 'Average mark', cell: (line) => <><Percent value={line.averagePercent} hidden={SMALL} /> of {line.graded} graded</> },
            ]}
          />
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
      <Card title="Required pages">
        {a.requiredPages.length === 0 ? <EmptyState title="No required pages." description="Pages marked required appear here." /> : (
          <DataTable
            caption="Required pages"
            rows={a.requiredPages}
            rowKey={(page) => page.pageId}
            columns={[
              { key: 'page', header: 'Page', primary: true, cell: (page) => <span className="font-bold">{page.title}</span> },
              { key: 'completed', header: 'Completed', cell: (page) => page.completed },
              { key: 'percent', header: 'Completion', cell: (page) => <Percent value={page.completedPercent} hidden={SMALL} /> },
            ]}
          />
        )}
      </Card>
      <ExportsPanel kind="COURSE_ANALYTICS" parameter={lmsCourseId} label="Export these analytics (Excel)" />
    </div>
  )
}
