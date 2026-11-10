import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { getQuestions } from '../api/lmsQuestionBankApi'
import {
  archiveExamPaper,
  downloadExamPaper,
  editExamPaper,
  finalizeExamPaper,
  getExamPaper,
  setExamPaperQuestions,
} from '../api/lmsExamPapersApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { saveBlob } from '../courses/materials'
import { SectionState } from '../courses/SectionState'
import { FormattedText } from '../formatting/FormattedText'
import type { TextFormat } from '../formatting/textFormat'
import type { ExamPaper, LmsQuestion, QuestionDifficulty, QuestionType } from '../types/lms'
import { Alert, Badge, Button, ButtonLink, Card, SelectField, TextField } from '../ui'
import { answerKeyText, marksText, PAPER_FORBIDDEN, paperFacts, paperRefusal, parseMarks } from './examPaperText'
import { PaperDetailsForm, PaperStatusBadge } from './ExamPapersEditor'
import { difficultyLabel, QUESTION_TYPES, questionName, questionTypeLabel } from './questionText'

/** A question in the builder's working list, before it is saved. */
type Working = {
  questionId: number
  sectionTitle: string
  marks: string
  type: QuestionType
  text: string
  textFormat: TextFormat
  topic: string | null
  difficulty: QuestionDifficulty
}

function workingOf(paper: ExamPaper): Working[] {
  return paper.questions.map((question) => ({
    questionId: question.questionId,
    sectionTitle: question.sectionTitle ?? '',
    marks: String(question.marks),
    type: question.type,
    text: question.text,
    textFormat: question.textFormat,
    topic: question.topic,
    difficulty: question.difficulty,
  }))
}

function refusalWords(status: number | null, message: string | null, failed: string): string {
  return status === 403 ? PAPER_FORBIDDEN : message && status !== null && status < 500 ? message : failed
}

