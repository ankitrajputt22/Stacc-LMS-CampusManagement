import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { decideIdea, getAllIdeas, getIdeasToReview, getMyIdeas, reviewIdea, submitIdea, withdrawIdea } from '../api/innovationApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { Idea, IdeaStatus } from '../types/innovation'
import { Alert, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

type Run = (action: () => Promise<unknown>, done: string, fallback: string) => void

const STATUS_LABEL: Record<IdeaStatus, string> = {
  SUBMITTED: 'Under review', SHORTLISTED: 'Shortlisted', ACCEPTED: 'Accepted', NOT_SELECTED: 'Not selected', WITHDRAWN: 'Withdrawn',
}

const STATUS_TONE: Record<IdeaStatus, string> = {
  SUBMITTED: 'border-orange-200 bg-orange-50 text-ink',
  SHORTLISTED: 'border-sky-200 bg-sky-50 text-ink',
  ACCEPTED: 'border-success-line bg-success-soft text-ink',
  NOT_SELECTED: 'border-danger/40 bg-danger/5 text-danger',
  WITHDRAWN: 'border-line-strong bg-surface-muted text-ink-muted',
}

const ideaItemClasses = 'space-y-3 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5'

function IdeaText({ idea }: { idea: Idea }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-xl font-bold break-words text-portal-navy">{idea.title}</span>
        <span className={`rounded-sm border px-2 py-0.5 text-sm font-medium ${STATUS_TONE[idea.status]}`}>{STATUS_LABEL[idea.status]}</span>
      </div>
      {idea.submitterLoginId && <p className="text-sm text-ink-muted">By {idea.submitterLoginId}</p>}
      <div>
        <p className="text-sm font-semibold text-ink">Problem</p>
        <p className="text-[0.95rem] break-words whitespace-pre-wrap text-ink">{idea.problem}</p>
      </div>
      <div>
        <p className="text-sm font-semibold text-ink">Idea</p>
        <p className="text-[0.95rem] break-words whitespace-pre-wrap text-ink">{idea.solution}</p>
      </div>
      {idea.decisionNote && (
        <p className="rounded-control bg-surface-muted px-3 py-2 text-[0.95rem] text-ink">{`Decision note: ${idea.decisionNote}`}</p>
      )}
    </>
  )
}

