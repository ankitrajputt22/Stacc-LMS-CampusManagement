import { getMyAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Percent } from '../analytics/Percent'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { Card, PageHeader } from '../ui'

/** /academic/analytics: the student's own figures as the backend works them out. Only their own; nobody is compared. */
export function StudentAnalyticsPage() {
  const analytics = useApiQuery(queryKeys.analytics.mine(), (signal) => getMyAnalytics(signal))
  return (
    <div className="max-w-4xl">
      <title>My progress · Stacc</title>
      <PageHeader title="My progress" description="Your attendance, assignment work, and results so far. Only you see this." />
      <div className="mt-6 space-y-6">
        {analytics.status !== 'loaded' ? (
          <Card title="My progress"><SectionState request={analytics} what="your progress" /></Card>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              {[
                ['Attendance', analytics.data.overallAttendancePercent],
                ['Assignment marks', analytics.data.assignmentAveragePercent],
                [`Results (${analytics.data.publishedResults})`, analytics.data.resultAveragePercent],
              ].map(([label, value]) => (
                <Card key={String(label)} title={String(label)}>
                  <p className="text-3xl font-bold text-portal-navy"><Percent value={value as number | null} hidden="Nothing recorded yet" /></p>
                </Card>
              ))}
            </div>
            <Card title="Attendance by course" padding="none">
              {analytics.data.attendance.length === 0 ? (
                <EmptyState title="No attendance recorded yet." description="Finalized classes appear here." />
              ) : (
                <ul className="divide-y divide-line text-sm">
                  {analytics.data.attendance.map((line) => (
                    <li key={line.courseCode} className="flex justify-between px-4 py-2 sm:px-5">
                      <span className="font-medium text-ink">{line.courseCode}</span>
                      <span className="text-ink-muted">{line.present} of {line.sessions} · <Percent value={line.presentPercent} /></span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Assignments by course" padding="none">
              {analytics.data.courses.length === 0 ? (
                <EmptyState title="No current courses." description="Your courses appear here." />
              ) : (
                <ul className="divide-y divide-line text-sm">
                  {analytics.data.courses.map((course) => (
                    <li key={course.lmsCourseId} className="flex flex-wrap justify-between gap-2 px-4 py-2 sm:px-5">
                      <span className="font-medium text-ink">{course.courseCode} · {course.courseName}</span>
                      <span className="text-ink-muted">
                        {course.submitted} handed in of {course.assignmentsDue} due{course.late > 0 && `, ${course.late} late`} · marks <Percent value={course.averageMarkPercent} hidden="No marks yet" />
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </>
        )}
      </div>
    </div>
  )
}
