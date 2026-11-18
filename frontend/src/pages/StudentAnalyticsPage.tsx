import { getMyAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Percent } from '../analytics/Percent'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { Card, DataTable, PageHeader } from '../ui'

export function StudentAnalyticsPage() {
  const analytics = useApiQuery(queryKeys.analytics.mine(), (signal) => getMyAnalytics(signal))
  return (
    <div className="max-w-6xl">
      <title>My progress · Stacc</title>
      <PageHeader title="My progress" description="Your attendance, assignment work, and results so far. Only you see this." />
      <div className="mt-6 space-y-6">
        {analytics.status !== 'loaded' ? (
          <Card title="My progress"><SectionState request={analytics} what="your progress" /></Card>
        ) : (
          <>
            <div className="grid gap-4 rounded-panel border border-line bg-surface p-4 sm:grid-cols-3">
              {[
                ['Attendance', analytics.data.overallAttendancePercent],
                ['Assignment marks', analytics.data.assignmentAveragePercent],
                [`Results (${analytics.data.publishedResults})`, analytics.data.resultAveragePercent],
              ].map(([label, value]) => (
                <section key={String(label)} aria-label={String(label)} className="rounded-panel border border-line px-5 py-4">
                  <h2 className="text-lg font-bold text-ink">{label}</h2>
                  <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
                  <p className="mt-3 text-4xl font-bold text-ink"><Percent value={value as number | null} hidden="Nothing recorded yet" /></p>
                </section>
              ))}
            </div>
            <Card title="Attendance by course">
              {analytics.data.attendance.length === 0 ? (
                <EmptyState title="No attendance recorded yet." description="Finalized classes appear here." />
              ) : (
                <DataTable
                  caption="Attendance by course"
                  rows={analytics.data.attendance}
                  rowKey={(line) => line.courseCode}
                  columns={[
                    { key: 'course', header: 'Course', primary: true, cell: (line) => <span className="font-bold">{line.courseCode}</span> },
                    { key: 'attended', header: 'Attendance', cell: (line) => `${line.present} of ${line.sessions}` },
                    { key: 'percent', header: 'Attendance %', cell: (line) => <Percent value={line.presentPercent} /> },
                  ]}
                />
              )}
            </Card>
            <Card title="Assignments by course">
              {analytics.data.courses.length === 0 ? (
                <EmptyState title="No current courses." description="Your courses appear here." />
              ) : (
                <DataTable
                  caption="Assignments by course"
                  rows={analytics.data.courses}
                  rowKey={(course) => course.lmsCourseId}
                  columns={[
                    { key: 'code', header: 'Course code', primary: true, cell: (course) => <span className="font-bold">{course.courseCode}</span> },
                    { key: 'name', header: 'Course', cell: (course) => course.courseName },
                    { key: 'handed', header: 'Handed in', cell: (course) => `${course.submitted} handed in of ${course.assignmentsDue} due` },
                    { key: 'late', header: 'Late', cell: (course) => (course.late > 0 ? course.late : <span aria-label="None">–</span>) },
                    { key: 'marks', header: 'Marks', cell: (course) => <Percent value={course.averageMarkPercent} hidden="No marks yet" /> },
                  ]}
                />
              )}
            </Card>
          </>
        )}
      </div>
    </div>
  )
}
