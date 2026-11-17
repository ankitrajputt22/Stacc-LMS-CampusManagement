import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { evaluatePracticeAnswer, generateCoursePracticeQuestions } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiEvaluation, AiPracticeMode, AiPracticeQuestion, AiPracticeSet } from '../types/ai'
import { SelectField } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { aiFailure, INSUFFICIENT_CONTEXT } from './aiText'
import { hintClasses, labelClasses, panelClasses } from './aiStyles'
import { type CourseTopicChoice, CourseTopicOptions } from './CourseTopicOptions'
import { useAiAction } from './useAiAction'

/** The topic choice for adaptive practice (v0.15.6): the backend picks a page weighted to the student's weaker ones. */
const ADAPTIVE = 'adaptive'

/** Practice marks are out of this; they are practice only, never an official mark. */
const MARKS_EACH = 4
/** A viva grows by its follow-ups; it stops offering them at this many questions. */
const MAX_VIVA_QUESTIONS = 20

const MODES: { value: AiPracticeMode; label: string; hint: string }[] = [
  { value: 'PRACTICE', label: 'Practice', hint: 'One question at a time, with feedback after each answer.' },
  { value: 'MOCK_EXAM', label: 'Mock exam', hint: 'Answer every question, then hand in for marks and feedback.' },
  { value: 'VIVA', label: 'Viva', hint: 'One question at a time, with a follow-up question after each answer that you can answer next.' },
]

const VERDICT: Record<NonNullable<AiEvaluation['verdict']>, string> = {
  CORRECT: 'Correct',
  PARTLY_CORRECT: 'Partly correct',
  INCORRECT: 'Not correct',
}

/** A checked answer, a check that failed, or a mock exam question left empty. */
type Result = AiEvaluation | 'failed' | 'unanswered'

/** One question's marks, feedback, and the model answer after it was checked. */
function Feedback({ question, result }: { question: AiPracticeQuestion; result: Result }) {
  if (result === 'unanswered') {
    return (
      <div className="mt-2">
        <p className="text-sm font-medium text-ink">Not answered · 0 practice marks</p>
        <ModelAnswer question={question} />
      </div>
    )
  }
  if (result === 'failed' || result.insufficientContext || result.marks === null) {
    return (
      <div className="mt-2">
        <AiMessage tone="note">{result === 'failed' ? 'Couldn’t check this answer.' : INSUFFICIENT_CONTEXT}</AiMessage>
        <ModelAnswer question={question} />
      </div>
    )
  }
  return (
    <div className="mt-2 space-y-2 text-sm">
      <p className="font-medium text-ink">
        {result.verdict ? VERDICT[result.verdict] : 'Checked'} · {result.marks} of {result.maxMarks} practice marks
      </p>
      {/* Plain text only. */}
      <p className="leading-6 break-words whitespace-pre-line text-ink">{result.feedback}</p>
      {result.followUpQuestion && (
        <p className="text-ink">
          <span className="font-medium">Follow-up:</span> {result.followUpQuestion}
        </p>
      )}
      <ModelAnswer question={question} />
      <AiCitations citations={result.citations} headingLevel={4} />
    </div>
  )
}

/** The viva follow-up question of a checked answer, if any. */
function followUpOf(result: Result | undefined): string | null {
  return typeof result === 'object' && result.followUpQuestion ? result.followUpQuestion : null
}

function ModelAnswer({ question }: { question: AiPracticeQuestion }) {
  return (
    <details className="text-sm">
      <summary className="cursor-pointer font-medium text-stacc-primary">Model answer</summary>
      <p className="mt-1 leading-6 break-words whitespace-pre-line text-ink">{question.suggestedAnswer}</p>
    </details>
  )
}

/**
 * Practice (v0.15.4): one practice model for practice, mock exams, and viva. Questions come from the course's published
 * content; the student's answers are marked by AI against the model answer and the sources, with each mistake explained.
 * Marks are practice only, never official, and nothing is kept once the page is left.
 */