function Mine({ run }: { run: Run }) {
  const ideas = useApiQuery(queryKeys.innovation.mine(), (signal) => getMyIdeas(signal))
  const [form, setForm] = useState({ title: '', problem: '', solution: '' })
  return (
    <>
      <Card title="Submit an idea">
        <form
          className="grid gap-3"
          onSubmit={(event) => {
            event.preventDefault()
            run(() => submitIdea({ title: form.title.trim(), problem: form.problem.trim(), solution: form.solution.trim() }).then(() => setForm({ title: '', problem: '', solution: '' })),
              'Idea submitted for review.', 'Couldn’t submit the idea.')
          }}
        >
          <TextField label="Idea title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
          <TextAreaField label="The problem" rows={3} value={form.problem} maxLength={5000} onChange={(event) => setForm({ ...form, problem: event.target.value })} />
          <TextAreaField label="Your idea" rows={3} value={form.solution} maxLength={5000} onChange={(event) => setForm({ ...form, solution: event.target.value })} />
          <div><Button type="submit">Submit idea</Button></div>
        </form>
      </Card>
      <Card title="My ideas" padding="none">
        {ideas.status !== 'loaded' ? (
          <SectionState request={ideas} what="your ideas" />
        ) : ideas.data.length === 0 ? (
          <EmptyState title="No ideas yet." />
        ) : (
          <ul className="space-y-3 px-4 py-4 sm:px-5">
            {ideas.data.map((idea) => (
              <li key={idea.ideaId} className={ideaItemClasses}>
                <IdeaText idea={idea} />
                {idea.reviews.length > 0 && (
                  <div>
                    <p className="text-sm font-semibold text-ink">Reviewers’ comments</p>
                    <ul className="mt-1 space-y-2 text-[0.95rem] text-ink">
                      {idea.reviews.map((review, index) => (
                        <li key={index} className="rounded-control border border-line bg-surface-muted px-3 py-2 break-words whitespace-pre-wrap">{review.comment}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {(idea.status === 'SUBMITTED' || idea.status === 'SHORTLISTED') && (
                  <Button size="sm" onClick={() => run(() => withdrawIdea(idea.ideaId), 'Idea withdrawn.', 'Couldn’t withdraw the idea.')}>
                    Withdraw<span className="sr-only">{` ${idea.title}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}

function ReviewForm({ idea, run }: { idea: Idea; run: Run }) {
  const mine = idea.reviews[0]
  const [score, setScore] = useState(String(mine?.score ?? 3))
  const [comment, setComment] = useState(mine?.comment ?? '')
  return (
    <form
      className="grid gap-2 sm:grid-cols-[8rem_1fr_auto] sm:items-end"
      onSubmit={(event) => {
        event.preventDefault()
        run(() => reviewIdea(idea.ideaId, Number(score), comment.trim()), 'Review saved.', 'Couldn’t save the review.')
      }}
    >
      <SelectField label={`Score for ${idea.title}`} value={score} onChange={(event) => setScore(event.target.value)}>
        {['1', '2', '3', '4', '5'].map((value) => <option key={value} value={value}>{value}</option>)}
      </SelectField>
      <TextField label={`Comment on ${idea.title}`} value={comment} maxLength={5000} onChange={(event) => setComment(event.target.value)} />
      <Button type="submit" size="sm">{mine ? 'Update review' : 'Save review'}</Button>
    </form>
  )
}

function Reviews({ run }: { run: Run }) {
  const ideas = useApiQuery(queryKeys.innovation.reviews(), (signal) => getIdeasToReview(signal))
  return (
    <Card title="Ideas to review" description="Score each idea and leave a comment; the submitter sees comments without your name once it is decided." padding="none">
      {ideas.status !== 'loaded' ? (
        <SectionState request={ideas} what="ideas to review" />
      ) : ideas.data.length === 0 ? (
        <EmptyState title="Nothing to review." />
      ) : (
        <ul className="space-y-3 px-4 py-4 sm:px-5">
          {ideas.data.map((idea) => (
            <li key={idea.ideaId} className={ideaItemClasses}>
              <IdeaText idea={idea} />
              <ReviewForm idea={idea} run={run} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function Office({ run }: { run: Run }) {
  const ideas = useApiQuery(queryKeys.innovation.office(), (signal) => getAllIdeas(signal))
  const [notes, setNotes] = useState<Record<number, string>>({})
  return (
    <Card title="All ideas" description="Every idea with its reviews. Decisions move forward only." padding="none">
      {ideas.status !== 'loaded' ? (
        <SectionState request={ideas} what="ideas" />
      ) : ideas.data.length === 0 ? (
        <EmptyState title="No ideas submitted yet." />
      ) : (
        <ul className="space-y-3 px-4 py-4 sm:px-5">
          {ideas.data.map((idea) => {
            const open = idea.status === 'SUBMITTED' || idea.status === 'SHORTLISTED'
            const note = notes[idea.ideaId]?.trim() || null
            return (
              <li key={idea.ideaId} className={ideaItemClasses}>
                <IdeaText idea={idea} />
                {idea.reviews.length > 0 && (
                  <div>
                    <p className="text-sm font-semibold text-ink">Reviews</p>
                    <ul className="mt-1 space-y-2 text-[0.95rem] text-ink">
                      {idea.reviews.map((review) => (
                        <li key={review.reviewerLoginId} className="flex items-start gap-3 rounded-control border border-line bg-surface-muted px-3 py-2">
                          <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-portal-bar-strong text-xs font-bold text-portal-navy uppercase">
                            {(review.reviewerLoginId ?? '').slice(0, 2)}
                          </span>
                          <span className="min-w-0 break-words">{`${review.reviewerLoginId}: ${review.score} of 5 — ${review.comment}`}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {open && (
                  <div className="flex flex-wrap items-end gap-2 border-t border-line pt-3">
                    <TextField label={`Decision note for ${idea.title} (optional)`} value={notes[idea.ideaId] ?? ''} maxLength={1000} onChange={(event) => setNotes({ ...notes, [idea.ideaId]: event.target.value })} />
                    {idea.status === 'SUBMITTED' && (
                      <Button size="sm" onClick={() => run(() => decideIdea(idea.ideaId, 'SHORTLISTED', note), 'Idea shortlisted.', 'Couldn’t decide.')}>
                        Shortlist<span className="sr-only">{` ${idea.title}`}</span>
                      </Button>
                    )}
                    <Button size="sm" variant="secondary" onClick={() => run(() => decideIdea(idea.ideaId, 'ACCEPTED', note), 'Idea accepted.', 'Couldn’t decide.')}>
                      Accept<span className="sr-only">{` ${idea.title}`}</span>
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => run(() => decideIdea(idea.ideaId, 'NOT_SELECTED', note), 'Idea marked not selected.', 'Couldn’t decide.')}>
                      Not selected<span className="sr-only">{` ${idea.title}`}</span>
                    </Button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}

export function InnovationPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const run: Run = (action, done, fallback) =>
    void action()
      .then(async () => {
        setMessage({ tone: 'success', text: done })
        await queryClient.invalidateQueries({ queryKey: queryKeys.innovation.all })
      })
      .catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, fallback) ?? '' }))
  return (
    <>
      <title>Innovation · Stacc</title>
      <PageHeader title="Innovation" description="Share ideas for the college; faculty review them and the campus office decides." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {hasAnyRole(roles, ['STUDENT', 'FACULTY']) && <Mine run={run} />}
        {hasAnyRole(roles, ['FACULTY']) && <Reviews run={run} />}
        {hasAnyRole(roles, ['ADMIN']) && <Office run={run} />}
      </div>
    </>
  )
}
