import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { createFlashcardSet, deleteFlashcardSet, getFlashcardSet, getFlashcardSets, reviewFlashcard } from '../api/aiApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { StatusConfirmation } from '../components/StatusConfirmation'
import type { AiFlashcard, AiFlashcardSet, ReviewRating } from '../types/ai'
import { Alert, Button, SelectField } from '../ui'
import { AiMessage } from './AiMessage'
import { hintClasses, panelClasses } from './aiStyles'
import type { SummaryChoices } from './SummaryPanel'
import { useAiAction } from './useAiAction'

type Scope = 'COURSE' | 'TOPIC' | 'MODULE' | 'DOCUMENT'

const RATINGS: { value: ReviewRating; label: string }[] = [
  { value: 'AGAIN', label: 'Again' },
  { value: 'HARD', label: 'Hard' },
  { value: 'GOOD', label: 'Good' },
  { value: 'EASY', label: 'Easy' },
]

function Review({ lmsCourseId, set, onClose }: { lmsCourseId: number; set: AiFlashcardSet; onClose: () => void }) {
  const queryClient = useQueryClient()
  const today = set.today
  const [queue, setQueue] = useState<AiFlashcard[]>(() => set.cards.filter((card) => card.dueOn <= today))
  const [shown, setShown] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reviewed, setReviewed] = useState(0)
  const card = queue[0]

  async function rate(rating: ReviewRating) {
    if (!card || busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      const next = await reviewFlashcard(lmsCourseId, set.id, card.id, rating)
      setQueue((current) => [...current.slice(1), ...(next.dueOn <= today ? [next] : [])])
      setShown(false)
      setReviewed((count) => count + 1)
      void queryClient.invalidateQueries({ queryKey: queryKeys.lms.mastery(lmsCourseId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.lms.studyHome() })
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.flashcardSets(lmsCourseId) })
    } catch (failure) {
      if (getHttpStatus(failure) !== 401) {
        setError(getApiError(failure)?.message ?? 'Couldn’t save the review. Try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-4 rounded-panel border border-line p-4 text-[0.95rem] sm:p-5">
      <p className="text-2xl font-bold text-ink">Reviewing: {set.title}</p>
      {!card ? (
        <p className="mt-2 text-ink-muted">
          {reviewed > 0
            ? `Done for today: you made ${reviewed} ${reviewed === 1 ? 'review' : 'reviews'}. Nothing more is due in this set.`
            : 'Nothing is due today in this set. Come back when cards are due.'}
        </p>
      ) : (
        <div className="mt-1 space-y-3">
          <p className="text-ink-muted">{queue.length} {queue.length === 1 ? 'card' : 'cards'} left today</p>
          <div className="rounded-panel border border-line px-4 py-4">
            <p className="text-lg font-bold text-ink" aria-hidden="true">Question</p>
            <p className="text-base text-ink">{card.front}</p>
            {shown && (
              <>
                <p className="mt-3 text-lg font-bold text-ink" aria-hidden="true">Answer</p>
                <p className="leading-6 whitespace-pre-line text-ink">{card.back}</p>
                <p className="mt-2 text-ink-muted">From {card.source.title}</p>
              </>
            )}
          </div>
          {shown ? (
            <>
              <div className="flex flex-wrap gap-3 border-t border-line pt-3 sm:grid sm:grid-cols-4" role="group" aria-label="How well did you know it?">
                <p aria-hidden="true" className="w-full text-lg font-bold text-ink sm:col-span-4">How well did you know it?</p>
                {RATINGS.map((rating) => (
                  <Button key={rating.value} variant={rating.value === 'EASY' ? 'primary' : 'secondary'} disabled={busy} onClick={() => void rate(rating.value)}>
                    {rating.label}
                  </Button>
                ))}
              </div>
            </>
          ) : (
            <Button size="sm" onClick={() => setShown(true)}>Show answer</Button>
          )}
        </div>
      )}
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
      <div className="mt-4 border-t border-line pt-4">
        <Button onClick={onClose}>Close review</Button>
      </div>
    </div>
  )
}

export function FlashcardsPanel({ lmsCourseId, choices }: { lmsCourseId: number; choices: SummaryChoices }) {
  const id = useId()
  const queryClient = useQueryClient()
  const sets = useApiQuery(queryKeys.lms.flashcardSets(lmsCourseId), (signal) => getFlashcardSets(lmsCourseId, signal))
  const [scope, setScope] = useState<Scope>('COURSE')
  const [itemId, setItemId] = useState('')
  const [count, setCount] = useState('10')
  const [reviewing, setReviewing] = useState<AiFlashcardSet | null>(null)
  const [deleting, setDeleting] = useState<number | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const make = useAiAction<AiFlashcardSet>('student')
  const options = scope === 'TOPIC'
    ? choices.topics.map((topic) => ({ id: topic.id, label: `${topic.moduleTitle} · ${topic.title}` }))
    : scope === 'MODULE'
      ? choices.modules.map((module) => ({ id: module.id, label: module.title }))
      : scope === 'DOCUMENT'
        ? choices.files.map((file) => ({ id: file.id, label: `${file.title} (${file.pageTitle})` }))
        : []
  const chosen = options.some((option) => String(option.id) === itemId) ? itemId : options[0] ? String(options[0].id) : ''

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (make.state.status === 'running' || (scope !== 'COURSE' && !chosen)) {
      return
    }
    const made = await make.run((signal) =>
      createFlashcardSet(lmsCourseId, { scope, id: scope === 'COURSE' ? null : Number(chosen), cardCount: Number(count) }, signal),
    )
    if (made) {
      setAnnouncement(`Made “${made.title}” with ${made.cards.length} ${made.cards.length === 1 ? 'card' : 'cards'}.`)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.flashcardSets(lmsCourseId) })
    }
  }

  async function startReview(setId: number) {
    setReviewing(await queryClient.fetchQuery({
      queryKey: queryKeys.lms.flashcardSet(lmsCourseId, setId),
      queryFn: ({ signal }) => getFlashcardSet(lmsCourseId, setId, signal),
      staleTime: 0,
    }))
  }

  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-2xl font-bold text-ink">
        Flashcards
      </h2>
      <p className={hintClasses}>Make cards from your course with AI, then review the ones due each day. Your sets are your own.</p>
      <p role="status" className="mt-2 text-sm text-ink-muted empty:hidden">{announcement}</p>
      <form noValidate onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-3">
        <SelectField label="Make cards from" value={scope} onChange={(event) => setScope(event.target.value as Scope)}>
          <option value="COURSE">The whole course</option>
          <option value="TOPIC">A page</option>
          <option value="MODULE">A module</option>
          <option value="DOCUMENT">A file</option>
        </SelectField>
        {scope !== 'COURSE' && (
          <SelectField label="Which" value={chosen} disabled={options.length === 0} onChange={(event) => setItemId(event.target.value)}>
            {options.length === 0 && <option value="">None published yet</option>}
            {options.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
          </SelectField>
        )}
        <SelectField label="Cards" value={count} onChange={(event) => setCount(event.target.value)}>
          {['5', '10', '15', '20', '30'].map((value) => <option key={value} value={value}>{value}</option>)}
        </SelectField>
        <div className="sm:col-span-3">
          <Button type="submit" disabled={make.state.status === 'running'}>
            {make.state.status === 'running' ? 'Making cards…' : 'Make flashcards'}
          </Button>
        </div>
      </form>
      {make.state.status === 'failed' && <AiMessage tone="error" className="mt-3">{make.state.failure.message}</AiMessage>}

      {sets.status === 'loaded' && (
        sets.data.length === 0 ? (
          <p className="mt-3 text-sm text-ink-muted">No flashcard sets yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-line text-sm">
            {sets.data.map((set) => (
              <li key={set.id} className="py-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    <span className="font-medium text-ink">{set.title}</span>
                    <span className="block text-ink-muted">{set.cards} cards · {set.dueToday} due today</span>
                  </span>
                  <span className="flex flex-wrap gap-2">
                    <Button size="sm" onClick={() => void startReview(set.id)}>
                      Review<span className="sr-only">{` ${set.title}`}</span>
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setDeleting(set.id)}>
                      Delete<span className="sr-only">{` ${set.title}`}</span>
                    </Button>
                  </span>
                </div>
                {deleting === set.id && (
                  <div className="mt-2">
                    <StatusConfirmation
                      question={`Delete “${set.title}”?`}
                      detail="The set and its cards are removed for good. This cannot be undone."
                      confirmLabel="Delete set"
                      busyLabel="Deleting…"
                      onCancel={() => setDeleting(null)}
                      describeRefusal={(status, message) => (message && status !== null && status < 500 ? message : 'Couldn’t delete it. Try again.')}
                      onConfirm={async () => {
                        await deleteFlashcardSet(lmsCourseId, set.id)
                        setDeleting(null)
                        if (reviewing?.id === set.id) {
                          setReviewing(null)
                        }
                        setAnnouncement(`Deleted “${set.title}”.`)
                        await queryClient.invalidateQueries({ queryKey: queryKeys.lms.flashcardSets(lmsCourseId) })
                      }}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        )
      )}
      {sets.status === 'failed' && <Alert tone="error" className="mt-3">Couldn’t load your flashcard sets.</Alert>}
      {reviewing && <Review key={reviewing.id} lmsCourseId={lmsCourseId} set={reviewing} onClose={() => setReviewing(null)} />}
    </section>
  )
}
