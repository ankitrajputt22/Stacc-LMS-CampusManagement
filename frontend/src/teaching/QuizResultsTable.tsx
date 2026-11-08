import { Link } from 'react-router'
import { getQuizResults } from '../api/lmsQuizzesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { attemptStateLabel } from '../courses/quizText'
import { SectionState } from '../courses/SectionState'
import type { QuizResults } from '../types/lms'
import { Badge, ButtonLink, Card, DataTable } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Row = QuizResults['attempts'][number]

/**
 * Every attempt at one quiz with its automatic marks, teachers' marks, and score once fully marked, with Review for
 * each. Students appear by college ID only. Shows only what the results API sends.
 */
export function QuizResultsTable({ lmsCourseId, quizId }: { lmsCourseId: number; quizId: number }) {
  const results = useApiQuery(queryKeys.lms.quizResults(quizId), (signal) => getQuizResults(quizId, signal))
  if (results.status !== 'loaded') {
    return (
      <Card title="Results">
        {results.status === 'notFound' ? (
          <EmptyState title="Results unavailable" description="This quiz could not be found, is still a draft, or is not in a course you teach." />
        ) : (
          <SectionState request={results} what="results" />
        )}
      </Card>
    )
  }
  const data = results.data
  const waiting = data.attempts.filter((attempt) => attempt.needsMarking > 0).length
  return (
    <Card
      title={data.title}
      description={`${data.attempts.length} ${data.attempts.length === 1 ? 'attempt' : 'attempts'} · out of ${data.totalMarks} marks${waiting > 0 ? ` · ${waiting} waiting for marking` : ''}`}
      action={
        <ButtonLink to={`/teaching/${lmsCourseId}/quizzes/${quizId}`} variant="secondary" size="sm">
          Back to the quiz
        </ButtonLink>
      }
      padding="none"
    >
      <DataTable<Row>
        caption={`Attempts at ${data.title}`}
        rows={data.attempts}
        rowKey={(row) => row.attemptId}
        empty={<EmptyState title="No attempts yet." />}
        columns={[
          {
            key: 'student',
            header: 'Student',
            primary: true,
            cell: (row) => (
              <Link to={`/teaching/${lmsCourseId}/quiz-attempts/${row.attemptId}`} className={textLinkClasses}>
                {row.studentLoginId}
                <span className="sr-only">, attempt {row.attemptNumber}: review</span>
              </Link>
            ),
          },
          { key: 'attempt', header: 'Attempt', cell: (row) => row.attemptNumber, align: 'right' },
          { key: 'submitted', header: 'Handed in', cell: (row) => (row.submittedAt ? `${formatDateTime(row.submittedAt)}${row.late ? ' (late)' : ''}` : '—') },
          {
            key: 'status',
            header: 'Status',
            cell: (row) =>
              row.needsMarking > 0 ? <Badge tone="warning">Needs marking</Badge> : <span>{attemptStateLabel(row.state)}</span>,
          },
          { key: 'automatic', header: 'Automatic', cell: (row) => row.automaticMarks, align: 'right' },
          { key: 'teacher', header: 'Teacher', cell: (row) => row.teacherMarks, align: 'right' },
          { key: 'score', header: 'Score', cell: (row) => (row.score === null ? '—' : row.score), align: 'right' },
        ]}
      />
    </Card>
  )
}
