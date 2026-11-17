import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { createQuiz, getFacultyQuizzes } from '../api/lmsQuizzesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import type { LmsContentStatus } from '../types/lms'
import { Badge, Button, Card, DataTable, textLinkClasses } from '../ui'
import { emptySettings, quizFacts, quizTypeLabel, quizWindow } from './quizSettings'
import { QuizSettingsForm } from './QuizSettingsForm'

export function QuizStatusBadge({ status }: { status: LmsContentStatus }) {
  switch (status) {
    case 'PUBLISHED':
      return <Badge tone="info">Published</Badge>
    case 'ARCHIVED':
      return <Badge tone="neutral">Archived</Badge>
    default:
      return <Badge tone="neutral">Draft</Badge>
  }
}

export function QuizzesEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const quizzes = useApiQuery(queryKeys.lms.facultyQuizzes(lmsCourseId), (signal) => getFacultyQuizzes(lmsCourseId, signal))
  const [adding, setAdding] = useState(false)

  return (
    <>
      {adding && (
        <Card title="New quiz" description="The quiz starts as a draft. Add questions from the bank, then publish it." className="mb-6">
          <QuizSettingsForm
            initial={emptySettings()}
            submitLabel="Create quiz"
            onCancel={() => setAdding(false)}
            onSubmit={async (input) => {
              const created = await createQuiz(lmsCourseId, input)
              await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
              void navigate(`/teaching/${lmsCourseId}/quizzes/${created.id}`)
            }}
          />
        </Card>
      )}

      <Card
        title="Quizzes"
        padding="none"
        action={
          adding ? undefined : (
            <Button size="sm" onClick={() => setAdding(true)}>
              New quiz
            </Button>
          )
        }
      >
        {quizzes.status !== 'loaded' ? (
          <SectionState request={quizzes} what="quizzes" />
        ) : quizzes.data.length === 0 ? (
          <EmptyState title="No quizzes yet." description="Create a quiz, then add reviewed questions from the question bank." />
        ) : (
          <DataTable<(typeof quizzes.data)[number]>
            caption="Quizzes"
            rows={quizzes.data}
            rowKey={(quiz) => quiz.id}
            columns={[
              {
                key: 'title',
                header: 'Quiz title',
                primary: true,
                cell: (quiz) => (
                  <Link to={`/teaching/${lmsCourseId}/quizzes/${quiz.id}`} className={`font-semibold ${textLinkClasses}`}>
                    {quiz.title}
                  </Link>
                ),
              },
              { key: 'type', header: 'Type', cell: (quiz) => quizTypeLabel(quiz.type) },
              { key: 'status', header: 'Status', cell: (quiz) => <QuizStatusBadge status={quiz.status} /> },
              {
                key: 'facts',
                header: 'Details',
                cell: (quiz) => (
                  <>
                    <span className="block">{quizFacts(quiz)}</span>
                    {quizWindow(quiz) && <span className="block text-ink-muted">{quizWindow(quiz)}</span>}
                  </>
                ),
              },
            ]}
          />
        )}
      </Card>
    </>
  )
}
