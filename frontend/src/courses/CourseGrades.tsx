import { getMyGrades } from '../api/lmsGradebookApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import type { StudentGrades } from '../types/lms'
import { Card, DataTable } from '../ui'
import { cellText, gradeTypeLabel } from './gradeText'
import { SectionState } from './SectionState'

type Item = StudentGrades['items'][number]

/**
 * The signed-in student's own grades in a course: only the columns their teachers have published, with the total of
 * what is graded so far. Never anyone else's marks, and no percentages or ranks.
 */
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
  return (
    <Card
      title="Grades"
      description={
        Number(data.totalOutOf) === 0 ? 'Nothing graded is published yet.' : `Current total: ${data.totalMarks} / ${data.totalOutOf}, over graded work only.`
      }
      padding="none"
    >
      <DataTable<Item>
        caption="Your grades"
        rows={data.items}
        rowKey={(row) => `${row.type}-${row.title}`}
        empty={<EmptyState title="No grades published yet." description="Your teachers publish grades when they are ready." />}
        columns={[
          { key: 'assessment', header: 'Assessment', primary: true, cell: (row) => row.title },
          { key: 'type', header: 'Type', cell: (row) => gradeTypeLabel(row.type) },
          { key: 'score', header: 'Score', align: 'right', cell: (row) => cellText(row) },
          { key: 'max', header: 'Maximum', align: 'right', cell: (row) => row.maxMarks },
          { key: 'feedback', header: 'Feedback', cell: (row) => <span className="break-words whitespace-pre-line">{row.feedback ?? '—'}</span> },
        ]}
      />
    </Card>
  )
}
