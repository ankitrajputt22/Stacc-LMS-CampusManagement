import { type FormEvent, useId, useState } from 'react'
import { summarizeCourseTopic } from '../api/aiApi'
import { Button } from '../components/Button'
import { border, fieldClasses } from '../teaching/formStyles'
import type { AiTopicSummary } from '../types/ai'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { hintClasses, labelClasses, panelClasses } from './aiStyles'
import { type CourseTopicChoice, CourseTopicOptions } from './CourseTopicOptions'
import { useAiAction } from './useAiAction'

/** A summary of one published topic, chosen from the course's own published topics. */
export function TopicSummaryPanel({ lmsCourseId, topics }: { lmsCourseId: number; topics: CourseTopicChoice[] }) {
  const id = useId()
  const [topicId, setTopicId] = useState(() => (topics[0] ? String(topics[0].id) : ''))
  const { state, run } = useAiAction<AiTopicSummary>('student')
  const running = state.status === 'running'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (running || topicId === '') {
      return
    }
    await run((signal) => summarizeCourseTopic(lmsCourseId, Number(topicId), signal))
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Topic summary
      </h2>
      {topics.length === 0 ? (
        <p className={hintClasses}>This course has no published topics yet.</p>
      ) : (
        <form onSubmit={submit} className="mt-3">
          <label htmlFor={`${id}-topic`} className={labelClasses}>
            Topic
          </label>
          <select
            id={`${id}-topic`}
            value={topicId}
            onChange={(event) => setTopicId(event.target.value)}
            className={`${fieldClasses} ${border(false)} h-11 sm:h-9`}
          >
            <CourseTopicOptions topics={topics} />
          </select>
          <div className="mt-3">
            <Button type="submit" disabled={running}>
              Generate summary
            </Button>
          </div>
        </form>
      )}

      <div aria-live="polite" className="mt-4 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating summary…</p>}
        {state.status === 'done' &&
          (state.data.insufficientContext || state.data.summary === null ? (
            <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>
          ) : (
            <div>
              <h3 className="text-sm font-semibold break-words text-ink">Summary of {state.data.topicTitle}</h3>
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{state.data.summary}</p>
              {state.data.keyPoints.length > 0 && (
                <>
                  <h3 className="mt-3 text-sm font-semibold text-ink">Key points</h3>
                  <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-6 text-ink">
                    {state.data.keyPoints.map((point, index) => (
                      <li key={index} className="break-words">
                        {point}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              <AiCitations citations={state.data.citations} />
            </div>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
