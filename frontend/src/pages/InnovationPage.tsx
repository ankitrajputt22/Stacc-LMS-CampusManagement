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
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

type Run = (action: () => Promise<unknown>, done: string, fallback: string) => void

const STATUS_LABEL: Record<IdeaStatus, string> = {
  SUBMITTED: 'Under review', SHORTLISTED: 'Shortlisted', ACCEPTED: 'Accepted', NOT_SELECTED: 'Not selected', WITHDRAWN: 'Withdrawn',
}

function IdeaText({ idea }: { idea: Idea }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold break-words text-ink">{idea.title}</span>
        {idea.submitterLoginId && <span className="font-mono text-ink-muted">{idea.submitterLoginId}</span>}
        <Badge tone={idea.status === 'ACCEPTED' ? 'success' : idea.status === 'SHORTLISTED' ? 'info' : 'neutral'}>{STATUS_LABEL[idea.status]}</Badge>
      </div>
      <p className="text-sm break-words whitespace-pre-wrap text-ink"><span className="font-medium">Problem: </span>{idea.problem}</p>
      <p className="text-sm break-words whitespace-pre-wrap text-ink"><span className="font-medium">Idea: </span>{idea.solution}</p>
      {idea.decisionNote && <p className="text-sm text-ink-muted">Decision note: {idea.decisionNote}</p>}
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
          <ul className="divide-y divide-line">
            {ideas.data.map((idea) => (
              <li key={idea.ideaId} className="space-y-2 px-4 py-3 sm:px-5">
                <IdeaText idea={idea} />
                {idea.reviews.length > 0 && (
                  <ul className="list-disc pl-5 text-sm text-ink-muted">
                    {idea.reviews.map((review, index) => <li key={index} className="break-words whitespace-pre-wrap">{review.comment}</li>)}
                  </ul>
                )}
                {(idea.status === 'SUBMITTED' || idea.status === 'SHORTLISTED') && (
                  <Button size="sm" variant="secondary" onClick={() => run(() => withdrawIdea(idea.ideaId), 'Idea withdrawn.', 'Couldn’t withdraw the idea.')}>
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
        <ul className="divide-y divide-line">
          {ideas.data.map((idea) => (
            <li key={idea.ideaId} className="space-y-2 px-4 py-3 sm:px-5">
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
        <ul className="divide-y divide-line">
          {ideas.data.map((idea) => {
            const open = idea.status === 'SUBMITTED' || idea.status === 'SHORTLISTED'
            const note = notes[idea.ideaId]?.trim() || null
            return (
              <li key={idea.ideaId} className="space-y-2 px-4 py-3 sm:px-5">
                <IdeaText idea={idea} />
                {idea.reviews.length > 0 && (
                  <ul className="list-disc pl-5 text-sm text-ink-muted">
                    {idea.reviews.map((review) => (
                      <li key={review.reviewerLoginId} className="break-words">{`${review.reviewerLoginId}: ${review.score} of 5 — ${review.comment}`}</li>
                    ))}
                  </ul>
                )}
                {open && (
                  <div className="flex flex-wrap items-end gap-2">
                    <TextField label={`Decision note for ${idea.title} (optional)`} value={notes[idea.ideaId] ?? ''} maxLength={1000} onChange={(event) => setNotes({ ...notes, [idea.ideaId]: event.target.value })} />
                    {idea.status === 'SUBMITTED' && (
                      <Button size="sm" variant="secondary" onClick={() => run(() => decideIdea(idea.ideaId, 'SHORTLISTED', note), 'Idea shortlisted.', 'Couldn’t decide.')}>
                        Shortlist<span className="sr-only">{` ${idea.title}`}</span>
                      </Button>
                    )}
                    <Button size="sm" onClick={() => run(() => decideIdea(idea.ideaId, 'ACCEPTED', note), 'Idea accepted.', 'Couldn’t decide.')}>
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

/** /innovation: submitting ideas, faculty reviews, and the campus office's decisions, one part per role held. */
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