export function PracticePanel({ lmsCourseId, topics }: { lmsCourseId: number; topics: CourseTopicChoice[] }) {
  const id = useId()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<AiPracticeMode>('PRACTICE')
  const [topicId, setTopicId] = useState('')
  const [count, setCount] = useState('5')
  const [questions, setQuestions] = useState<AiPracticeQuestion[]>([])
  const [answers, setAnswers] = useState<string[]>([])
  const [results, setResults] = useState<(Result | undefined)[]>([])
  const [current, setCurrent] = useState(0)
  const [checking, setChecking] = useState(false)
  const [note, setNote] = useState<string | null>(null)
  const generate = useAiAction<AiPracticeSet>('student')
  const [activeMode, setActiveMode] = useState<AiPracticeMode>('PRACTICE')

  async function start(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const set = await generate.run((signal) =>
      generateCoursePracticeQuestions(lmsCourseId, Number(count), topicId === '' || topicId === ADAPTIVE ? null : Number(topicId),
        signal, topicId === ADAPTIVE),
    )
    if (set) {
      setQuestions(set.insufficientContext ? [] : set.questions)
      setAnswers(set.questions.map(() => ''))
      setResults(set.questions.map(() => undefined))
      setCurrent(0)
      setActiveMode(mode)
      setNote(set.reason && !set.insufficientContext ? `Chosen for you: ${topics.find((topic) => topic.id === set.chosenTopicId)?.title ?? 'a page'}. ${set.reason}` : null)
    }
  }

  async function check(index: number): Promise<AiEvaluation | 'failed'> {
    const question = questions[index]
    try {
      return await evaluatePracticeAnswer(lmsCourseId, {
        question: question.question.slice(0, 1500),
        referenceAnswer: question.suggestedAnswer.slice(0, 2000),
        studentAnswer: answers[index].trim(),
        maxMarks: MARKS_EACH,
        mode: activeMode,
      })
    } catch (failure) {
      const said = aiFailure(failure, 'student')
      if (said) {
        setNote(said.message)
      }
      return 'failed'
    }
  }

  /** A marked answer is learning evidence, so the student's mastery and next steps may have changed. */
  function refreshProgress() {
    void queryClient.invalidateQueries({ queryKey: queryKeys.lms.mastery(lmsCourseId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.lms.studyHome() })
  }

  async function checkOne(index: number) {
    if (checking || answers[index].trim() === '') {
      return
    }
    setChecking(true)
    const result = await check(index)
    setResults((previous) => previous.map((item, position) => (position === index ? result : item)))
    setChecking(false)
    refreshProgress()
  }

  /** Viva: the follow-up becomes the next question, checked against the same model answer and course sources. */
  function answerFollowUp(index: number, followUpQuestion: string) {
    const asked: AiPracticeQuestion = {
      question: followUpQuestion,
      suggestedAnswer: questions[index].suggestedAnswer,
      citations: [],
    }
    const insert = <T,>(items: T[], item: T) => [...items.slice(0, index + 1), item, ...items.slice(index + 1)]
    setQuestions((previous) => insert(previous, asked))
    setAnswers((previous) => insert(previous, ''))
    setResults((previous) => insert(previous, undefined))
    setCurrent(index + 1)
  }

  async function handIn() {
    if (checking) {
      return
    }
    setChecking(true)
    // One at a time: the backend runs one AI request per student at once.
    for (let index = 0; index < questions.length; index++) {
      if (answers[index].trim() === '') {
        setResults((previous) => previous.map((item, position) => (position === index ? 'unanswered' : item)))
        continue
      }
      const result = await check(index)
      setResults((previous) => previous.map((item, position) => (position === index ? result : item)))
    }
    setChecking(false)
    refreshProgress()
  }

  const scored = results.filter((result): result is AiEvaluation => typeof result === 'object' && result.marks !== null)
  const total = scored.reduce((sum, result) => sum + (result.marks ?? 0), 0)
  const handedIn = activeMode === 'MOCK_EXAM' && results.length > 0 && results.every((result) => result !== undefined)

  function answerBox(index: number) {
    return (
      <div className="mt-2">
        <label htmlFor={`${id}-answer-${index}`} className={labelClasses}>
          Your answer<span className="sr-only">{` to question ${index + 1}`}</span>
        </label>
        <textarea
          id={`${id}-answer-${index}`}
          rows={3}
          maxLength={4000}
          value={answers[index]}
          disabled={results[index] !== undefined}
          onChange={(event) => setAnswers((previous) => previous.map((item, position) => (position === index ? event.target.value : item)))}
          className={`${fieldClasses} ${border(false)} py-2 leading-6`}
        />
      </div>
    )
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-2xl font-bold text-ink">
        Practice
      </h2>
      <p className={hintClasses}>Practice marks are for your own practice only. They are never official marks, and nothing is kept.</p>
      <form onSubmit={start} className="mt-3 grid gap-3 sm:grid-cols-3">
        <SelectField label="Mode" value={mode} onChange={(event) => setMode(event.target.value as AiPracticeMode)}>
          {MODES.map((choice) => <option key={choice.value} value={choice.value}>{choice.label}</option>)}
        </SelectField>
        <div>
          <label htmlFor={`${id}-topic`} className={labelClasses}>Topic</label>
          <select id={`${id}-topic`} value={topicId} onChange={(event) => setTopicId(event.target.value)}
            className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}>
            <option value="">The whole course</option>
            <option value={ADAPTIVE}>Focus on what I need</option>
            <CourseTopicOptions topics={topics} />
          </select>
        </div>
        <SelectField label="Questions" value={count} onChange={(event) => setCount(event.target.value)}>
          {['1', '3', '5', '10'].map((value) => <option key={value} value={value}>{value}</option>)}
        </SelectField>
        <p className="text-sm text-ink-muted sm:col-span-3">{MODES.find((choice) => choice.value === mode)?.hint}</p>
        <div className="sm:col-span-3">
          <Button type="submit" disabled={generate.state.status === 'running' || checking}>
            {generate.state.status === 'running' ? 'Preparing questions…' : 'Start'}
          </Button>
        </div>
      </form>
      {generate.state.status === 'failed' && <AiMessage tone="error" className="mt-3">{generate.state.failure.message}</AiMessage>}
      {generate.state.status === 'done' && questions.length === 0 && <AiMessage tone="note" className="mt-3">{INSUFFICIENT_CONTEXT}</AiMessage>}
      {note && <AiMessage tone="note" className="mt-3">{note}</AiMessage>}

      {questions.length > 0 && activeMode !== 'MOCK_EXAM' && (
        <div className="mt-4 rounded-control border border-line px-3 py-3" aria-live="polite">
          <p className="text-sm text-ink-muted">Question {current + 1} of {questions.length}</p>
          <p className="mt-1 text-sm leading-6 font-medium break-words whitespace-pre-line text-ink">{questions[current].question}</p>
          {answerBox(current)}
          {results[current] === undefined ? (
            <Button className="mt-2" size="sm" disabled={checking || answers[current].trim() === ''} onClick={() => void checkOne(current)}>
              {checking ? 'Checking…' : 'Check answer'}
            </Button>
          ) : (
            <>
              <Feedback question={questions[current]} result={results[current] as Result} />
              {activeMode === 'VIVA' && followUpOf(results[current]) && questions.length < MAX_VIVA_QUESTIONS
                && questions[current + 1]?.question !== followUpOf(results[current]) && (
                <Button className="mt-2 mr-2" size="sm" onClick={() => answerFollowUp(current, followUpOf(results[current]) as string)}>
                  Answer the follow-up
                </Button>
              )}
              {current + 1 < questions.length && (
                <Button className="mt-2" size="sm" variant="secondary" onClick={() => setCurrent(current + 1)}>Next question</Button>
              )}
            </>
          )}
        </div>
      )}

      {questions.length > 0 && activeMode === 'MOCK_EXAM' && (
        <div className="mt-4" aria-live="polite">
          <ol className="space-y-3">
            {questions.map((question, index) => (
              <li key={`${index}-${question.question}`} className="rounded-control border border-line px-3 py-3">
                <p className="text-sm leading-6 font-medium break-words whitespace-pre-line text-ink">
                  {index + 1}. {question.question}
                </p>
                {answerBox(index)}
                {results[index] !== undefined && <Feedback question={question} result={results[index] as Result} />}
              </li>
            ))}
          </ol>
          {handedIn ? (
            <p className="mt-3 text-sm font-medium text-ink">
              Mock exam score: {total} of {questions.length * MARKS_EACH} practice marks
            </p>
          ) : (
            <Button className="mt-3" disabled={checking} onClick={() => void handIn()}>
              {checking ? 'Marking…' : 'Hand in mock exam'}
            </Button>
          )}
        </div>
      )}
    </section>
  )
}