/** The draft's questions: section and marks for each, Move up, Move down, Remove, and Save questions. */
function QuestionBuilder({ paper, bank, onSaved }: { paper: ExamPaper; bank: LmsQuestion[] | null; onSaved: (message: string) => Promise<void> }) {
  const [working, setWorking] = useState<Working[]>(() => workingOf(paper))
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')

  function change(next: Working[]) {
    setWorking(next)
    setDirty(true)
  }

  function move(index: number, offset: number) {
    const next = [...working]
    const [item] = next.splice(index, 1)
    next.splice(index + offset, 0, item)
    change(next)
  }

  async function save() {
    const questions = []
    for (const [index, item] of working.entries()) {
      const marks = parseMarks(item.marks)
      if (marks === null) {
        setError(`Question ${index + 1}: enter marks greater than zero, with at most 2 decimal places.`)
        return
      }
      questions.push({ questionId: item.questionId, sectionTitle: item.sectionTitle.trim() || null, marks })
    }
    setBusy(true)
    setError(null)
    try {
      await setExamPaperQuestions(paper.id, questions)
      setDirty(false)
      await onSaved('Saved the questions.')
    } catch (refused) {
      setError(paperRefusal(refused, 'Couldn’t save the questions. Try again.'))
    } finally {
      setBusy(false)
    }
  }

  const used = new Set(working.map((item) => item.questionId))
  const words = search.trim().toLowerCase()
  const available = (bank ?? []).filter(
    (question) => question.status === 'ACTIVE' && question.reviewStatus === 'REVIEWED' && !used.has(question.id),
  )
  const shown = available.filter(
    (question) =>
      (type === '' || question.type === type) && (words === '' || `${question.text} ${question.topic ?? ''}`.toLowerCase().includes(words)),
  )
  const total = working.reduce((sum, item) => sum + (parseMarks(item.marks) ?? 0), 0)

  return (
    <>
      <Card
        title="Questions"
        description={`${working.length} ${working.length === 1 ? 'question' : 'questions'} · ${marksText(total)}${dirty ? ' · unsaved changes' : ''}`}
        padding="none"
        action={
          <Button size="sm" disabled={busy || !dirty} onClick={() => void save()}>
            {busy ? 'Saving…' : 'Save questions'}
          </Button>
        }
      >
        {error && (
          <div className="px-4 pt-4 sm:px-5">
            <Alert tone="error">{error}</Alert>
          </div>
        )}
        {working.length === 0 ? (
          <EmptyState title="No questions yet." description="Add reviewed questions from the bank below, then save." />
        ) : (
          <ol className="divide-y divide-line">
            {working.map((item, index) => (
              <li key={item.questionId} className="space-y-3 px-4 py-4 sm:px-5">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="font-semibold text-ink">Question {index + 1}</span>
                  <Badge tone="brand">{questionTypeLabel(item.type)}</Badge>
                </div>
                <FormattedText text={item.text} format={item.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
                <div className="grid gap-3 sm:grid-cols-2">
                  <TextField
                    label={`Section for question ${index + 1}`}
                    value={item.sectionTitle}
                    maxLength={100}
                    onChange={(event) => change(working.map((entry, at) => (at === index ? { ...entry, sectionTitle: event.target.value } : entry)))}
                  />
                  <TextField
                    label={`Marks for question ${index + 1}`}
                    inputMode="decimal"
                    value={item.marks}
                    onChange={(event) => change(working.map((entry, at) => (at === index ? { ...entry, marks: event.target.value } : entry)))}
                  />
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" size="sm" disabled={index === 0} onClick={() => move(index, -1)}>
                    Move up<span className="sr-only">{` question ${index + 1}`}</span>
                  </Button>
                  <Button variant="secondary" size="sm" disabled={index === working.length - 1} onClick={() => move(index, 1)}>
                    Move down<span className="sr-only">{` question ${index + 1}`}</span>
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => change(working.filter((_, at) => at !== index))}>
                    Remove<span className="sr-only">{` question ${index + 1} from the paper`}</span>
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        )}
      </Card>

      <Card title="Add from the question bank" description="Only reviewed, active questions can go in a paper." padding="none">
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
        {bank === null ? (
          <p className="px-4 py-4 text-sm text-ink-muted sm:px-5">Loading the question bank…</p>
        ) : available.length === 0 ? (
          <EmptyState title="No more questions to add." description="Every reviewed question of the bank is in this paper, or none are reviewed yet." />
        ) : shown.length === 0 ? (
          <EmptyState title="No questions match these filters." />
        ) : (
          <ul className="divide-y divide-line">
            {shown.map((question) => (
              <li key={question.id} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start sm:justify-between sm:px-5">
                <div className="min-w-0 space-y-1.5">
                  <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
                  <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
                  <p className="text-sm text-ink-muted">
                    {[question.topic, difficultyLabel(question.difficulty), marksText(question.marks)].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  className="shrink-0"
                  onClick={() =>
                    change([
                      ...working,
                      {
                        questionId: question.id,
                        sectionTitle: working.at(-1)?.sectionTitle ?? '',
                        marks: String(question.marks),
                        type: question.type,
                        text: question.text,
                        textFormat: question.textFormat,
                        topic: question.topic,
                        difficulty: question.difficulty,
                      },
                    ])
                  }
                >
                  Add<span className="sr-only">{` “${questionName(question)}” to the paper`}</span>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}

/** A final or archived paper's questions with their answer keys, read-only. */
function QuestionList({ paper }: { paper: ExamPaper }) {
  return (
    <Card title="Questions" padding="none">
      {paper.questions.length === 0 ? (
        <EmptyState title="This paper has no questions." />
      ) : (
        <ol className="divide-y divide-line">
          {paper.questions.map((question) => (
            <li key={question.id} className="space-y-2 px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="font-semibold text-ink">Question {question.position}</span>
                <Badge tone="brand">{questionTypeLabel(question.type)}</Badge>
                {question.sectionTitle && <Badge tone="neutral">{question.sectionTitle}</Badge>}
              </div>
              <FormattedText text={question.text} format={question.textFormat} className="text-sm leading-6 text-ink" headingLevel={4} />
              <p className="text-sm text-ink-muted">{`${marksText(question.marks)} · Answer: ${answerKeyText(question)}`}</p>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}

/**
 * One exam paper: its status with Make final and Archive (each confirmed), Print view and Word downloads (with or
 * without the answer key), its details, and its questions (in a draft, the builder with the bank). A final paper keeps
 * its questions; an archived one is read-only.
 */
export function ExamPaperEditor({ lmsCourseId, paperId }: { lmsCourseId: number; paperId: number }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const paper = useApiQuery(queryKeys.lms.examPaper(paperId), (signal) => getExamPaper(paperId, signal))
  const isDraft = paper.status === 'loaded' && paper.data.status === 'DRAFT'
  const bank = useApiQuery(isDraft ? queryKeys.lms.questions(lmsCourseId) : null, (signal) => getQuestions(lmsCourseId, signal))
  const [confirming, setConfirming] = useState<'final' | 'archive' | null>(null)
  const [editing, setEditing] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const [downloadError, setDownloadError] = useState<string | null>(null)

  if (paper.status !== 'loaded') {
    return (
      <Card title="Exam paper">
        {paper.status === 'notFound' ? (
          <EmptyState title="Paper unavailable" description="This paper could not be found, or it is not in a course you teach." />
        ) : (
          <SectionState request={paper} what="the paper" />
        )}
      </Card>
    )
  }

  const data = paper.data
  async function saved(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
  }

  async function download(answers: boolean) {
    setDownloadError(null)
    try {
      saveBlob(await downloadExamPaper(data.id, answers), `${data.title}${answers ? ' with answers' : ''}.docx`)
      setAnnouncement('The Word document is downloading.')
    } catch (refused) {
      setDownloadError(paperRefusal(refused, 'Couldn’t download the document. Try again.'))
    }
  }

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card
        title={data.title}
        action={
          data.status === 'ARCHIVED' ? undefined : (
            <div className="flex flex-wrap gap-2">
              {data.status === 'DRAFT' && (
                <Button size="sm" onClick={() => setConfirming('final')}>
                  Make final
                </Button>
              )}
              <Button variant="secondary" size="sm" onClick={() => setConfirming('archive')}>
                Archive paper
              </Button>
            </div>
          )
        }
      >
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <PaperStatusBadge status={data.status} />
            <span className="text-sm text-ink-muted">
              {paperFacts({ questionCount: data.questions.length, totalMarks: data.totalMarks, durationMinutes: data.durationMinutes })}
            </span>
          </div>
          <p className="text-sm text-ink-muted">
            {data.status === 'DRAFT'
              ? 'Draft: its details and questions can change. Questions read from the bank as they are now.'
              : data.status === 'FINAL'
                ? `Final ${data.finalizedAt ? formatDateTime(data.finalizedAt) : ''}. Its questions no longer change and can’t be edited in the bank.`
                : 'Archived: kept for history and no longer changes.'}
          </p>
          <div className="flex flex-wrap gap-2">
            <ButtonLink to={`/teaching/${lmsCourseId}/papers/${data.id}/print`} variant="secondary" size="sm">
              Print view
            </ButtonLink>
            <Button variant="secondary" size="sm" onClick={() => void download(false)}>
              Download Word
            </Button>
            <Button variant="secondary" size="sm" onClick={() => void download(true)}>
              Download Word with answers
            </Button>
          </div>
          {downloadError && <Alert tone="error">{downloadError}</Alert>}
        </div>
        {confirming === 'final' && (
          <StatusConfirmation
            question={`Make “${data.title}” final?`}
            detail="Its details and questions can no longer change, and its questions can no longer be edited in the bank. There is no undo."
            confirmLabel="Make final"
            busyLabel="Saving…"
            describeRefusal={(status, message) => refusalWords(status, message, 'Couldn’t make the paper final. Try again.')}
            onCancel={() => setConfirming(null)}
            onConfirm={async () => {
              await finalizeExamPaper(data.id)
              setConfirming(null)
              await saved('The paper is final.')
            }}
          />
        )}
        {confirming === 'archive' && (
          <StatusConfirmation
            question={`Archive “${data.title}”?`}
            detail="It is kept with its questions for history but can no longer change. Nothing restores it."
            confirmLabel="Archive"
            busyLabel="Archiving…"
            describeRefusal={(status, message) => refusalWords(status, message, 'Couldn’t archive. Try again.')}
            onCancel={() => setConfirming(null)}
            onConfirm={async () => {
              await archiveExamPaper(data.id)
              setConfirming(null)
              await saved('Archived the paper.')
              void navigate(`/teaching/${lmsCourseId}/papers`)
            }}
          />
        )}
      </Card>

      <Card
        title="Details"
        action={
          data.status !== 'DRAFT' || editing ? undefined : (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              Edit details
            </Button>
          )
        }
      >
        {editing ? (
          <PaperDetailsForm
            initial={{ title: data.title, instructions: data.instructions, durationMinutes: data.durationMinutes }}
            submitLabel="Save details"
            onCancel={() => setEditing(false)}
            onSubmit={async (input) => {
              await editExamPaper(data.id, input)
              setEditing(false)
              await saved('Saved the paper details.')
            }}
          />
        ) : (
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-ink-muted">Course</dt>
              <dd className="font-medium text-ink">{`${data.courseCode} ${data.courseName}`}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Duration</dt>
              <dd className="font-medium text-ink">{data.durationMinutes === null ? 'Not set' : `${data.durationMinutes} minutes`}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-ink-muted">Instructions</dt>
              <dd className="whitespace-pre-line text-ink">{data.instructions ?? 'None'}</dd>
            </div>
          </dl>
        )}
      </Card>

      {data.status === 'DRAFT' ? (
        // Keyed by the saved version, so a save starts the builder again from what the server kept.
        <QuestionBuilder key={data.updatedAt + data.questions.length} paper={data} bank={bank.status === 'loaded' ? bank.data : null} onSaved={saved} />
      ) : (
        <QuestionList paper={data} />
      )}
    </div>
  )
}
