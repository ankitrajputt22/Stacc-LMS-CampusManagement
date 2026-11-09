import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'
import { getQuestions } from '../api/lmsQuestionBankApi'
import {
  addQuizQuestion,
  archiveQuiz,
  changeQuizQuestionMarks,
  getFacultyQuiz,
  publishQuiz,
  removeQuizQuestion,
  reorderQuizQuestions,
  updateQuiz,
} from '../api/lmsQuizzesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { LmsQuestion, LmsQuiz, LmsQuizQuestion } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, SelectField, TextField } from '../ui'
import { difficultyLabel, QUESTION_TYPES, questionTypeLabel } from './questionText'
import { quizFacts, quizTypeLabel, quizWindow, settingsOf } from './quizSettings'
import { QUIZ_FORBIDDEN, QuizSettingsForm } from './QuizSettingsForm'
import { QuizStatusBadge } from './QuizzesEditor'
import { useContentChanges } from './useContentChanges'

const MARKS_PATTERN = /^\d{1,6}(\.\d{1,2})?$/

function plainName(text: string): string {
  const line = text.replace(/\s+/g, ' ').trim()
  return line.length > 60 ? `${line.slice(0, 57)}…` : line
}

function refusalText(status: number | null, apiMessage: string | null, failed: string): string {
  return status === 403 ? QUIZ_FORBIDDEN : apiMessage && status !== null && status < 500 ? apiMessage : failed
}

type Changes = ReturnType<typeof useContentChanges>

/** One question of the quiz; in a draft with its marks, Move up, Move down, and Remove. */
function QuizQuestionRow({ quiz, question, changes }: { quiz: LmsQuiz; question: LmsQuizQuestion; changes: Changes }) {
  const draft = quiz.status === 'DRAFT'
  const [marks, setMarks] = useState(String(question.marks))
  const [marksError, setMarksError] = useState<string | null>(null)
  const count = quiz.questions.length
  const name = plainName(question.text)
  const order = quiz.questions.map((entry) => entry.id)

  function move(offset: number) {
    const at = order.indexOf(question.id)
    const next = [...order]
    next.splice(at, 1)
    next.splice(at + offset, 0, question.id)
    void changes.run(() => reorderQuizQuestions(quiz.id, next), {
      done: `Moved question ${question.position} ${offset < 0 ? 'up' : 'down'}.`,
    })
  }

  function saveMarks(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const typed = marks.trim()
    if (!MARKS_PATTERN.test(typed) || Number(typed) <= 0) {
      setMarksError('Enter marks greater than zero, with at most 2 decimal places.')
      return
    }
    setMarksError(null)
    void changes.run(() => changeQuizQuestionMarks(quiz.id, question.id, Number(typed)), {
      done: `Saved the marks of question ${question.position}.`,
    })
  }

  return (
    <li className="space-y-3 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="font-semibold text-ink">Question {question.position}</span>
        <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
        {!draft ? null : question.status === 'ARCHIVED' ? (
          <Badge tone="danger">Archived in the bank</Badge>
        ) : question.reviewStatus === 'NEEDS_REVIEW' ? (
          <Badge tone="warning">Needs review</Badge>
        ) : quiz.type === 'PRACTICE' && !question.practiceAllowed ? (
          <Badge tone="warning">Not offered for practice</Badge>
        ) : null}
      </div>
      <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
      <p className="text-sm text-ink-muted">
        {[question.topic, difficultyLabel(question.difficulty), `${question.marks} ${question.marks === 1 ? 'mark' : 'marks'}`]
          .filter(Boolean)
          .join(' · ')}
      </p>
      {draft && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <form className="flex items-end gap-2" onSubmit={saveMarks} noValidate>
            <TextField
              label={`Marks for question ${question.position}`}
              className="w-40"
              inputMode="decimal"
              value={marks}
              error={marksError}
              onChange={(event) => setMarks(event.target.value)}
            />
            <Button type="submit" variant="secondary" size="sm" disabled={changes.busy}>
              Save marks<span className="sr-only">{` for question ${question.position}`}</span>
            </Button>
          </form>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" size="sm" disabled={changes.busy || question.position === 1} onClick={() => move(-1)}>
              Move up<span className="sr-only">{` question ${question.position}`}</span>
            </Button>
            <Button variant="secondary" size="sm" disabled={changes.busy || question.position === count} onClick={() => move(1)}>
              Move down<span className="sr-only">{` question ${question.position}`}</span>
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={changes.busy}
              onClick={() =>
                void changes.run(() => removeQuizQuestion(quiz.id, question.id), {
                  done: `Removed “${name}” from the quiz. It stays in the question bank.`,
                })
              }
            >
              Remove<span className="sr-only">{` question ${question.position} from the quiz`}</span>
            </Button>
          </div>
        </div>
      )}
    </li>
  )
}

