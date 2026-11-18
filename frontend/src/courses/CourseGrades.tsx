import { getMyGrades } from '../api/lmsGradebookApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import type { StudentGrades } from '../types/lms'
import { Alert, Card, DataTable } from '../ui'
import { cellText, gradeTypeLabel } from './gradeText'
import { SectionState } from './SectionState'

type Item = StudentGrades['items'][number]

export function CourseGrades({ lmsCourseId }: { lmsCourseId: number }) {
  const grades = useApiQuery(queryKeys.lms.myGrades(lmsCourseId), (signal) => getMyGrades(lmsCourseId, signal))
  if (grades.status !== 'loaded') {
    return (
      <Card title="Grades">
        <SectionState request={grades} what="grades" />
      </Card>
    )
  }
  const data = grades.data
  const anythingGraded = Number(data.totalOutOf) > 0
  return (
    <div className="space-y-5">
      <section aria-label="Current total" className="grid gap-4 rounded-panel border border-line bg-surface px-5 py-4 md:grid-cols-[auto_minmax(0,1fr)] md:items-center md:gap-8">
        <div className="md:border-r md:border-line md:pr-8">
          <p className="text-lg font-semibold text-ink">Current total</p>
          {anythingGraded ? (
            <>
              <p className="text-4xl font-bold text-portal-navy tabular-nums">
                {data.totalMarks}/{data.totalOutOf}
              </p>
              <p className="text-[0.95rem] text-ink-muted">Over graded work only</p>
            </>
          ) : (
            <p className="text-[0.95rem] text-ink-muted">Nothing graded is published yet.</p>
          )}
        </div>
        <Alert tone="info">These are course assessment marks; official results are in ERP.</Alert>
      </section>
      <Card padding="none">
        <DataTable<Item>
          caption="Your grades"
          rows={data.items}
          rowKey={(row) => `${row.type}-${row.title}`}
          empty={<EmptyState title="No grades published yet." description="Your teachers publish grades when they are ready." />}
          columns={[
            { key: 'assessment', header: 'Assessment', primary: true, cell: (row) => row.title },
            { key: 'type', header: 'Type', cell: (row) => gradeTypeLabel(row.type) },
            { key: 'score', header: 'Score', cell: (row) => cellText(row) },
            { key: 'max', header: 'Maximum', cell: (row) => row.maxMarks },
            { key: 'feedback', header: 'Feedback', cell: (row) => <span className="break-words whitespace-pre-line">{row.feedback ?? '—'}</span> },
          ]}
        />
      </Card>
      {anythingGraded && <Alert tone="info">The current total includes graded work only.</Alert>}
    </div>
  )
}
