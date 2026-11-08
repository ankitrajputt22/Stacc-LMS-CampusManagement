import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getFacultyOutcomes } from '../api/lmsOutcomesApi'
import {
  addQuestion,
  allowQuestionPractice,
  archiveQuestion,
  editQuestion,
  getQuestions,
  markQuestionReviewed,
} from '../api/lmsQuestionBankApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { bloomLabel } from '../courses/bloomText'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { LmsOutcome, LmsQuestion } from '../types/lms'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../ui'
import { draftOf, emptyDraft } from './questionDraft'
import { QuestionForm } from './QuestionForm'
import {
  DIFFICULTIES,
  difficultyLabel,
  filterQuestions,
  NO_FILTERS,
  QUESTION_TYPES,
  questionName,
  questionTopics,
  questionTypeLabel,
  type QuestionFilters,
} from './questionText'
import { useContentChanges } from './useContentChanges'

const FORBIDDEN =
  'You don’t have access to change this course’s question bank. If your access changed recently, sign out and sign in again.'

/** The question's answers, as faculty read them. Plain text, never HTML. */
function Answers({ question }: { question: LmsQuestion }) {
  switch (question.type) {
    case 'TRUE_FALSE':
      return <p className="text-sm text-ink">Answer: {question.trueFalseAnswer ? 'True' : 'False'}</p>
    case 'SHORT_ANSWER':
      return (
        <p className="text-sm break-words text-ink">Accepted answers: {question.options.map((option) => option.text).join(' · ')}</p>
      )
    case 'DESCRIPTIVE':
      return question.answerGuidance ? (
        <p className="text-sm break-words whitespace-pre-line text-ink">
          <span className="font-medium">Answer guidance:</span> {question.answerGuidance}
        </p>
      ) : (
        <p className="text-sm text-ink-muted">No answer guidance.</p>
      )
    default:
      return (
        <ol className="list-[upper-alpha] space-y-0.5 pl-6 text-sm text-ink">
          {question.options.map((option, index) => (
            <li key={index} className="break-words">
              {option.text}
              {option.correct && <span className="ml-1 font-semibold text-success">(correct)</span>}
            </li>
          ))}
        </ol>
      )
  }
}

/** One question with its facts and answers. */
function QuestionSummary({ question }: { question: LmsQuestion }) {
  const facts = [
    question.topic,
    difficultyLabel(question.difficulty),
    `${question.marks} ${question.marks === 1 ? 'mark' : 'marks'}`,
    question.outcome && question.outcome.code,
    question.bloomLevel && `Bloom level ${bloomLabel(question.bloomLevel)}`,
  ].filter((fact): fact is string => Boolean(fact))
  return (
    <div className="min-w-0 space-y-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
        {question.status === 'ARCHIVED' ? (
          <Badge tone="neutral">Archived</Badge>
        ) : question.reviewStatus === 'REVIEWED' ? (
          <Badge tone="success">Reviewed</Badge>
        ) : (
          <Badge tone="warning">Needs review</Badge>
        )}
        {question.practiceAllowed && <Badge tone="info">OK for practice</Badge>}
        {question.inPublishedQuiz && <Badge tone="neutral">In a published quiz</Badge>}
      </div>
      <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
      <Answers question={question} />
      {question.explanation && (
        <div className="text-sm text-ink-muted">
          <p className="font-medium">Explanation</p>
          <FormattedText text={question.explanation} format={question.textFormat} className="leading-6" headingLevel={5} />
        </div>
      )}
      <p className="text-sm text-ink-muted">{facts.join(' · ')}</p>
    </div>
  )
}

/** The outcomes a question may choose: active ones, and the archived one it already has. */
function outcomeChoices(outcomes: LmsOutcome[], current: LmsOutcome | null): LmsOutcome[] {
  return outcomes.filter((outcome) => outcome.status === 'ACTIVE' || outcome.id === current?.id)
}

/**
 * The course's question bank as faculty manage it: Add a question, filter and search the bank, Edit (which needs a new
 * review), Mark reviewed, OK for practice (reviewed questions only), and Archive, confirmed first. Nothing is deleted.
 * Students never see this page: questions carry their answers.
 */
