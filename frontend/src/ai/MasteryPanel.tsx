import { useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { clearMastery, getMastery } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import type { AiTopicMastery } from '../types/ai'
import { Badge, type BadgeTone, Button } from '../ui'
import { hintClasses, panelClasses } from './aiStyles'

const LEVEL: Record<AiTopicMastery['level'], { label: string; tone: BadgeTone }> = {
  NOT_STARTED: { label: 'Not started', tone: 'neutral' },
  LEARNING: { label: 'Learning', tone: 'warning' },
  PRACTISING: { label: 'Practising', tone: 'info' },
  MASTERED: { label: 'Mastered', tone: 'success' },
}

function confidenceWords(confidence: number): string {
  return confidence >= 0.75 ? 'high confidence' : confidence >= 0.5 ? 'some confidence' : 'low confidence'
}

/**
 * My mastery (v0.15.5): each published page with the student's own mastery, worked out from their practice answers and
 * flashcard reviews. Only they see it. Clear my evidence (confirmed first) starts over for good.
 */
export function MasteryPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const queryClient = useQueryClient()
  const key = queryKeys.lms.mastery(lmsCourseId)
  const mastery = useApiQuery(key, (signal) => getMastery(lmsCourseId, signal))
  const [clearing, setClearing] = useState(false)

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={`${id}-heading`} className="text-2xl font-bold text-ink">My mastery</h2>
        <Button size="sm" variant="ghost" onClick={() => void mastery.refresh()}>Update</Button>
      </div>
      <p className={hintClasses}>
        From your practice answers and flashcard reviews, with recent work counting more. Only you see this.
      </p>
      {mastery.status === 'loaded' && (
        mastery.data.length === 0 ? (
          <p className="mt-3 text-sm text-ink-muted">This course has no published pages yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-line text-sm">
            {mastery.data.map((topic) => (
              <li key={topic.topicId} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="min-w-0 break-words text-ink">{topic.title}</span>
                <span className="flex flex-wrap items-center gap-2 text-ink-muted">
                  <Badge tone={LEVEL[topic.level].tone}>{LEVEL[topic.level].label}</Badge>
                  {topic.evidence > 0 && (
                    <span>
                      {Math.round(topic.mastery * 100)}% · {confidenceWords(topic.confidence)} · {topic.evidence}{' '}
                      {topic.evidence === 1 ? 'try' : 'tries'}
                    </span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )
      )}
      {mastery.status === 'loaded' && mastery.data.some((topic) => topic.evidence > 0) && (
        clearing ? (
          <div className="mt-3">
            <StatusConfirmation
              question="Clear your learning evidence in this course?"
              detail="Your mastery starts over. This cannot be undone."
              confirmLabel="Clear evidence"
              busyLabel="Clearing…"
              onCancel={() => setClearing(false)}
              describeRefusal={(status, message) => (message && status !== null && status < 500 ? message : 'Couldn’t clear it. Try again.')}
              onConfirm={async () => {
                await clearMastery(lmsCourseId)
                setClearing(false)
                await queryClient.invalidateQueries({ queryKey: key })
              }}
            />
          </div>
        ) : (
          <Button className="mt-3" size="sm" variant="secondary" onClick={() => setClearing(true)}>Clear my evidence</Button>
        )
      )}
    </section>
  )
}
