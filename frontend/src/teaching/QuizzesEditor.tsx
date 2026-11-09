import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { createQuiz, getFacultyQuizzes } from '../api/lmsQuizzesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import type { LmsContentStatus } from '../types/lms'
import { Badge, Button, Card, textLinkClasses } from '../ui'
import { emptySettings, quizFacts, quizTypeLabel, quizWindow } from './quizSettings'
import { QuizSettingsForm } from './QuizSettingsForm'

export function QuizStatusBadge({ status }: { status: LmsContentStatus }) {
  switch (status) {
    case 'PUBLISHED':
      return <Badge tone="success">Published</Badge>
    case 'ARCHIVED':
      return <Badge tone="neutral">Archived</Badge>
    default:
      return <Badge tone="warning">Draft</Badge>
  }
}

/**
 * The course's quizzes as faculty manage them: New quiz (a draft without questions, then opened to add questions), and
 * every quiz with its type, status, size, and times, each opening its own page. Nothing is deleted.
 */
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
          <ol className="divide-y divide-line">
            {quizzes.data.map((quiz) => {
              const window = quizWindow(quiz)
              return (
                <li key={quiz.id} className="space-y-1.5 px-4 py-4 sm:px-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link to={`/teaching/${lmsCourseId}/quizzes/${quiz.id}`} className={`font-semibold ${textLinkClasses}`}>
                      {quiz.title}
                    </Link>
                    <Badge tone="brand">{quizTypeLabel(quiz.type)}</Badge>
                    <QuizStatusBadge status={quiz.status} />
                  </div>
                  <p className="text-sm text-ink-muted">{quizFacts(quiz)}</p>
                  {window && <p className="text-sm text-ink-muted">{window}</p>}
                </li>
              )
            })}
          </ol>
        )}
      </Card>
    </>
  )
}