export function QuestionBankEditor({ lmsCourseId }: { lmsCourseId: number }) {
  const queryClient = useQueryClient()
  const questions = useApiQuery(queryKeys.lms.questions(lmsCourseId), (signal) => getQuestions(lmsCourseId, signal))
  const outcomes = useApiQuery(queryKeys.lms.facultyOutcomes(lmsCourseId), (signal) => getFacultyOutcomes(lmsCourseId, signal))
  const [filters, setFilters] = useState<QuestionFilters>(NO_FILTERS)
  const [editing, setEditing] = useState<number | null>(null)
  const [archiving, setArchiving] = useState<LmsQuestion | null>(null)
  const [adding, setAdding] = useState(0)
  const [announcement, setAnnouncement] = useState('')
  const changes = useContentChanges(() => void queryClient.invalidateQueries({ queryKey: queryKeys.lms.all }), FORBIDDEN)

  const allOutcomes = outcomes.status === 'loaded' ? outcomes.data : []
  const shown = questions.status === 'loaded' ? filterQuestions(questions.data, filters) : []
  const topics = questions.status === 'loaded' ? questionTopics(questions.data) : []

  async function saved(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  return (
    <>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <p role="status" className="sr-only">
        {changes.announcement}
      </p>
      <Card
        title="Add a question"
        description="A new question needs review before it can be offered for practice. Students never see the bank."
        className="mb-6"
      >
        <QuestionForm
          key={adding}
          initial={emptyDraft()}
          outcomes={outcomeChoices(allOutcomes, null)}
          submitLabel="Add question"
          onSubmit={async (input) => {
            await addQuestion(lmsCourseId, input)
            setAdding((count) => count + 1)
            await saved('Added the question.')
          }}
        />
      </Card>

      {archiving && (
        <div className="mb-6">
          <StatusConfirmation
            question={`Archive “${questionName(archiving)}”?`}
            detail="It stays in the bank under Archived but can no longer be changed or offered for practice. Nothing restores it."
            confirmLabel="Archive question"
            busyLabel="Archiving…"
            describeRefusal={(status, apiMessage) =>
              status === 403 ? FORBIDDEN : apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t archive. Try again.'
            }
            onCancel={() => setArchiving(null)}
            onConfirm={async () => {
              await archiveQuestion(archiving.id)
              setArchiving(null)
              await saved('Archived the question.')
            }}
          />
        </div>
      )}

      <Card title="Questions" padding="none">
        <form
          role="search"
          aria-label="Filter questions"
          className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5 lg:grid-cols-5"
          onSubmit={(event) => event.preventDefault()}
        >
          <TextField
            label="Search questions"
            className="lg:col-span-1"
            value={filters.search}
            onChange={(event) => setFilters({ ...filters, search: event.target.value })}
          />
          <SelectField
            label="Type"
            value={filters.type}
            onChange={(event) => setFilters({ ...filters, type: event.target.value as QuestionFilters['type'] })}
          >
            <option value="">Any type</option>
            {QUESTION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Topic"
            value={filters.topic}
            onChange={(event) => setFilters({ ...filters, topic: event.target.value })}
          >
            <option value="">Any topic</option>
            {topics.map((topic) => (
              <option key={topic} value={topic}>
                {topic}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Difficulty"
            value={filters.difficulty}
            onChange={(event) => setFilters({ ...filters, difficulty: event.target.value as QuestionFilters['difficulty'] })}
          >
            <option value="">Any difficulty</option>
            {DIFFICULTIES.map((difficulty) => (
              <option key={difficulty.value} value={difficulty.value}>
                {difficulty.label}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Status"
            value={filters.status}
            onChange={(event) => setFilters({ ...filters, status: event.target.value as QuestionFilters['status'] })}
          >
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
            <option value="">Active and archived</option>
          </SelectField>
        </form>

        {changes.error && (
          <div className="px-4 pt-4 sm:px-5">
            <Alert tone="error">{changes.error}</Alert>
          </div>
        )}

        {questions.status !== 'loaded' ? (
          <SectionState request={questions} what="questions" />
        ) : questions.data.length === 0 ? (
          <EmptyState title="No questions yet." description="Add questions above to build the course’s question bank." />
        ) : shown.length === 0 ? (
          <EmptyState title="No questions match these filters." />
        ) : (
          <>
            <p className="px-4 pt-3 text-sm text-ink-muted sm:px-5">
              Showing {shown.length} of {questions.data.length} {questions.data.length === 1 ? 'question' : 'questions'}.
            </p>
            <ol className="divide-y divide-line">
              {shown.map((question) => {
                const name = questionName(question)
                return (
                  <li key={question.id} className="px-4 py-4 sm:px-5">
                    {editing === question.id ? (
                      <QuestionForm
                        initial={draftOf(question)}
                        outcomes={outcomeChoices(allOutcomes, question.outcome)}
                        submitLabel="Save question"
                        onCancel={() => setEditing(null)}
                        onSubmit={async (input) => {
                          await editQuestion(question.id, input)
                          setEditing(null)
                          await saved('Saved the question. It needs a new review.')
                        }}
                      />
                    ) : (
                      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                        <QuestionSummary question={question} />
                        {question.status === 'ACTIVE' && (
                          <div className="flex shrink-0 flex-wrap gap-2">
                            {/* A published quiz keeps asking what it asked, so its questions are not edited. */}
                            {!question.inPublishedQuiz && (
                              <Button variant="secondary" size="sm" onClick={() => setEditing(question.id)}>
                                Edit<span className="sr-only"> question “{name}”</span>
                              </Button>
                            )}
                            {question.reviewStatus === 'NEEDS_REVIEW' ? (
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() =>
                                  void changes.run(() => markQuestionReviewed(question.id), { done: 'Marked the question reviewed.' })
                                }
                              >
                                Mark reviewed<span className="sr-only">: “{name}”</span>
                              </Button>
                            ) : (
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() =>
                                  void changes.run(() => allowQuestionPractice(question.id, !question.practiceAllowed), {
                                    done: question.practiceAllowed
                                      ? 'The question is no longer offered for practice.'
                                      : 'The question is offered for practice.',
                                  })
                                }
                              >
                                {question.practiceAllowed ? 'Not for practice' : 'OK for practice'}
                                <span className="sr-only">: “{name}”</span>
                              </Button>
                            )}
                            <Button variant="secondary" size="sm" onClick={() => setArchiving(question)}>
                              Archive<span className="sr-only"> question “{name}”</span>
                            </Button>
                          </div>
                        )}
                      </div>
                    )}
                  </li>
                )
              })}
            </ol>
          </>
        )}
      </Card>
    </>
  )
}
