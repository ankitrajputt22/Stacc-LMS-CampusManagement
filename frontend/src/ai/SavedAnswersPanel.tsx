import { useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { deleteSavedAnswer, getSavedAnswers } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { formatDateTime } from '../courses/dates'
import type { AiSavedAnswer } from '../types/ai'
import { Alert, Button } from '../ui'
import { AiCitations } from './AiCitations'
import { hintClasses, panelClasses } from './aiStyles'

/**
 * Saved answers (v0.15.1, decision 6): the AI answers the student chose to keep in this course, newest first, with their
 * sources. Nobody else sees them. Deleting (confirmed first) removes one for good.
 */
export function SavedAnswersPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const queryClient = useQueryClient()
  const key = queryKeys.lms.savedAnswers(lmsCourseId)
  const saved = useApiQuery(key, (signal) => getSavedAnswers(lmsCourseId, signal))
  const [deleting, setDeleting] = useState<AiSavedAnswer | null>(null)
  const [announcement, setAnnouncement] = useState('')

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">
        Saved answers
      </h2>
      <p className={hintClasses}>Answers you chose to keep. Only you see them. AI keeps nothing else from your questions.</p>
      <p role="status" className="mt-2 text-sm text-ink-muted empty:hidden">{announcement}</p>
      {saved.status === 'loading' && <p className="mt-3 text-sm text-ink-muted">Loading saved answers…</p>}
      {saved.status === 'failed' && (
        <Alert tone="error" className="mt-3">
          Couldn’t load your saved answers.{' '}
          <button type="button" className="underline" onClick={saved.retry}>Retry</button>
        </Alert>
      )}
      {(saved.status === 'denied' || saved.status === 'notFound') && (
        <p className="mt-3 text-sm text-ink-muted">Saved answers are not available for this course.</p>
      )}
      {saved.status === 'loaded' &&
        (saved.data.length === 0 ? (
          <p className="mt-3 text-sm text-ink-muted">No saved answers yet. Use Save answer under an answer.</p>
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {saved.data.map((answer) => (
              <li key={answer.id} className="py-3 text-sm">
                <p className="font-medium text-ink">{answer.question}</p>
                <p className="mt-1 leading-6 break-words whitespace-pre-line text-ink">{answer.answer}</p>
                <AiCitations citations={answer.citations} headingLevel={4} pageLink={(pageId) => `/courses/${lmsCourseId}/pages/${pageId}`} />
                <div className="mt-2 flex flex-wrap items-center gap-3">
                  <span className="text-ink-muted">Saved <time dateTime={answer.savedAt}>{formatDateTime(answer.savedAt)}</time></span>
                  <Button size="sm" variant="secondary" onClick={() => setDeleting(answer)}>
                    Delete<span className="sr-only">{` saved answer “${answer.question}”`}</span>
                  </Button>
                </div>
                {deleting?.id === answer.id && (
                  <div className="mt-2">
                    <StatusConfirmation
                      question="Delete this saved answer?"
                      detail="It is removed for good. This cannot be undone."
                      confirmLabel="Delete saved answer"
                      busyLabel="Deleting…"
                      onCancel={() => setDeleting(null)}
                      describeRefusal={(status, message) =>
                        message && status !== null && status < 500 ? message : 'Couldn’t delete it. Try again.'}
                      onConfirm={async () => {
                        await deleteSavedAnswer(lmsCourseId, answer.id)
                        setDeleting(null)
                        setAnnouncement('Deleted the saved answer.')
                        await queryClient.invalidateQueries({ queryKey: key })
                      }}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        ))}
    </section>
  )
}
