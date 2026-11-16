import { type FormEvent, useId, useState } from 'react'
import { summarizeCourse } from '../api/aiApi'
import { Button } from '../components/Button'
import type { AiSummary, AiSummaryScope, AiSummaryStyle } from '../types/ai'
import { SelectField } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { INSUFFICIENT_CONTEXT } from './aiText'
import { hintClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

/** What the student can summarise, from the course's own published content. */
export type SummaryChoices = {
  modules: { id: number; title: string }[]
  topics: { id: number; title: string; moduleTitle: string }[]
  files: { id: number; title: string; pageTitle: string }[]
}

const SCOPES: { value: AiSummaryScope; label: string }[] = [
  { value: 'TOPIC', label: 'A page' },
  { value: 'MODULE', label: 'A module' },
  { value: 'DOCUMENT', label: 'A file' },
]

const STYLES: { value: AiSummaryStyle; label: string }[] = [
  { value: 'BRIEF', label: 'Brief' },
  { value: 'DETAILED', label: 'Detailed' },
  { value: 'REVISION_NOTES', label: 'Revision notes' },
]

/**
 * Summaries (v0.15.2): a page, a whole module, or one file, in a brief, detailed, or revision-notes style, from the
 * course's published content only, with sources. Sent only on the button; nothing is kept.
 */
export function SummaryPanel({ lmsCourseId, choices }: { lmsCourseId: number; choices: SummaryChoices }) {
  const id = useId()
  const [scope, setScope] = useState<AiSummaryScope>('TOPIC')
  const [style, setStyle] = useState<AiSummaryStyle>('BRIEF')
  const options = scope === 'TOPIC'
    ? choices.topics.map((topic) => ({ id: topic.id, label: `${topic.moduleTitle} · ${topic.title}` }))
    : scope === 'MODULE'
      ? choices.modules.map((module) => ({ id: module.id, label: module.title }))
      : choices.files.map((file) => ({ id: file.id, label: `${file.title} (${file.pageTitle})` }))
  const [itemId, setItemId] = useState('')
  const chosen = options.some((option) => String(option.id) === itemId) ? itemId : options[0] ? String(options[0].id) : ''
  const { state, run } = useAiAction<AiSummary>('student')
  const running = state.status === 'running'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!running && chosen) {
      await run((signal) => summarizeCourse(lmsCourseId, { scope, id: Number(chosen), style }, signal))
    }
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Summaries
      </h2>
      <p className={hintClasses}>Summarise a page, a whole module, or one file, from the course’s published content.</p>
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-3">
        <SelectField label="Summarise" value={scope} onChange={(event) => setScope(event.target.value as AiSummaryScope)}>
          {SCOPES.map((choice) => <option key={choice.value} value={choice.value}>{choice.label}</option>)}
        </SelectField>
        <SelectField label="Which" value={chosen} disabled={options.length === 0} onChange={(event) => setItemId(event.target.value)}>
          {options.length === 0 && <option value="">None published yet</option>}
          {options.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
        </SelectField>
        <SelectField label="Style" value={style} onChange={(event) => setStyle(event.target.value as AiSummaryStyle)}>
          {STYLES.map((choice) => <option key={choice.value} value={choice.value}>{choice.label}</option>)}
        </SelectField>
        <div className="sm:col-span-3">
          <Button type="submit" disabled={running || !chosen}>Summarise</Button>
        </div>
      </form>
      <div aria-live="polite" className="mt-4 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Generating summary…</p>}
        {state.status === 'done' &&
          (state.data.insufficientContext || state.data.summary === null ? (
            <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>
          ) : (
            <div>
              <h3 className="text-sm font-semibold text-ink">{STYLES.find((choice) => choice.value === state.data.style)?.label ?? 'Summary'}</h3>
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{state.data.summary}</p>
              {state.data.keyPoints.length > 0 && (
                <ul className="mt-2 list-disc pl-5 text-sm text-ink">
                  {state.data.keyPoints.map((point) => <li key={point}>{point}</li>)}
                </ul>
              )}
              <AiCitations citations={state.data.citations} pageLink={(pageId) => `/courses/${lmsCourseId}/pages/${pageId}`} />
            </div>
          ))}
      </div>
      {state.status === 'failed' && <AiMessage tone="error" className="mt-4">{state.failure.message}</AiMessage>}
    </section>
  )
}