/** The active bank questions not yet in the quiz, searchable, each with Add. Shown only while the quiz is a draft. */
function BankPicker({ quiz, bank, changes }: { quiz: LmsQuiz; bank: LmsQuestion[]; changes: Changes }) {
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')
  const used = new Set(quiz.questions.map((question) => question.questionId))
  const words = search.trim().toLowerCase()
  const available = bank.filter((question) => question.status === 'ACTIVE' && !used.has(question.id))
  const shown = available.filter(
    (question) =>
      (type === '' || question.type === type) &&
      (words === '' || `${question.text} ${question.topic ?? ''}`.toLowerCase().includes(words)),
  )

  return (
    <Card
      title="Add from the question bank"
      description={
        quiz.type === 'PRACTICE'
          ? 'A practice quiz can be published only with reviewed questions offered for practice.'
          : 'A quiz can be published only with reviewed questions.'
      }
      padding="none"
    >
      <form
        role="search"
        aria-label="Filter bank questions"
        className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5"
        onSubmit={(event) => event.preventDefault()}
      >
        <TextField label="Search the bank" value={search} onChange={(event) => setSearch(event.target.value)} />
        <SelectField label="Question type" value={type} onChange={(event) => setType(event.target.value)}>
          <option value="">Any type</option>
          {QUESTION_TYPES.map((choice) => (
            <option key={choice.value} value={choice.value}>
              {choice.label}
            </option>
          ))}
        </SelectField>
      </form>
      {available.length === 0 ? (
        <EmptyState title="No more questions to add." description="Every active question of the bank is in this quiz, or the bank is empty." />
      ) : shown.length === 0 ? (
        <EmptyState title="No questions match these filters." />
      ) : (
        <ul className="divide-y divide-line">
          {shown.map((question) => (
            <li key={question.id} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start sm:justify-between sm:px-5">
              <div className="min-w-0 space-y-1.5">
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
                  {question.reviewStatus === 'REVIEWED' ? <Badge tone="success">Reviewed</Badge> : <Badge tone="warning">Needs review</Badge>}
                  {question.practiceAllowed && <Badge tone="info">OK for practice</Badge>}
                </div>
                <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
                <p className="text-sm text-ink-muted">
                  {[question.topic, difficultyLabel(question.difficulty), `${question.marks} ${question.marks === 1 ? 'mark' : 'marks'}`]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              </div>
              <Button
                variant="secondary"
                size="sm"
                className="shrink-0"
                disabled={changes.busy}
                onClick={() =>
                  void changes.run(() => addQuizQuestion(quiz.id, question.id), {
                    done: `Added “${plainName(question.text)}” to the quiz.`,
                  })
                }
              >
                Add<span className="sr-only">{` “${plainName(question.text)}” to the quiz`}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The quiz's settings as a short list, with the times in the browser's own time zone. */
function SettingsSummary({ quiz }: { quiz: LmsQuiz }) {
  const rows: [string, string][] = [
    ['Type', quizTypeLabel(quiz.type)],
    ['Time limit', quiz.timeLimitMinutes === null ? 'None' : `${quiz.timeLimitMinutes} minutes`],
    ['Attempts allowed', quiz.maxAttempts === null ? 'Unlimited' : String(quiz.maxAttempts)],
    ['Opens', quiz.availableFrom ? formatDateTime(quiz.availableFrom) : 'When published'],
    ['Due', quiz.dueAt ? formatDateTime(quiz.dueAt) : 'No due date'],
    ['Closes', quiz.availableUntil ? formatDateTime(quiz.availableUntil) : 'Never'],
    ['Shuffle questions', quiz.shuffleQuestions ? 'Yes' : 'No'],
    ['Shuffle choices', quiz.shuffleOptions ? 'Yes' : 'No'],
    ['Negative marking', Number(quiz.negativeMarkPercent) === 0 ? 'None' : `${quiz.negativeMarkPercent}% of a question’s marks`],
    ['Results', quiz.resultRelease === 'AFTER_DUE_DATE' ? 'After the due date, once marked' : 'Once marked'],
    ['Correct answers with results', quiz.showAnswers ? 'Shown' : 'Not shown'],
  ]
  return (
    <div className="space-y-4">
      {quiz.instructions && (
        <FormattedText text={quiz.instructions} format={quiz.instructionsFormat} className="text-sm leading-6 text-ink" headingLevel={3} />
      )}
      <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-3">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-ink-muted">{label}</dt>
            <dd className="font-medium text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

/**
 * One quiz as faculty manage it: its status with Publish and Archive (each confirmed), its settings, its questions (in a
 * draft: marks, Move up, Move down, Remove), and the bank questions to add. A published quiz keeps its questions,
 * type, shuffling, and negative marking; an archived one is read-only.
 */
export function QuizEditor({ lmsCourseId, quizId }: { lmsCourseId: number; quizId: number }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const quiz = useApiQuery(queryKeys.lms.facultyQuiz(quizId), (signal) => getFacultyQuiz(quizId, signal))
  const isDraft = quiz.status === 'loaded' && quiz.data.status === 'DRAFT'
  // The bank is needed only while questions can still be added.
  const bank = useApiQuery(isDraft ? queryKeys.lms.questions(lmsCourseId) : null, (signal) => getQuestions(lmsCourseId, signal))
  const [confirming, setConfirming] = useState<'publish' | 'archive' | null>(null)
  const [editingSettings, setEditingSettings] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const changes = useContentChanges(() => void queryClient.invalidateQueries({ queryKey: queryKeys.lms.all }), QUIZ_FORBIDDEN)

  if (quiz.status !== 'loaded') {
    return (
      <Card title="Quiz">
        {quiz.status === 'notFound' ? (
          <EmptyState title="Quiz unavailable" description="This quiz could not be found, or it is not in a course you teach." />
        ) : (
          <SectionState request={quiz} what="the quiz" />
        )}
      </Card>
    )
  }

  const data = quiz.data
  async function saved(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <p role="status" className="sr-only">
        {changes.announcement}
      </p>
      <Card
        title={data.title}
        action={
          data.status === 'ARCHIVED' ? (
            <ButtonLink to={`/teaching/${lmsCourseId}/quizzes/${data.id}/results`} variant="secondary" size="sm">
              Results
            </ButtonLink>
          ) : (
            <div className="flex flex-wrap gap-2">
              {data.status === 'PUBLISHED' && (
                <ButtonLink to={`/teaching/${lmsCourseId}/quizzes/${data.id}/results`} variant="secondary" size="sm">
                  Results
                </ButtonLink>
              )}
              {data.status === 'DRAFT' && (
                <Button size="sm" onClick={() => setConfirming('publish')}>
                  Publish quiz
                </Button>
              )}
              <Button variant="secondary" size="sm" onClick={() => setConfirming('archive')}>
                Archive quiz
              </Button>
            </div>
          )
        }
      >
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="brand">{quizTypeLabel(data.type)}</Badge>
            <QuizStatusBadge status={data.status} />
          </div>
          <p className="text-sm text-ink-muted">
            {quizFacts({ ...data, questionCount: data.questions.length })}
          </p>
          {quizWindow(data) && <p className="text-sm text-ink-muted">{quizWindow(data)}</p>}
          <p className="text-sm text-ink-muted">
            {data.status === 'DRAFT'
              ? 'Draft: students can’t see it. Questions can change until it is published.'
              : data.status === 'PUBLISHED'
                ? `Published ${data.publishedAt ? formatDateTime(data.publishedAt) : ''}. Its questions, type, shuffling, and negative marking no longer change.`
                : 'Archived: kept for history and no longer changes.'}
          </p>
        </div>
        {confirming === 'publish' && (
          <StatusConfirmation
            question={`Publish “${data.title}”?`}
            detail="Its questions, type, shuffling, and negative marking can no longer change, and its questions can no longer be edited in the bank. There is no unpublish."
            confirmLabel="Publish"
            busyLabel="Publishing…"
            describeRefusal={(status, apiMessage) => refusalText(status, apiMessage, 'Couldn’t publish. Try again.')}
            onCancel={() => setConfirming(null)}
            onConfirm={async () => {
              await publishQuiz(data.id)
              setConfirming(null)
              await saved('Published the quiz.')
            }}
          />
        )}
        {confirming === 'archive' && (
          <StatusConfirmation
            question={`Archive “${data.title}”?`}
            detail="It is kept with its questions for history but can no longer change or be published. Nothing restores it."
            confirmLabel="Archive"
            busyLabel="Archiving…"
            describeRefusal={(status, apiMessage) => refusalText(status, apiMessage, 'Couldn’t archive. Try again.')}
            onCancel={() => setConfirming(null)}
            onConfirm={async () => {
              await archiveQuiz(data.id)
              setConfirming(null)
              await saved('Archived the quiz.')
              void navigate(`/teaching/${lmsCourseId}/quizzes`)
            }}
          />
        )}
      </Card>

      <Card
        title="Settings"
        action={
          data.status === 'ARCHIVED' || editingSettings ? undefined : (
            <Button variant="secondary" size="sm" onClick={() => setEditingSettings(true)}>
              Edit settings
            </Button>
          )
        }
      >
        {editingSettings ? (
          <QuizSettingsForm
            initial={settingsOf(data)}
            published={data.status !== 'DRAFT'}
            submitLabel="Save settings"
            onCancel={() => setEditingSettings(false)}
            onSubmit={async (input) => {
              await updateQuiz(data.id, input)
              setEditingSettings(false)
              await saved('Saved the quiz settings.')
            }}
          />
        ) : (
          <SettingsSummary quiz={data} />
        )}
      </Card>

      <Card title="Questions" padding="none">
        {changes.error && (
          <div className="px-4 pt-4 sm:px-5">
            <Alert tone="error">{changes.error}</Alert>
          </div>
        )}
        {data.questions.length === 0 ? (
          <EmptyState title="No questions yet." description="Add questions from the question bank below." />
        ) : (
          <ol className="divide-y divide-line">
            {data.questions.map((question) => (
              // Keyed by marks too, so a saved or refused change shows the stored marks again.
              <QuizQuestionRow key={`${question.id}-${question.marks}`} quiz={data} question={question} changes={changes} />
            ))}
          </ol>
        )}
      </Card>

      {isDraft &&
        (bank.status === 'loaded' ? (
          <BankPicker quiz={data} bank={bank.data} changes={changes} />
        ) : (
          <Card title="Add from the question bank">
            <SectionState request={bank} what="questions" />
          </Card>
        ))}
    </div>
  )
}
