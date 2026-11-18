import { useState } from 'react'
import { suggestModelAnswer } from '../api/aiApi'
import type { AiModelAnswer } from '../types/ai'
import { Button } from '../ui'
import { AiCitations } from './AiCitations'
import { AiMessage } from './AiMessage'
import { DRAFT_WARNING, INSUFFICIENT_CONTEXT } from './aiText'
import { useAiAction } from './useAiAction'

export function ModelAnswerSuggestion({ questionId }: { questionId: number }) {
  const [open, setOpen] = useState(false)
  const { state, run } = useAiAction<AiModelAnswer>('faculty')
  const running = state.status === 'running'

  async function ask() {
    setOpen(true)
    await run((signal) => suggestModelAnswer(questionId, signal))
  }

  return (
    <div className="mt-2 text-sm">
      {!open ? (
        <Button size="sm" variant="ghost" onClick={() => void ask()}>Suggest a model answer</Button>
      ) : (
        <div aria-live="polite" className="rounded-control border border-line px-3 py-2">
          {running && <p className="text-ink-muted">Suggesting a model answer…</p>}
          {state.status === 'done' &&
            (state.data.insufficientContext || state.data.answer === null ? (
              <AiMessage tone="note">{INSUFFICIENT_CONTEXT}</AiMessage>
            ) : (
              <div className="space-y-2">
                <AiMessage tone="warning">{DRAFT_WARNING}</AiMessage>
                <p className="font-medium text-ink">Model answer</p>
                <p className="leading-6 break-words whitespace-pre-line text-ink">{state.data.answer}</p>
                {state.data.keyPoints.length > 0 && (
                  <ul className="list-disc pl-5 text-ink">
                    {state.data.keyPoints.map((point) => <li key={point}>{point}</li>)}
                  </ul>
                )}
                <AiCitations citations={state.data.citations} headingLevel={4} />
                <p className="text-ink-muted">Nothing is saved. Use Edit to copy what you want into the marking guidance.</p>
              </div>
            ))}
          {state.status === 'failed' && <AiMessage tone="error">{state.failure.message}</AiMessage>}
          <Button size="sm" variant="ghost" className="mt-1" onClick={() => setOpen(false)}>Hide</Button>
        </div>
      )}
    </div>
  )
}
