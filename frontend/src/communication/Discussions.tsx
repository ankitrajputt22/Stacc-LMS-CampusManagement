import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { acceptReply, getDiscussion, getDiscussions, pinDiscussion, replyToDiscussion, startDiscussion } from '../api/lmsCommunicationApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { AuthorKind, CourseSide } from '../types/communication'
import { Alert, Badge, Button, ButtonLink, Card } from '../ui'
import { PostForm, PostText } from './PostForm'
import { refusalText } from './refusal'

function Author({ loginId, kind }: { loginId: string; kind: AuthorKind }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <span className="font-medium text-ink">{loginId}</span>
      {kind === 'FACULTY' && <Badge tone="brand">Faculty</Badge>}
    </span>
  )
}

/** The course's discussions: pinned first, then the most recently active, with Start a discussion. */
export function DiscussionBoard({ side, lmsCourseId, basePath }: { side: CourseSide; lmsCourseId: number; basePath: string }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const threads = useApiQuery(queryKeys.lms.discussions(side, lmsCourseId), (signal) => getDiscussions(side, lmsCourseId, signal))
  const [starting, setStarting] = useState(false)

  return (
    <div className="space-y-6">
      {starting && (
        <Card title="Start a discussion" description="Everyone in the course can read and reply. Posts can’t be edited or deleted.">
          <PostForm
            titleLabel="Title"
            bodyLabel="Question or topic"
            submitLabel="Post discussion"
            onCancel={() => setStarting(false)}
            onSubmit={async (title, body) => {
              const thread = await startDiscussion(side, lmsCourseId, title, body)
              await queryClient.invalidateQueries({ queryKey: queryKeys.lms.discussions(side, lmsCourseId) })
              void navigate(`${basePath}/${thread.threadId}`)
            }}
          />
        </Card>
      )}
      <Card
        title="Discussions"
        padding="none"
        action={starting ? undefined : <Button size="sm" onClick={() => setStarting(true)}>Start a discussion</Button>}
      >
        {threads.status !== 'loaded' ? (
          <SectionState request={threads} what="discussions" />
        ) : threads.data.length === 0 ? (
          <EmptyState title="No discussions yet." description="Start one to ask the class or the faculty a question." />
        ) : (
          <ul className="divide-y divide-line">
            {threads.data.map((thread) => (
              <li key={thread.threadId} className="px-4 py-3 sm:px-5">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`${basePath}/${thread.threadId}`} className="font-semibold text-stacc-primary underline-offset-4 hover:underline">
                    {thread.title}
                  </Link>
                  {thread.pinned && <Badge tone="info">Pinned</Badge>}
                  {thread.answered && <Badge tone="success">Answered</Badge>}
                </div>
                <p className="mt-0.5 text-sm text-ink-muted">
                  {thread.authorLoginId} · {thread.replyCount === 1 ? '1 reply' : `${thread.replyCount} replies`} · Last activity{' '}
                  <time dateTime={thread.lastActivityAt}>{formatDateTime(thread.lastActivityAt)}</time>
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

/** One discussion with its replies, Reply, Accept as answer (asker or faculty), and Pin (faculty). */
export function DiscussionThreadView({
  side,
  lmsCourseId,
  threadId,
  basePath,
}: {
  side: CourseSide
  lmsCourseId: number
  threadId: number
  basePath: string
}) {
  const queryClient = useQueryClient()
  const key = queryKeys.lms.discussion(side, lmsCourseId, threadId)
  const thread = useApiQuery(key, (signal) => getDiscussion(side, lmsCourseId, threadId, signal))
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({ queryKey: queryKeys.lms.discussions(side, lmsCourseId) })
  }

  async function act(action: () => Promise<unknown>, message: string) {
    setError(null)
    try {
      await action()
      await changed(message)
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t save. Try again.'))
    }
  }

  const back = (
    <ButtonLink to={basePath} variant="secondary" size="sm">
      All discussions
    </ButtonLink>
  )

  if (thread.status !== 'loaded') {
    return (
      <Card title="Discussion" action={back}>
        {thread.status === 'notFound' ? (
          <EmptyState title="Discussion unavailable" description="This discussion could not be found in this course." />
        ) : (
          <SectionState request={thread} what="the discussion" />
        )}
      </Card>
    )
  }
  const data = thread.data

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card title={data.title} action={back}>
        <div className="space-y-3">
          <p className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <Author loginId={data.authorLoginId} kind={data.authorKind} />
            <time dateTime={data.createdAt}>{formatDateTime(data.createdAt)}</time>
            {data.pinned && <Badge tone="info">Pinned</Badge>}
          </p>
          <PostText text={data.body} />
          {data.canPin && (
            <Button variant="secondary" size="sm" onClick={() => void act(() => pinDiscussion(lmsCourseId, threadId, !data.pinned), data.pinned ? 'Unpinned.' : 'Pinned.')}>
              {data.pinned ? 'Unpin' : 'Pin to top'}
            </Button>
          )}
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      </Card>
      <Card title={data.replies.length === 1 ? '1 reply' : `${data.replies.length} replies`} padding="none">
        {data.replies.length === 0 ? (
          <EmptyState title="No replies yet." description="Be the first to reply." />
        ) : (
          <ol className="divide-y divide-line">
            {data.replies.map((reply) => (
              <li key={reply.replyId} className={`space-y-2 px-4 py-4 sm:px-5 ${reply.accepted ? 'bg-surface-muted' : ''}`}>
                <p className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
                  <Author loginId={reply.authorLoginId} kind={reply.authorKind} />
                  <time dateTime={reply.createdAt}>{formatDateTime(reply.createdAt)}</time>
                  {reply.accepted && <Badge tone="success">Accepted answer</Badge>}
                </p>
                <PostText text={reply.body} />
                {data.canAccept && !reply.accepted && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void act(() => acceptReply(side, lmsCourseId, threadId, reply.replyId), 'Accepted the answer.')}
                  >
                    Accept as answer<span className="sr-only">{` from ${reply.authorLoginId}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>
      <Card title="Reply">
        <PostForm
          bodyLabel="Your reply"
          submitLabel="Post reply"
          onSubmit={async (_title, body) => {
            await replyToDiscussion(side, lmsCourseId, threadId, body)
            await queryClient.invalidateQueries({ queryKey: key })
            await changed('Posted your reply.')
          }}
        />
      </Card>
    </div>
  )
}
