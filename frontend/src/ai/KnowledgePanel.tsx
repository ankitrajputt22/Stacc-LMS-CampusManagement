import { type ReactNode, useId, useMemo } from 'react'
import { getFacultyAiStatus, syncFacultyAiKnowledge } from '../api/aiApi'
import { useApiRequest } from '../api/useApiRequest'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import { formatDateTime } from '../courses/dates'
import type { AiKnowledgeStatus, AiKnowledgeSync } from '../types/ai'
import { AiMessage } from './AiMessage'
import { AI_UNAVAILABLE, KNOWLEDGE_STATUS_LABELS } from './aiText'
import { hintClasses, panelClasses } from './aiStyles'
import { useAiAction } from './useAiAction'

function Row({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 py-2 sm:grid-cols-[11rem_minmax(0,1fr)] sm:gap-4">
      <dt className="text-sm text-ink-muted">{term}</dt>
      <dd className="flex flex-wrap items-center gap-2 text-sm text-ink">{children}</dd>
    </div>
  )
}

function StatusDetails({ status }: { status: AiKnowledgeStatus }) {
  return (
    <dl className="mt-3 divide-y divide-line">
      <Row term="Status">
        <span className="font-medium">{KNOWLEDGE_STATUS_LABELS[status.status]}</span>
        {status.stale && (
          <span className="rounded-control border border-warning/40 bg-warning/5 px-2 py-0.5 text-sm font-medium text-warning">
            Content changed — refresh required
          </span>
        )}
      </Row>
      <Row term="Last indexed">
        {status.indexedAt ? <time dateTime={status.indexedAt}>{formatDateTime(status.indexedAt)}</time> : 'Never'}
      </Row>
      <Row term="Published sources">{status.publishedSourceCount}</Row>
    </dl>
  )
}

/**
 * The course's AI knowledge: its status, whether published content changed since the last refresh, and Refresh AI
 * knowledge, which rebuilds it from published topics and study materials only.
 */
export function KnowledgePanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const request = useMemo(() => (signal: AbortSignal) => getFacultyAiStatus(lmsCourseId, signal), [lmsCourseId])
  const status = useApiRequest(request)
  const sync = useAiAction<AiKnowledgeSync>('faculty')
  const running = sync.state.status === 'running'

  async function refresh() {
    if (running) {
      return
    }
    await sync.run((signal) => syncFacultyAiKnowledge(lmsCourseId, signal))
    status.refresh()
  }

  return (
    <section aria-labelledby={`${id}-heading`} aria-busy={running} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Course AI Knowledge
      </h2>
      <p className={hintClasses}>
        Built only from this course’s published topics and study materials. Refresh it after you publish or change
        content.
      </p>

      {status.status === 'loading' && <p className="mt-3 text-sm text-ink-muted">Loading AI knowledge status…</p>}
      {status.status === 'failed' && (
        <div role="alert" className="mt-3 flex flex-wrap items-center gap-3">
          <p className="text-sm text-ink">Couldn’t load the AI knowledge status.</p>
          <ActionButton onClick={status.retry}>Retry</ActionButton>
        </div>
      )}
      {(status.status === 'denied' || status.status === 'notFound') && (
        <p className="mt-3 text-sm text-ink-muted">AI knowledge is not available for this course.</p>
      )}
      {status.status === 'loaded' && (
        <>
          <StatusDetails status={status.data} />
          {!status.data.enabled && <AiMessage tone="note" className="mt-3">{AI_UNAVAILABLE}</AiMessage>}
          {status.data.status === 'FAILED' && status.data.failureMessage && (
            <AiMessage tone="note" className="mt-3">
              The last refresh failed: {status.data.failureMessage}
            </AiMessage>
          )}
          <div className="mt-3">
            <Button onClick={refresh} disabled={running || !status.data.enabled}>
              {running ? 'Refreshing AI knowledge…' : 'Refresh AI knowledge'}
            </Button>
          </div>
        </>
      )}

      <div aria-live="polite" className="mt-3 empty:hidden">
        {running && <p className="text-sm text-ink-muted">Refreshing AI knowledge… This can take a few minutes.</p>}
        {sync.state.status === 'done' && (
          <AiMessage tone="success">
            <p className="font-medium">AI knowledge is ready.</p>
            <p>
              Last indexed <time dateTime={sync.state.data.indexedAt}>{formatDateTime(sync.state.data.indexedAt)}</time>.{' '}
              {sync.state.data.indexedSourceCount} {sync.state.data.indexedSourceCount === 1 ? 'source' : 'sources'} indexed,{' '}
              {sync.state.data.skippedSourceCount} skipped.
            </p>
          </AiMessage>
        )}
      </div>
      {sync.state.status === 'failed' && (
        <div role="alert" className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-6">
          <p className="font-medium text-danger">Couldn’t refresh AI knowledge.</p>
          <p className="text-ink">{sync.state.failure.message}</p>
          <ActionButton className="mt-2" onClick={refresh}>
            Retry
          </ActionButton>
        </div>
      )}
    </section>
  )
}
